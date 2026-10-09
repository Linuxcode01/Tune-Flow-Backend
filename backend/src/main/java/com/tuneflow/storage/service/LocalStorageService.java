package com.tuneflow.storage.service;

import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.util.HashUtils;
import com.tuneflow.storage.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Filesystem-backed storage for local development (default provider).
 *
 * <p>It reproduces the *semantics* of S3 so the streaming flow is identical in dev and prod: the
 * API never serves bytes without a valid, unexpired HMAC signature, and URLs are short-lived.
 * Swapping {@code tuneflow.storage.provider} to {@code s3} switches to real pre-signed MinIO/S3
 * URLs with no application-code changes.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "tuneflow.storage.provider", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final StorageProperties storageProperties;
    private final StorageSigningKeyProvider keyProvider;

    private Path root;

    @PostConstruct
    void init() {
        this.root = Paths.get(storageProperties.localBasePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Local media storage initialised at {}", root);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create media directory " + root, ex);
        }
    }

    @Override
    public String store(String objectKey, InputStream content, long contentLength, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored {} ({} bytes, {})", objectKey, contentLength, contentType);
            return objectKey;
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.STORAGE_ERROR, "Failed to store media object", ex.getMessage());
        }
    }

    @Override
    public String generateSignedUrl(String objectKey, Duration ttl) {
        // Assets already hosted elsewhere (an external CDN, a licensed audio provider) are used
        // as-is; only assets in our own bucket need signing.
        if (objectKey != null && (objectKey.startsWith("http://") || objectKey.startsWith("https://"))) {
            return objectKey;
        }
        long expiresAt = Instant.now().plus(ttl).getEpochSecond();
        String signature = sign(objectKey, expiresAt);
        String base = storageProperties.publicBaseUrl();
        String path = "/api/v1/media/" + objectKey + "?expires=" + expiresAt + "&signature=" + signature;
        return base.isBlank() ? path : trimTrailingSlash(base) + path;
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException ex) {
            log.warn("Failed to delete media object {}: {}", objectKey, ex.getMessage());
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolve(objectKey));
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public Optional<SignedRequest> validateSignedRequest(String objectKey, long expiresAtEpochSeconds, String signature) {
        if (!StringUtils.hasText(signature)) {
            return Optional.empty();
        }
        if (Instant.now().getEpochSecond() > expiresAtEpochSeconds) {
            return Optional.empty();
        }
        String expected = sign(objectKey, expiresAtEpochSeconds);
        boolean valid = constantTimeEquals(expected, signature);
        return valid ? Optional.of(new SignedRequest(objectKey)) : Optional.empty();
    }

    /** Resolves a key inside the storage root and refuses path traversal attempts. */
    public Path resolve(String objectKey) {
        Path candidate = root.resolve(objectKey).normalize();
        if (!candidate.startsWith(root)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Invalid media key");
        }
        return candidate;
    }

    public Path root() {
        return root;
    }

    private String sign(String objectKey, long expiresAtEpochSeconds) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyProvider.signingKey(), HMAC_ALGORITHM));
            String payload = objectKey + "|" + expiresAtEpochSeconds;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign media URL", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** Derives a stable signing key from the configured JWT secret (no extra secret to manage). */
    public static String defaultSigningKey(String jwtSecret) {
        return "tuneflow-media:" + HashUtils.sha256Hex(jwtSecret == null ? "dev" : jwtSecret);
    }
}
