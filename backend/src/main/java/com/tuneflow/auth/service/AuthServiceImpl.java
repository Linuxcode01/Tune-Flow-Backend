package com.tuneflow.auth.service;

import com.tuneflow.artist.entity.Artist;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.auth.dto.AuthResponse;
import com.tuneflow.auth.dto.ForgotPasswordRequest;
import com.tuneflow.auth.dto.LoginRequest;
import com.tuneflow.auth.dto.PasswordResetResponse;
import com.tuneflow.auth.dto.RefreshTokenRequest;
import com.tuneflow.auth.dto.RegisterRequest;
import com.tuneflow.auth.dto.ResetPasswordRequest;
import com.tuneflow.auth.entity.RefreshToken;
import com.tuneflow.auth.repository.RefreshTokenRepository;
import com.tuneflow.auth.security.JwtService;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.security.Role;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.common.util.HashUtils;
import com.tuneflow.user.dto.UserResponse;
import com.tuneflow.user.entity.User;
import com.tuneflow.user.repository.UserRepository;
import com.tuneflow.user.service.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication and session lifecycle (docs section 15).
 *
 * <ul>
 *   <li>Passwords are BCrypt-hashed; plaintext is never stored and never logged.</li>
 *   <li>Access tokens are short-lived JWTs; refresh tokens are opaque, stored as SHA-256 digests,
 *       rotated on every use and revoked on logout / password change.</li>
 *   <li>Reuse of an already-rotated refresh token is treated as theft and revokes the whole family.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long PASSWORD_RESET_TTL_SECONDS = 900L; // 15 minutes
    private static final int MAX_DEV_TOKENS = 1_000;

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenStore passwordResetTokenStore;

    /**
     * When true the generated reset token is echoed back in the API response so the reset flow can
     * be exercised locally without an SMTP provider. Never enable in production.
     */
    @Value("${tuneflow.security.expose-reset-token:false}")
    private boolean exposeResetToken;

    /** Dev-only, bounded map of last issued reset token per user. */
    private final Map<UUID, String> devResetTokens = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // ADMIN can never be self-assigned; an unset/unknown role falls back to USER.
        Role role = request.role() == Role.ARTIST ? Role.ARTIST : Role.USER;

        User user = userRepository.save(User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .enabled(true)
                .build());

        UUID artistId = null;
        if (role == Role.ARTIST) {
            Artist artist = artistRepository.save(Artist.builder()
                    .user(user)
                    .name(user.getName())
                    .verified(false)
                    .build());
            artistId = artist.getId();
        }

        log.info("Registered new {} account {}", role, user.getId());
        return issueTokens(user, artistId, null, userAgent);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_CREDENTIALS));

        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This account has been deactivated");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Deliberately vague: never reveal whether the email exists.
            log.warn("Failed login attempt for account {}", user.getId());
            throw new ApiException(ErrorCode.BAD_CREDENTIALS);
        }

        UUID artistId = artistRepository.findByUser_Id(user.getId()).map(Artist::getId).orElse(null);
        return issueTokens(user, artistId, request.deviceId(), userAgent);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request, String userAgent) {
        String tokenHash = HashUtils.sha256Hex(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (stored.getRevokedAt() != null) {
            // Token replay: the family is compromised, revoke every active session.
            log.warn("Refresh token replay detected for user {}; revoking all sessions", stored.getUser().getId());
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId(), Instant.now());
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token has already been used");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token has expired");
        }

        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This account has been deactivated");
        }

        String rotatedRefreshToken = HashUtils.randomToken();
        stored.setRevokedAt(Instant.now());
        stored.setReplacedByHash(HashUtils.sha256Hex(rotatedRefreshToken));
        refreshTokenRepository.save(stored);

        UUID artistId = artistRepository.findByUser_Id(user.getId()).map(Artist::getId).orElse(null);
        String deviceId = StringUtils.hasText(request.deviceId()) ? request.deviceId() : stored.getDeviceId();
        return issueTokens(user, artistId, deviceId, userAgent, rotatedRefreshToken);
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByTokenHash(HashUtils.sha256Hex(request.refreshToken()))
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.setRevokedAt(Instant.now());
                        refreshTokenRepository.save(token);
                    }
                });
    }

    @Override
    @Transactional
    public void logoutAllDevices() {
        UUID userId = SecurityUtils.requireUserId();
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Revoked {} refresh token(s) for user {}", revoked, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PasswordResetResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        String devToken = null;

        var maybeUser = userRepository.findByEmailIgnoreCase(email);
        if (maybeUser.isPresent()) {
            User user = maybeUser.get();
            String generated = HashUtils.randomToken();
            passwordResetTokenStore.store(generated, user.getId(), PASSWORD_RESET_TTL_SECONDS);
            if (exposeResetToken) {
                if (devResetTokens.size() >= MAX_DEV_TOKENS) {
                    devResetTokens.clear();
                }
                devResetTokens.put(user.getId(), generated);
                devToken = generated;
                log.debug("Password reset token issued for user {} (dev exposure enabled)", user.getId());
            }
        }

        // Identical response whether or not the account exists: no user enumeration.
        return new PasswordResetResponse(
                "If an account exists for that email, a password reset link has been sent.",
                devToken,
                PASSWORD_RESET_TTL_SECONDS);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        UUID userId = passwordResetTokenStore.consume(request.token())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN, "Reset token is invalid or has expired"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        devResetTokens.remove(userId);
        log.info("Password reset completed for user {}", userId);
    }

    // ---------------------------------------------------------------------------------------------

    private AuthResponse issueTokens(User user, UUID artistId, String deviceId, String userAgent) {
        return issueTokens(user, artistId, deviceId, userAgent, HashUtils.randomToken());
    }

    private AuthResponse issueTokens(User user, UUID artistId, String deviceId, String userAgent, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getName(), user.getRole());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(HashUtils.sha256Hex(refreshToken))
                .expiresAt(Instant.now().plus(jwtService.refreshTokenTtl()))
                .deviceId(deviceId)
                .userAgent(trim(userAgent, 300))
                .build());

        UserResponse userResponse = UserMapper.toResponse(user, artistId);
        return AuthResponse.of(accessToken, refreshToken, jwtService.accessTokenTtl().toSeconds(), userResponse);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
