package com.tuneflow.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Partial update for song metadata; null fields are left unchanged. */
@Schema(name = "UpdateSongRequest")
public record UpdateSongRequest(
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        UUID albumId,

        @Min(value = 0, message = "Duration cannot be negative")
        @Max(value = 7200, message = "Duration looks unrealistic")
        Integer durationSeconds,

        @Min(value = 1, message = "Track number starts at 1")
        Integer trackNumber,

        @PastOrPresent(message = "Release date cannot be in the future")
        LocalDate releaseDate,

        Boolean explicit,

        Set<String> genres,

        String audioObjectKey,

        String coverImageObjectKey
) {
}
