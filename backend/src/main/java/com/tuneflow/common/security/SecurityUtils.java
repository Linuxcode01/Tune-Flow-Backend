package com.tuneflow.common.security;

import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Convenience accessors for the currently authenticated user. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedUser user ? Optional.of(user) : Optional.empty();
    }

    /** @throws ApiException 401 when no authenticated principal is present. */
    public static AuthenticatedUser requireUser() {
        return currentUser().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    public static java.util.UUID requireUserId() {
        return requireUser().id();
    }

    public static boolean isAdmin() {
        return currentUser().map(u -> u.role() == Role.ADMIN).orElse(false);
    }

    public static boolean isArtist() {
        return currentUser().map(u -> u.role() == Role.ARTIST).orElse(false);
    }
}
