package com.tuneflow.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope (see docs section 14):
 *
 * <pre>
 * {
 *   "success": false,
 *   "message": "Song not found",
 *   "errorCode": "SONG_NOT_FOUND",
 *   "timestamp": "2026-10-06T10:30:00Z"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ErrorResponse", description = "Standard TuneFlow error envelope")
public record ErrorResponse(
        boolean success,
        String message,
        String errorCode,
        List<FieldError> fieldErrors,
        String path,
        Instant timestamp
) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String errorCode, String message, List<FieldError> fieldErrors, String path) {
        return new ErrorResponse(false, message, errorCode, fieldErrors, path, Instant.now());
    }
}
