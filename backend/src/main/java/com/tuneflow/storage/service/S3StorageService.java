package com.tuneflow.storage.service;

import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.storage.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * S3 / MinIO implementation (docs sections 16-17). Selected with
 * {@code tuneflow.storage.provider=s3} and works against MinIO because path-style access is
 * configurable.
 *
 * <p>Streaming uses genuine pre-signed GET URLs, so audio bytes flow straight from the object
 * store or CDN to the Flutter client - never through this application server.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "tuneflow.storage.provider", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final StorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3StorageService(StorageProperties properties) {
        this.properties = properties;
        StorageProperties.S3 s3 = properties.s3();

        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(nvl(s3.accessKey(), "minioadmin"), nvl(s3.secretKey(), "minioadmin")));

        var clientBuilder = S3Client.builder()
                .region(Region.of(s3.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.pathStyleAccess())
                        .build());

        var presignerBuilder = S3Presigner.builder()
                .region(Region.of(s3.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.pathStyleAccess())
                        .build());

        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            clientBuilder.endpointOverride(URI.create(s3.endpoint()));
            presignerBuilder.endpointOverride(URI.create(s3.endpoint()));
        }

        this.s3Client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
        log.info("S3/MinIO storage initialised (bucket={}, endpoint={})", s3.bucket(), s3.endpoint());
    }

    @Override
    public String store(String objectKey, InputStream content, long contentLength, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(content, contentLength));
            return objectKey;
        } catch (RuntimeException ex) {
            log.error("Failed to upload {} to S3/MinIO: {}", objectKey, ex.getMessage());
            throw new ApiException(ErrorCode.STORAGE_ERROR, "Failed to store media object");
        }
    }

    @Override
    public String generateSignedUrl(String objectKey, Duration ttl) {
        if (objectKey != null && (objectKey.startsWith("http://") || objectKey.startsWith("https://"))) {
            return objectKey;
        }
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(objectKey)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(getObjectRequest)
                    .build();
            String url = presigner.presignGetObject(presignRequest).url().toString();
            String base = properties.publicBaseUrl();
            // CDN front: rewrite the origin host with the configured public CDN base when provided.
            if (base != null && !base.isBlank()) {
                java.net.URI uri = java.net.URI.create(url);
                String cdnBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
                String path = uri.getRawPath().replace("/" + properties.s3().bucket(), "");
                return cdnBase + path + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
            }
            return url;
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.STORAGE_ERROR, "Failed to sign media URL");
        }
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.s3().bucket())
                .key(objectKey)
                .build());
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (RuntimeException ex) {
            log.warn("Head object failed for {}: {}", objectKey, ex.getMessage());
            return false;
        }
    }

    @Override
    public String provider() {
        return "s3";
    }

    @Override
    public Optional<SignedRequest> validateSignedRequest(String objectKey, long expiresAtEpochSeconds, String signature) {
        // Pre-signed URLs are validated by S3/MinIO itself, not by the application.
        return Optional.empty();
    }

    private static String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
