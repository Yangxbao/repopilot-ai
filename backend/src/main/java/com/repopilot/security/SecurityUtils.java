package com.repopilot.security;

import com.repopilot.common.exception.BizException;
import com.repopilot.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 读取当前登录用户的工具类。
 * Controller / Service 层统一用它获取 userId，保证"数据属于谁"的判断有单一来源。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    public static long currentUserId() {
        return currentUser().userId();
    }
}
