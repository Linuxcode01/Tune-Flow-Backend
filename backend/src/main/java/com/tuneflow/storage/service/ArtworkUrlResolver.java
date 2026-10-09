package com.tuneflow.storage.service;

import com.tuneflow.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Converts whatever is stored in an artwork column into something the Flutter client can render:
 *
 * <ul>
 *   <li>an absolute {@code http(s)} URL (CDN asset, external artwork) is passed through untouched;</li>
 *   <li>an object key produced by an upload is converted into a short-lived signed URL.</li>
 * </ul>
 *
 * This keeps media URLs sensitive and short-lived (docs section 39) while letting catalog endpoints
 * return ready-to-use artwork URLs.
 */
@Component
@RequiredArgsConstructor
public class ArtworkUrlResolver {

    /** Artwork lives longer than audio: it is small, cacheable and reused across screens. */
    private static final Duration ARTWORK_TTL = Duration.ofHours(6);

    private final StorageService storageService;
    private final AppProperties appProperties;

    public String resolve(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        try {
            return storageService.generateSignedUrl(trimmed, ARTWORK_TTL);
        } catch (RuntimeException ex) {
            // Never fail a catalog read because a media URL could not be signed.
            return null;
        }
    }

    public Duration artworkTtl() {
        return ARTWORK_TTL;
    }

    public String provider() {
        return storageService.provider();
    }

    /** Exposed for diagnostics through actuator info. */
    public String storageProvider() {
        return appProperties.media() == null ? storageService.provider() : storageService.provider();
    }
}
