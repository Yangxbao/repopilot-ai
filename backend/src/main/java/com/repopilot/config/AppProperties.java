package com.repopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 中 repopilot.* 的类型安全映射。
 * 全部属性都支持用环境变量覆盖（见 yml 注释）。
 */
@ConfigurationProperties(prefix = "repopilot")
public record AppProperties(Jwt jwt, Ai ai, Upload upload, Qa qa, Agent agent, String dataDir) {

    public record Jwt(String secret, long expireHours) {
    }

    public record Ai(String provider, String baseUrl, String apiKey, String model,
                     int timeoutSeconds, int maxRetries) {
    }

    public record Upload(int maxZipMb, int maxEntries, int maxFileKb, int maxTotalMb, int maxCompressionRatio) {
    }

    public record Qa(int topK) {
    }

    public record Agent(int maxToolCalls) {
    }
}
