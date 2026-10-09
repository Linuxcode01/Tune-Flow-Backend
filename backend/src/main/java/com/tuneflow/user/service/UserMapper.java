package com.tuneflow.user.service;

import com.tuneflow.user.dto.UserResponse;
import com.tuneflow.user.entity.User;

import java.util.UUID;

/** Maps entities to API DTOs. DTOs never expose password hashes, token versions or internals. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return toResponse(user, null);
    }

    public static UserResponse toResponse(User user, UUID artistId) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getRole(),
                artistId,
                user.getCreatedAt());
    }
}
