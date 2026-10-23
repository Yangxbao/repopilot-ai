package com.repopilot.common.exception;

/**
 * 业务异常：Service 层用它在任意位置中断流程并携带错误码。
 * 由 {@link GlobalExceptionHandler} 统一转成 HTTP 响应。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
