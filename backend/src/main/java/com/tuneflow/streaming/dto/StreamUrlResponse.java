package com.tuneflow.streaming.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Short-lived playback authorization (docs section 17). The audio bytes never pass through Spring
 * Boot: this response is the client's ticket to fetch them from object storage/CDN.
 */
@Schema(name = "StreamUrlResponse", description = "Short-lived signed media URL")
public record StreamUrlResponse(
        UUID songId,
        String title,
        String url,
        @Schema(description = "True when the URL points at an HLS manifest rather than a single file")
        boolean hls,
        Instant expiresAt,
        long expiresInSeconds,
        Integer bitrateKbps,
        String contentType
) {
}
