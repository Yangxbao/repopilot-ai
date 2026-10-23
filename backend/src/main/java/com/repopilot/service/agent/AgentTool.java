package com.repopilot.service.agent;

import java.util.Map;

/**
 * Agent 可用的工具接口。
 *
 * <p>设计约束（重要）：
 * <ul>
 *   <li>工具只能访问"已导入并被索引"的代码库内容（数据来自数据库，不碰本地文件系统）；</li>
 *   <li>每次执行都会在内部重新校验代码库所有权（defense in depth）；</li>
 *   <li>参数必须经过校验，不接受任意绝对路径，不允许执行任何命令。</li>
 * </ul>
 */
public interface AgentTool {

    /** 工具名（全局唯一，作为白名单 key）。 */
    String name();

    String description();

    ToolResult execute(ToolRunContext ctx, Map<String, Object> params);
}
