package com.tuneflow.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateProfileRequest", description = "Partial profile update; null fields are left unchanged")
public record UpdateProfileRequest(
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
        String name,

        @Email(message = "Email must be valid")
        @Size(max = 190)
        String email,

        @Size(max = 2048, message = "Profile image URL is too long")
        @Pattern(regexp = "^$|^(https?://).*", message = "Profile image URL must be an absolute http(s) URL")
        String profileImageUrl
) {
}
