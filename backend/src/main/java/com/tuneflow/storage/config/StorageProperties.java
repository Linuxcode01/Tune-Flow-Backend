package com.tuneflow.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object storage configuration (docs sections 16-17, 26).
 *
 * <pre>
 * tuneflow:
 *   storage:
 *     provider: local | s3
 *     local-base-path: ./data/media
 *     s3:
 *       endpoint: http://localhost:9000
 *       region: us-east-1
 *       bucket: tuneflow-media
 *       access-key: minioadmin
 *       secret-key: minioadmin
 *       path-style-access: true
 * </pre>
 */
@ConfigurationProperties(prefix = "tuneflow.storage")
public record StorageProperties(
        String provider,
        String localBasePath,
        String publicBaseUrl,
        S3 s3
) {

    public StorageProperties {
        provider = provider == null || provider.isBlank() ? "local" : provider;
        localBasePath = localBasePath == null || localBasePath.isBlank() ? "./data/media" : localBasePath;
        publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl;
        s3 = s3 == null ? S3.empty() : s3;
    }

    public boolean isS3() {
        return "s3".equalsIgnoreCase(provider) || "minio".equalsIgnoreCase(provider);
    }

    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey,
                     boolean pathStyleAccess) {
        public S3 {
            region = region == null || region.isBlank() ? "us-east-1" : region;
            bucket = bucket == null || bucket.isBlank() ? "tuneflow-media" : bucket;
        }

        public static S3 empty() {
            return new S3(null, null, null, null, null, true);
        }
    }
}
