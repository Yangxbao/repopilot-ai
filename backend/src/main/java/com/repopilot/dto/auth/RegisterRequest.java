package com.repopilot.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册请求。用户名只允许字母数字下划线，防止注入与显示问题。 */
public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名只能包含字母、数字和下划线，长度 3-32")
        String username,
        @NotBlank
        @Size(min = 6, max = 64, message = "密码长度必须为 6-64 位")
        String password,
        @Size(max = 50, message = "昵称最长 50 字符")
        String nickname
) {
}
