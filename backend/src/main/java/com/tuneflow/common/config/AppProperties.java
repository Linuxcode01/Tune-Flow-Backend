package com.tuneflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Application-level settings that are business-relevant rather than framework plumbing.
 *
 * <pre>
 * tuneflow:
 *   cache:
 *     provider: caffeine | redis
 *   events:
 *     publisher: noop | kafka
 *   media:
 *     signed-url-ttl: 10m
 * </pre>
 */
@ConfigurationProperties(prefix = "tuneflow")
public record AppProperties(
        Cache cache,
        Events events,
        Media media,
        Cors cors,
        RateLimit rateLimit,
        Seed seed
) {

    public record Cache(
            String provider,
            Duration defaultTtl,
            Duration songTtl,
            Duration albumTtl,
            Duration artistTtl,
            Duration searchTtl,
            Duration homeFeedTtl,
            Duration recommendationTtl
    ) {
        public Cache {
            provider = provider == null ? "caffeine" : provider;
            defaultTtl = orDefault(defaultTtl, Duration.ofMinutes(10));
            songTtl = orDefault(songTtl, Duration.ofMinutes(15));
            albumTtl = orDefault(albumTtl, Duration.ofMinutes(15));
            artistTtl = orDefault(artistTtl, Duration.ofMinutes(30));
            searchTtl = orDefault(searchTtl, Duration.ofMinutes(5));
            homeFeedTtl = orDefault(homeFeedTtl, Duration.ofMinutes(5));
            recommendationTtl = orDefault(recommendationTtl, Duration.ofMinutes(30));
        }

        public boolean isRedis() {
            return "redis".equalsIgnoreCase(provider);
        }
    }

    public record Events(
            boolean enabled,
            String publisher,
            String topic
    ) {
        public Events {
            publisher = publisher == null ? "noop" : publisher;
            topic = topic == null ? "tuneflow.events" : topic;
        }

        public boolean isKafka() {
            return enabled && "kafka".equalsIgnoreCase(publisher);
        }
    }

    public record Media(
            Duration signedUrlTtl,
            Integer defaultBitrateKbps,
            Long maxAudioSizeBytes,
            Long maxImageSizeBytes,
            List<String> allowedAudioFormats,
            List<String> allowedImageFormats,
            Integer chunkSizeBytes,
            Duration uploadSessionTtl,
            String chunkTempPath
    ) {
        /** 4 MB: small enough that a retry costs seconds, large enough that a 40 MB track is 10 parts. */
        private static final int DEFAULT_CHUNK_SIZE_BYTES = 4 * 1024 * 1024;

        public Media {
            signedUrlTtl = orDefault(signedUrlTtl, Duration.ofMinutes(10));
            defaultBitrateKbps = defaultBitrateKbps == null ? 128 : defaultBitrateKbps;
            maxAudioSizeBytes = maxAudioSizeBytes == null ? 104_857_600L : maxAudioSizeBytes; // 100 MB
            maxImageSizeBytes = maxImageSizeBytes == null ? 10_485_760L : maxImageSizeBytes;  // 10 MB
            allowedAudioFormats = orEmpty(allowedAudioFormats, List.of("mp3", "m4a", "aac", "wav", "flac", "ogg"));
            allowedImageFormats = orEmpty(allowedImageFormats, List.of("jpg", "jpeg", "png", "webp"));
            // Guard rail, not a policy: a chunk below 64 KB would turn one track into thousands of
            // requests, so a value that small is treated as a misconfiguration and replaced.
            chunkSizeBytes = chunkSizeBytes == null || chunkSizeBytes < 64 * 1024
                    ? DEFAULT_CHUNK_SIZE_BYTES
                    : chunkSizeBytes;
            uploadSessionTtl = orDefault(uploadSessionTtl, Duration.ofHours(24));
            chunkTempPath = (chunkTempPath == null || chunkTempPath.isBlank())
                    ? "./data/uploads"
                    : chunkTempPath;
        }
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedMethods, boolean allowCredentials) {
        public Cors {
            allowedOrigins = orEmpty(allowedOrigins, List.of("*"));
            allowedMethods = orEmpty(allowedMethods, List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        }
    }

    public record RateLimit(boolean enabled, int generalPerMinute, int authPerMinute, int uploadPerMinute) {
        public RateLimit {
            generalPerMinute = generalPerMinute <= 0 ? 100 : generalPerMinute;
            authPerMinute = authPerMinute <= 0 ? 5 : authPerMinute;
            uploadPerMinute = uploadPerMinute <= 0 ? 10 : uploadPerMinute;
        }
    }

    /** Demo data bootstrap. Disabled for real deployments. */
    public record Seed(boolean enabled, boolean demoUsers) {
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

    private static List<String> orEmpty(List<String> value, List<String> fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
