package com.tuneflow.auth;

import com.tuneflow.auth.security.JwtProperties;
import com.tuneflow.auth.security.JwtService;
import com.tuneflow.auth.security.JwtServiceImpl;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for token issuing/parsing (docs section 31 - unit layer, JUnit 5 + AssertJ). */
class JwtServiceImplTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-for-hs384";

    private JwtServiceImpl jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(SECRET, "tuneflow-test",
                Duration.ofMinutes(15), Duration.ofDays(30));
        jwtService = new JwtServiceImpl(properties);
        // @PostConstruct is not invoked outside a Spring context.
        ReflectionTestUtils.invokeMethod(jwtService, "init");
    }

    @Test
    @DisplayName("access token round-trips identity and role claims")
    void accessTokenRoundTrip() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "aria@tuneflow.dev", "Aria", Role.ARTIST);

        JwtService.JwtClaims claims = jwtService.parseAccessToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.email()).isEqualTo("aria@tuneflow.dev");
        assertThat(claims.name()).isEqualTo("Aria");
        assertThat(claims.role()).isEqualTo(Role.ARTIST);
        assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
    }

    @Test
    @DisplayName("tampered token is rejected as INVALID_TOKEN")
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "a@b.c", "A", Role.USER);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("expired token is reported separately so the client knows to refresh")
    void expiredTokenIsReportedAsExpired() {
        JwtProperties shortLived = new JwtProperties(SECRET, "tuneflow-test",
                Duration.ofSeconds(-10), Duration.ofDays(1));
        JwtServiceImpl expiring = new JwtServiceImpl(shortLived);
        ReflectionTestUtils.invokeMethod(expiring, "init");

        String token = expiring.generateAccessToken(UUID.randomUUID(), "a@b.c", "A", Role.USER);

        assertThatThrownBy(() -> expiring.parseAccessToken(token))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("a token signed with a different key cannot be verified")
    void foreignKeyIsRejected() {
        JwtProperties other = new JwtProperties("a-completely-different-secret-key-value-here!!",
                "tuneflow-test", Duration.ofMinutes(15), Duration.ofDays(1));
        JwtServiceImpl otherService = new JwtServiceImpl(other);
        ReflectionTestUtils.invokeMethod(otherService, "init");

        String foreignToken = otherService.generateAccessToken(UUID.randomUUID(), "a@b.c", "A", Role.USER);

        assertThatThrownBy(() -> jwtService.parseAccessToken(foreignToken))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a too-short secret is rejected at startup rather than silently weak")
    void shortSecretFailsFast() {
        JwtServiceImpl weak = new JwtServiceImpl(new JwtProperties("too-short", "tuneflow", null, null));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(weak, "init"))
                .hasMessageContaining("at least");
    }
}
