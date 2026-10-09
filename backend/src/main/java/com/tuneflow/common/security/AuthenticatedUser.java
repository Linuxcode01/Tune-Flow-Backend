package com.tuneflow.common.security;

import java.util.UUID;

/**
 * Authenticated principal placed in the {@code SecurityContext} by {@code JwtAuthenticationFilter}.
 * The Flutter client is never trusted for identity: the user id always comes from the verified JWT.
 */
public record AuthenticatedUser(
        UUID id,
        String email,
        String name,
        Role role
) {
}
