package com.repopilot.service.agent;

import com.repopilot.common.exception.BizException;
import com.repopilot.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具注册器：维护工具白名单并统一执行。
 *
 * <p>安全模型：
 * <ul>
 *   <li>只有注册在这里的工具才能被调用（白名单，模型/流程无法引入任意能力）；</li>
 *   <li>每次调用都要通过运行预算检查（次数上限 + 截止时间）；</li>
 *   <li>每次调用都会写 tool_call_log（成功与失败都记录）。</li>
 * </ul>
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final ToolCallLogger logger;

    public ToolRegistry(java.util.List<AgentTool> toolList, ToolCallLogger logger) {
        this.logger = logger;
        for (AgentTool tool : toolList) {
            tools.put(tool.name(), tool);
        }
    }

    /** 当前注册的工具清单（供 API 展示）。 */
    public Map<String, String> describe() {
        Map<String, String> result = new LinkedHashMap<>();
        tools.forEach((name, tool) -> result.put(name, tool.description()));
        return result;
    }

    /**
     * 执行一个工具。预算不足抛 RATE_LIMIT；工具内部失败返回失败的 ToolResult
     * （由调用方决定是否继续流程）。
     */
    public ToolResult execute(ToolRunContext ctx, String toolName, Map<String, Object> params) {
        if (!tools.containsKey(toolName)) {
            // 不在白名单内的工具直接拒绝并记录
            logger.record(ctx, toolName, params, ToolResult.fail("未注册的工具"), 0);
            throw new BizException(ErrorCode.FORBIDDEN, "工具未注册或不允许调用：" + toolName);
        }
        if (!ctx.tryAcquire()) {
            logger.record(ctx, toolName, params, ToolResult.fail("超过工具调用预算"), 0);
            throw new BizException(ErrorCode.RATE_LIMIT,
                    "本次分析的工具调用次数或时间超过限制（上限 " + ctx.maxToolCalls() + " 次）");
        }
        long start = System.currentTimeMillis();
        ToolResult result;
        try {
            result = tools.get(toolName).execute(ctx, params);
        } catch (BizException e) {
            result = ToolResult.fail(e.getMessage());
            logger.record(ctx, toolName, params, result, System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            result = ToolResult.fail("工具执行异常：" + e.getMessage());
            logger.record(ctx, toolName, params, result, System.currentTimeMillis() - start);
            return result;
        }
        logger.record(ctx, toolName, params, result, System.currentTimeMillis() - start);
        return result;
    }
}
