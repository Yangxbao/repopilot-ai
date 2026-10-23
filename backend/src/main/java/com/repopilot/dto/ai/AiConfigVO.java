package com.repopilot.dto.ai;

/**
 * AI 模型配置页展示的数据。
 * 注意：永远不会返回完整 API Key，只返回"是否已配置"。
 */
public record AiConfigVO(
        String mode,
        String providerName,
        String model,
        String baseUrl,
        boolean apiKeyConfigured,
        int timeoutSeconds,
        int maxRetries
) {
}
