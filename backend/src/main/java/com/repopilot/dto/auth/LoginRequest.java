package com.repopilot.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登录请求。 */
public record LoginRequest(
        @NotBlank
        String username,
        @NotBlank
        @Size(max = 64)
        String password
) {
}
