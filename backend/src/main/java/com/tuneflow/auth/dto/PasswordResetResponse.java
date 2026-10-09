package com.tuneflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * In production the reset link is emailed and {@code resetToken} is omitted. For local development
 * the token is returned so the flow can be exercised end-to-end without an SMTP provider.
 */
@Schema(name = "PasswordResetResponse")
public record PasswordResetResponse(
        String message,
        @Schema(description = "Only populated in the dev profile") String resetToken,
        long expiresInSeconds
) {
}
