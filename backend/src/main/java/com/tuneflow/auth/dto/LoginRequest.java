package com.tuneflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", example = """
        { "email": "chandan@example.com", "password": "StrongPassword123" }
        """)
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        String password,

        @Size(max = 120)
        @Schema(description = "Optional client device identifier used for per-device refresh-token revocation")
        String deviceId
) {
}
