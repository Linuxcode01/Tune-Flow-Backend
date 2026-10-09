package com.tuneflow.common.security;

/**
 * RBAC roles (docs section 15). Stored as {@code users.role} and embedded in the access token as
 * the {@code roles} claim.
 */
public enum Role {
    USER,
    ARTIST,
    ADMIN
}
