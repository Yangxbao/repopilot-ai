package com.taskflow.exception;

/**
 * 业务异常（演示数据）。
 * 当业务规则不满足时（例如用户名已存在、状态流转非法）抛出。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
