package com.repopilot.service.ai;

import com.repopilot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Provider 注册器：根据配置 repopilot.ai.provider 选择当前生效的 Provider。
 *
 * <p>配置为 mock（默认）时使用 MockAiProvider；remote 时使用 RemoteAiProvider。
 * 配置值非法时回退到 mock 并告警，保证应用总能启动。
 */
@Component
public class AiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final Map<String, AiProvider> providers;
    private final String activeName;

    public AiProviderRegistry(java.util.List<AiProvider> providerList, AppProperties properties) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(p -> p.getName().toLowerCase(Locale.ROOT), Function.identity()));
        String configured = properties.ai().provider() == null ? "mock" : properties.ai().provider().toLowerCase(Locale.ROOT);
        if (!providers.containsKey(configured)) {
            log.warn("未知的 AI provider 配置：{}，回退到 mock 模式", configured);
            configured = "mock";
        }
        this.activeName = configured;
        log.info("当前 AI 模式：{}（model={}）", configured, active().getModelName());
    }

    public AiProvider active() {
        return providers.get(activeName);
    }

    public boolean isMockMode() {
        return active().isMock();
    }

    public String activeName() {
        return activeName;
    }
}
