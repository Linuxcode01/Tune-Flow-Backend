package com.tuneflow.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** In-process reset-token store (default dev profile). Tokens are single use and time-boxed. */
@Component
@ConditionalOnProperty(name = "tuneflow.cache.provider", havingValue = "caffeine", matchIfMissing = true)
public class CaffeinePasswordResetTokenStore implements PasswordResetTokenStore {

    private final Cache<String, Entry> tokens = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfter(new Expiry<String, Entry>() {
                @Override
                public long expireAfterCreate(String key, Entry value, long currentTime) {
                    return Duration.ofSeconds(value.ttlSeconds()).toNanos();
                }

                @Override
                public long expireAfterUpdate(String key, Entry value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(String key, Entry value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    @Override
    public void store(String token, UUID userId, long ttlSeconds) {
        tokens.put(token, new Entry(userId, ttlSeconds));
    }

    @Override
    public Optional<UUID> consume(String token) {
        Entry entry = tokens.asMap().remove(token);
        return Optional.ofNullable(entry).map(Entry::userId);
    }

    private record Entry(UUID userId, long ttlSeconds) {
    }
}
