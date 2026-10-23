package com.repopilot.common.exception;

import com.repopilot.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：保证所有错误都以统一的 JSON 结构返回，
 * 且不向前端泄露堆栈信息、数据库细节等敏感内容。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBiz(BizException e) {
        // 业务异常属于预期内的错误，日志只记 warn，避免刷屏
        log.warn("业务异常: code={}, message={}", e.getErrorCode().code(), e.getMessage());
        return ApiResponse.error(e.getErrorCode().code(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(Exception e) {
        String detail;
        if (e instanceof MethodArgumentNotValidException ex) {
            detail = ex.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + ": " + f.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else {
            detail = e.getMessage();
        }
        return ApiResponse.error(ErrorCode.VALIDATION.code(), detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException e) {
        return ApiResponse.error(ErrorCode.VALIDATION.code(), e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ApiResponse.error(ErrorCode.UPLOAD_TOO_LARGE.code(),
                "上传文件超过大小限制（ZIP 包最大 30MB）");
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMultipart(MultipartException e) {
        return ApiResponse.error(ErrorCode.BAD_REQUEST.code(), "上传内容格式不正确");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return ApiResponse.error(ErrorCode.BAD_REQUEST.code(), "请求体格式不正确");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ApiResponse.error(ErrorCode.BAD_REQUEST.code(), "不支持的请求方法");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleOther(Exception e) {
        // 未预期异常：记录完整堆栈用于排查，但对外只返回通用提示
        log.error("未处理异常", e);
        return ApiResponse.error(ErrorCode.INTERNAL.code(), "服务器内部错误，请稍后重试");
    }
}
