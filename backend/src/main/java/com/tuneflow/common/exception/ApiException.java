package com.tuneflow.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base runtime exception for all business failures. Services throw this (or one of its
 * subclasses) and {@code GlobalExceptionHandler} turns it into the standard error envelope.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, new Object[0]);
    }

    public ApiException(ErrorCode errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    public HttpStatus status() {
        return errorCode.status();
    }

    // ===== Convenience factories =====

    public static ApiException notFound(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message);
    }
}
