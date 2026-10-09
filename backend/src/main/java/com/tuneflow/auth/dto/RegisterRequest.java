package com.tuneflow.auth.dto;

import com.tuneflow.common.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", example = """
        {
          "name": "Chandan",
          "email": "chandan@example.com",
          "password": "StrongPassword123"
        }
        """)
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 190)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        String password,

        /**
         * Optional. USER by default; ARTIST creates an artist profile in the same transaction.
         * ADMIN can never be self-assigned (docs section 15 - RBAC).
         */
        @Schema(description = "Requested role: USER or ARTIST", allowableValues = {"USER", "ARTIST"})
        Role role
) {
}
