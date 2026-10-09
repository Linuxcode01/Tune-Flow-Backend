package com.tuneflow.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-node fixed-window limiter used when {@code tuneflow.cache.provider=caffeine} (the default
 * local/dev profile). Counters expire automatically, so memory stays bounded.
 */
@Component
@ConditionalOnProperty(name = "tuneflow.cache.provider", havingValue = "caffeine", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    @Override
    public Decision check(String key, int limit, int windowSeconds) {
        Window window = windows.get(key, k -> new Window(Instant.now(), windowSeconds));
        if (window.isExpired()) {
            window = new Window(Instant.now(), windowSeconds);
            windows.put(key, window);
        }
        int count = window.counter.incrementAndGet();
        if (count > limit) {
            return Decision.denied(windowSeconds);
        }
        return Decision.allowed(limit - count, windowSeconds);
    }

    private static final class Window {
        private final Instant startedAt;
        private final int windowSeconds;
        private final AtomicInteger counter = new AtomicInteger();

        private Window(Instant startedAt, int windowSeconds) {
            this.startedAt = startedAt;
            this.windowSeconds = windowSeconds;
        }

        private boolean isExpired() {
            return Instant.now().isAfter(startedAt.plusSeconds(windowSeconds));
        }
    }
}
