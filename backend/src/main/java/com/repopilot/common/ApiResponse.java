package com.repopilot.common;

/**
 * 统一 API 响应结构。
 *
 * <p>code = 0 表示成功；其他值对应 {@link com.repopilot.common.exception.ErrorCode} 中的业务错误码。
 * 前端 axios 拦截器根据 code 判断是否弹出错误提示。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "success", null);
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
