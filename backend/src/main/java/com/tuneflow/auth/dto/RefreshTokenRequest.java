package com.tuneflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest")
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken,

        @Schema(description = "Optional device identifier to bind the rotated token")
        String deviceId
) {
}
