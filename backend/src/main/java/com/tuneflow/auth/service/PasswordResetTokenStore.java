package com.tuneflow.auth.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Single-use, expiring token store for password resets. Backed by Caffeine for single-node
 * development and by Redis when {@code tuneflow.cache.provider=redis}, so the reset flow works
 * unchanged behind a load balancer.
 */
public interface PasswordResetTokenStore {

    void store(String token, UUID userId, long ttlSeconds);

    Optional<UUID> consume(String token);
}
