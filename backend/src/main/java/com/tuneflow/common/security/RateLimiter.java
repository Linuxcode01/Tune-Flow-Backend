package com.tuneflow.common.security;

/**
 * Rate-limit counter backend. Implemented by an in-memory (Caffeine) limiter for single-node
 * development and a Redis limiter for horizontally scaled deployments, so the same policy code
 * works in both (docs section 29: "Redis can support distributed counters").
 */
public interface RateLimiter {

    Decision check(String key, int limit, int windowSeconds);

    record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
        public static Decision allowed(long remaining, int windowSeconds) {
            return new Decision(true, remaining, 0);
        }

        public static Decision denied(int windowSeconds) {
            return new Decision(false, 0, windowSeconds);
        }
    }
}
