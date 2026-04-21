package com.ticket.common.exception;

/**
 * 业务异常类.
 *
 * 用于封装业务逻辑中的预期异常(如座位已售罄、超过限购数量等).
 * 持有 ErrorCode 引用,便于 GlobalExceptionHandler 统一提取错误码和消息.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
