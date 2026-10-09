package com.tuneflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuneflow.common.config.AppProperties;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.exception.ErrorResponseFactory;
import com.tuneflow.common.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limiting for abuse-prone endpoints (docs section 29).
 *
 * <ul>
 *   <li><b>auth</b> - login/register/forgot-password: per client IP, default 5 requests/minute</li>
 *   <li><b>expensive</b> - signed stream URL generation and downloads: per authenticated user</li>
 *   <li><b>upload-chunk</b> - individual parts of a chunked upload: generous, because retrying a
 *       single part is the intended failure mode rather than an abuse signal</li>
 *   <li><b>general</b> - everything else: per authenticated user, falling back to IP</li>
 * </ul>
 *
 * The counter backend is pluggable ({@link RateLimiter}): Caffeine for a single node, Redis for a
 * horizontally scaled deployment, so the same policy code works in both.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        AppProperties.RateLimit config = properties.rateLimit();
        if (config == null || !config.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        Policy policy = resolvePolicy(path, config);
        String clientIp = clientIp(request);
        String identity = policy.scope() == Scope.IP ? clientIp : authenticatedIdentityOrIp(clientIp);
        String key = "rl:" + policy.name() + ":" + identity;

        RateLimiter.Decision decision = rateLimiter.check(key, policy.limit(), policy.windowSeconds());
        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, decision.remaining())));

        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = ErrorResponseFactory.of(ErrorCode.RATE_LIMIT_EXCEEDED, path);
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Policy resolvePolicy(String path, AppProperties.RateLimit config) {
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/forgot-password")) {
            return new Policy("auth", config.authPerMinute(), 60, Scope.IP);
        }
        if (path.contains("/stream") || path.contains("/downloads")) {
            return new Policy("expensive", Math.max(30, config.uploadPerMinute() * 6), 60, Scope.USER);
        }
        // Chunked uploads are many small requests by design (a 40 MB track is ten parts, plus retries),
        // so they cannot share the tight per-upload budget with the metadata calls.
        if (path.startsWith("/api/v1/uploads/audio/") && path.contains("/parts/")) {
            return new Policy("upload-chunk", Math.max(240, config.generalPerMinute() * 6), 60, Scope.USER);
        }
        return new Policy("general", config.generalPerMinute(), 60, Scope.USER);
    }

    private static String authenticatedIdentityOrIp(String fallbackIp) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id().toString();
        }
        return fallbackIp;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    private enum Scope { IP, USER }

    private record Policy(String name, int limit, int windowSeconds, Scope scope) {
    }
}
