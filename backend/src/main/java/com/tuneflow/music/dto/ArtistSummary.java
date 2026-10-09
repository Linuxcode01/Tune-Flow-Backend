package com.tuneflow.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Lightweight artist reference embedded in song/album payloads to avoid extra round trips. */
@Schema(name = "ArtistSummary")
public record ArtistSummary(
        UUID id,
        String name,
        String profileImageUrl,
        boolean verified
) {
}
