package com.tuneflow.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings. Access tokens are intentionally short-lived; refresh tokens are long-lived but
 * revocable and rotated server-side (docs section 15).
 */
@ConfigurationProperties(prefix = "tuneflow.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "tuneflow" : issuer;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
    }
}
