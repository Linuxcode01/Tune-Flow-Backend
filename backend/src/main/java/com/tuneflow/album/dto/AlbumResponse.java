package com.tuneflow.album.dto;

import com.tuneflow.music.dto.ArtistSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "AlbumResponse")
public record AlbumResponse(
        UUID id,
        String title,
        String description,
        String coverImageUrl,
        LocalDate releaseDate,
        int songCount,
        int totalDurationSeconds,
        ArtistSummary artist,
        @Schema(description = "True when the authenticated user follows the album's artist; null when anonymous")
        Boolean followingArtist
) {
}
