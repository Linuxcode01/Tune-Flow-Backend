package com.tuneflow.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UploadResponse", description = """
        Result of a media upload. `objectKey` is what the client persists via the song/album/artist
        update endpoints; the short-lived `previewUrl` is for immediate playback in the upload UI.
        """)
public record UploadResponse(
        String objectKey,
        String previewUrl,
        long expiresInSeconds,
        Integer durationSeconds,
        Integer bitrateKbps,
        String contentType,
        long sizeBytes,
        String checksum
) {
}
