package com.tuneflow.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Standard success envelope used by every TuneFlow endpoint (see docs section 14):
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Song fetched successfully",
 *   "data": {},
 *   "timestamp": "2026-10-06T10:30:00Z"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ApiResponse", description = "Standard TuneFlow success envelope")
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Request successful", data);
    }
}
