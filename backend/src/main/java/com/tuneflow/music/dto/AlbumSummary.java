package com.tuneflow.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/** Lightweight album reference embedded in song payloads. */
@Schema(name = "AlbumSummary")
public record AlbumSummary(
        UUID id,
        String title,
        String coverImageUrl,
        LocalDate releaseDate
) {
}
