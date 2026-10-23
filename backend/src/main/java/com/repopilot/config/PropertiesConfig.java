package com.repopilot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用 @ConfigurationProperties 配置绑定。 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class PropertiesConfig {
}
