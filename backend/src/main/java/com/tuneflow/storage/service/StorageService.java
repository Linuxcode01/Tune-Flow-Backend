package com.tuneflow.storage.service;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/**
 * Media storage abstraction (docs section 39): the application authorizes access and hands the
 * client a short-lived URL; the object store/CDN delivers the bytes. Permanent public URLs are
 * never exposed.
 */
public interface StorageService {

    /**
     * Uploads an object.
     *
     * @return the object key to persist in the database (<em>not</em> a public URL)
     */
    String store(String objectKey, InputStream content, long contentLength, String contentType);

    /** Short-lived, pre-authorized URL for direct client streaming/download. */
    String generateSignedUrl(String objectKey, Duration ttl);

    void delete(String objectKey);

    boolean exists(String objectKey);

    /** Provider name, e.g. {@code local} or {@code s3} - surfaced in health/actuator info. */
    String provider();

    /** Local provider only: used by the media endpoint to validate a signed request. */
    default Optional<SignedRequest> validateSignedRequest(String objectKey, long expiresAtEpochSeconds, String signature) {
        return Optional.empty();
    }

    record SignedRequest(String objectKey) {
    }
}
