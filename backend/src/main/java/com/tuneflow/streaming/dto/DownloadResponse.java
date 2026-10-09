package com.tuneflow.streaming.dto;

import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.streaming.entity.Download;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "DownloadResponse", description = "Offline download metadata; the URL is short-lived")
public record DownloadResponse(
        UUID id,
        SongResponse song,
        String deviceId,
        Download.DownloadStatus status,
        Integer bitrateKbps,
        Long sizeBytes,
        String downloadUrl,
        Instant licenceExpiresAt,
        Instant downloadedAt,
        Instant createdAt
) {
}
