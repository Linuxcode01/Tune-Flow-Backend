package com.tuneflow.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed sliding-ish fixed-window limiter for multi-node deployments
 * ({@code tuneflow.rate-limit.store=redis} / {@code tuneflow.cache.provider=redis}).
 * INCR + EXPIRE keeps the counter atomic across nodes.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tuneflow.cache.provider", havingValue = "redis")
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Override
    public Decision check(String key, int limit, int windowSeconds) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > limit) {
                return Decision.denied(windowSeconds);
            }
            return Decision.allowed(limit - (count == null ? 0 : count), windowSeconds);
        } catch (RuntimeException ex) {
            // Fail open: a Redis outage must not take the API down.
            log.warn("Rate limiter unavailable, allowing request: {}", ex.getMessage());
            return Decision.allowed(limit, windowSeconds);
        }
    }
}
