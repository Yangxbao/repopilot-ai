package com.repopilot.common.exception;

/**
 * 业务错误码表。
 *
 * <p>httpStatus 用于设置 HTTP 状态码，code 是业务侧错误码（前端据此做精细化提示）。
 * 按资源类型分段：401xx 认证、403xx 权限、404xx 资源不存在、400xx 参数与上传、
 * 409xx 状态冲突、502xx/504xx AI 服务。
 */
public enum ErrorCode {
    UNAUTHORIZED(401, 40101, "未登录或登录已过期"),
    FORBIDDEN(403, 40301, "无权访问该资源"),
    NOT_FOUND(404, 40401, "资源不存在"),
    BAD_REQUEST(400, 40001, "请求参数错误"),
    VALIDATION(400, 40002, "参数校验失败"),
    CONFLICT(409, 40901, "资源冲突"),
    UPLOAD_TOO_LARGE(413, 41301, "上传文件超过限制"),
    BAD_ZIP(400, 40003, "压缩包不合法或包含被禁止的内容"),
    REPO_NOT_READY(409, 40902, "代码库尚未完成索引"),
    REPO_NAME_CONFLICT(409, 40903, "已存在同名代码库"),
    AI_ERROR(502, 50201, "AI 服务调用失败"),
    AI_TIMEOUT(504, 50202, "AI 服务响应超时"),
    AI_NOT_CONFIGURED(503, 50203, "AI 服务未配置"),
    RATE_LIMIT(429, 42901, "调用次数超过限制"),
    INTERNAL(500, 50001, "服务器内部错误");

    private final int httpStatus;
    private final int code;
    private final String defaultMessage;

    ErrorCode(int httpStatus, int code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
