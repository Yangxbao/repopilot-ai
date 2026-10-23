package com.repopilot.service.ai;

/**
 * AI Provider 调用失败异常。
 * 由编排层（问答 / Bug 分析）捕获后记录失败状态并返回友好错误，
 * 保证模型服务不可用不会导致整个应用崩溃。
 */
public class AiProviderException extends RuntimeException {

    private final com.repopilot.common.exception.ErrorCode errorCode;

    public AiProviderException(com.repopilot.common.exception.ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiProviderException(com.repopilot.common.exception.ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public com.repopilot.common.exception.ErrorCode getErrorCode() {
        return errorCode;
    }
}
