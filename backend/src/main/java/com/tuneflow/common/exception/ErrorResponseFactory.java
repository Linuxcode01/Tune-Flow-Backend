package com.tuneflow.common.exception;

import com.tuneflow.common.response.ErrorResponse;

/** Small helper used by servlet filters (outside the MVC exception handler) to build error bodies. */
public final class ErrorResponseFactory {

    private ErrorResponseFactory() {
    }

    public static ErrorResponse of(ErrorCode code, String path) {
        return ErrorResponse.of(code.name(), code.defaultMessage(), null, path);
    }

    public static ErrorResponse of(ErrorCode code, String message, String path) {
        return ErrorResponse.of(code.name(), message, null, path);
    }
}
