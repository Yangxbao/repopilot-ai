package com.repopilot.dto.auth;

/** 注册/登录成功后的返回：JWT token + 用户基本信息。 */
public record AuthResponse(String token, long expiresInHours, UserVO user) {

    public record UserVO(long id, String username, String nickname, String role) {
    }
}
