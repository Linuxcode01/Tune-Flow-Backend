package com.tuneflow.user.service;

import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.auth.repository.RefreshTokenRepository;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.user.dto.ChangePasswordRequest;
import com.tuneflow.user.dto.UpdateProfileRequest;
import com.tuneflow.user.dto.UserResponse;
import com.tuneflow.user.entity.User;
import com.tuneflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        return toResponse(loadUser(SecurityUtils.requireUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        return toResponse(loadUser(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = loadUser(SecurityUtils.requireUserId());

        if (StringUtils.hasText(request.name())) {
            user.setName(request.name().trim());
        }
        if (StringUtils.hasText(request.email())) {
            String normalized = request.email().trim().toLowerCase();
            if (!normalized.equalsIgnoreCase(user.getEmail())) {
                userRepository.findByEmailIgnoreCase(normalized).ifPresent(existing -> {
                    throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
                });
                user.setEmail(normalized);
            }
        }
        if (request.profileImageUrl() != null) {
            user.setProfileImageUrl(StringUtils.hasText(request.profileImageUrl())
                    ? request.profileImageUrl().trim()
                    : null);
        }
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = loadUser(SecurityUtils.requireUserId());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.BAD_CREDENTIALS, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Invalidate previously issued access tokens and every refresh token for this account.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        int revoked = refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        log.info("Password changed for user {}; revoked {} refresh token(s)", user.getId(), revoked);
    }

    @Override
    @Transactional
    public void deactivateAccount() {
        UUID userId = SecurityUtils.requireUserId();
        User user = loadUser(userId);
        user.setEnabled(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Account deactivated for user {}", userId);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private UserResponse toResponse(User user) {
        UUID artistId = artistRepository.findByUser_Id(user.getId())
                .map(a -> a.getId())
                .orElse(null);
        return UserMapper.toResponse(user, artistId);
    }
}
