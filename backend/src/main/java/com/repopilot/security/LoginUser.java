package com.repopilot.security;

/**
 * 认证通过后的用户信息，作为 SecurityContext 中的 principal。
 * 携带数据库主键，业务代码通过 SecurityUtils.currentUserId() 获取当前用户。
 */
public record LoginUser(long userId, String username, String role) {
}
