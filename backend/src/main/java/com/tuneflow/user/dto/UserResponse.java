package com.tuneflow.user.dto;

import com.tuneflow.common.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "UserResponse", description = "Public user profile")
public record UserResponse(
        UUID id,
        String name,
        String email,
        String profileImageUrl,
        Role role,
        UUID artistId,
        Instant createdAt
) {
}
