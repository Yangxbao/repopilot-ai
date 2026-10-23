package com.repopilot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置：在 Swagger UI 中启用 Bearer Token 认证，
 * 先调用 /api/auth/login 拿到 token，再点右上角 Authorize 粘贴即可调试所有接口。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI repopilotOpenApi() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("RepoPilot AI API")
                        .description("代码库知识问答与 Bug 分析 Agent 的后端接口文档")
                        .version("v0.1.0"))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
