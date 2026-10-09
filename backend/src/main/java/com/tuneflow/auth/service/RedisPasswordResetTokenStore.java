package com.tuneflow.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Shared reset-token store for multi-node deployments. */
@Component
@ConditionalOnProperty(name = "tuneflow.cache.provider", havingValue = "redis")
@RequiredArgsConstructor
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {

    private static final String PREFIX = "pwreset:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void store(String token, UUID userId, long ttlSeconds) {
        redisTemplate.opsForValue().set(PREFIX + token, userId.toString(), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<UUID> consume(String token) {
        String key = PREFIX + token;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        redisTemplate.delete(key);
        return Optional.of(UUID.fromString(value));
    }
}
