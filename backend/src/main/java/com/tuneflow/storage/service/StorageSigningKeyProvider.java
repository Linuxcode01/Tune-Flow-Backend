package com.tuneflow.storage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Supplies the HMAC key used to sign media URLs. It is derived from the configured JWT secret so
 * deployments manage a single secret, and it is never logged or returned by an endpoint.
 */
@Component
public class StorageSigningKeyProvider {

    private final byte[] signingKey;

    public StorageSigningKeyProvider(
            @Value("${tuneflow.jwt.secret:}") String jwtSecret,
            @Value("${tuneflow.storage.signing-secret:}") String mediaSecret) {

        String effective = mediaSecret != null && !mediaSecret.isBlank() ? mediaSecret : jwtSecret;
        if (effective == null || effective.isBlank()) {
            effective = "tuneflow-local-development-secret-change-me";
        }
        this.signingKey = LocalStorageService.defaultSigningKey(effective).getBytes(StandardCharsets.UTF_8);
    }

    public byte[] signingKey() {
        return signingKey.clone();
    }
}
