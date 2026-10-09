package com.tuneflow.auth.security;

import com.tuneflow.common.security.Role;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * JWT issuing/parsing abstraction. Kept as an interface so the signing scheme (HMAC now,
 * asymmetric keys later) can change without touching the auth module.
 */
public interface JwtService {

    /** Short-lived access token carrying identity + role claims. */
    String generateAccessToken(UUID userId, String email, String name, Role role);

    /** Opaque-looking, but signed, refresh token id used for rotation bookkeeping. */
    String generateRefreshTokenId();

    /** @throws com.tuneflow.common.exception.ApiException INVALID_TOKEN when malformed/expired. */
    JwtClaims parseAccessToken(String token);

    Duration accessTokenTtl();

    Duration refreshTokenTtl();

    record JwtClaims(
            UUID userId,
            String email,
            String name,
            Role role,
            String tokenId,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
