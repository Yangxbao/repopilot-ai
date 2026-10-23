package com.repopilot.dto.user;

import java.time.LocalDateTime;

/** 管理员查看的用户信息（不含密码哈希）。 */
public record UserVO(
        long id,
        String username,
        String nickname,
        String role,
        String status,
        LocalDateTime createdAt
) {
}
