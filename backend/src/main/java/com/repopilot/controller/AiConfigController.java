package com.repopilot.controller;

import com.repopilot.common.ApiResponse;
import com.repopilot.config.AppProperties;
import com.repopilot.dto.ai.AiConfigVO;
import com.repopilot.service.ai.AiProviderException;
import com.repopilot.service.ai.AiProviderRegistry;
import com.repopilot.service.agent.ToolRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模型配置：展示当前模式（mock / remote）、Agent 工具清单与连通性测试。
 *
 * <p>设计说明：模型配置通过环境变量管理（Key 不落库、不进 Git），
 * 本页面只读展示 + 测试连通性，不提供在线修改 Key 的功能。
 */
@Tag(name = "AI 模型配置")
@RestController
@RequestMapping("/api/ai-config")
public class AiConfigController {

    private static final Logger log = LoggerFactory.getLogger(AiConfigController.class);

    private final AiProviderRegistry registry;
    private final AppProperties properties;
    private final ToolRegistry toolRegistry;

    public AiConfigController(AiProviderRegistry registry, AppProperties properties, ToolRegistry toolRegistry) {
        this.registry = registry;
        this.properties = properties;
        this.toolRegistry = toolRegistry;
    }

    @Operation(summary = "当前 AI 配置（不返回密钥）")
    @GetMapping
    public ApiResponse<AiConfigVO> config() {
        var ai = properties.ai();
        boolean mock = registry.isMockMode();
        return ApiResponse.ok(new AiConfigVO(
                mock ? "mock" : "remote",
                registry.active().getName(),
                registry.active().getModelName(),
                mock ? null : ai.baseUrl(),
                mock ? false : ai.apiKey() != null && !ai.apiKey().isBlank(),
                ai.timeoutSeconds(),
                ai.maxRetries()));
    }

    @Operation(summary = "当前可用的 Agent 工具清单")
    @GetMapping("/tools")
    public ApiResponse<Map<String, String>> tools() {
        return ApiResponse.ok(new LinkedHashMap<>(toolRegistry.describe()));
    }

    @Operation(summary = "连通性测试")
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test() {
        long start = System.currentTimeMillis();
        try {
            String reply = registry.active().ping();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("mode", registry.active().getName());
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("reply", reply);
            return ApiResponse.ok(result);
        } catch (AiProviderException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("mode", registry.active().getName());
            result.put("message", e.getMessage());
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("AI 连通性测试异常", e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("message", "测试失败：" + e.getMessage());
            return ApiResponse.ok(result);
        }
    }
}
