package com.tuneflow.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Create song metadata (docs section 13: {@code POST /songs}).
 *
 * <p>Artists may only target their own artist profile; admins may target any artist.
 */
@Schema(name = "CreateSongRequest", example = """
        {
          "title": "Neon Skyline",
          "artistId": "3f1c…",
          "albumId": "8bb2…",
          "durationSeconds": 214,
          "trackNumber": 1,
          "releaseDate": "2026-09-12",
          "genres": ["electronic", "synthwave"],
          "audioObjectKey": "audio/2026/09/1f2e….mp3",
          "coverImageObjectKey": "images/2026/09/77aa….jpg"
        }
        """)
public record CreateSongRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Schema(description = "Required for admins; artists may omit it (their own profile is used)")
        UUID artistId,

        UUID albumId,

        @Min(value = 0, message = "Duration cannot be negative")
        @Max(value = 7200, message = "Duration looks unrealistic")
        int durationSeconds,

        @Min(value = 1, message = "Track number starts at 1")
        Integer trackNumber,

        @PastOrPresent(message = "Release date cannot be in the future")
        LocalDate releaseDate,

        boolean explicit,

        @Schema(description = "Genre slugs, e.g. [\"rock\", \"indie\"]")
        Set<String> genres,

        @Schema(description = "objectKey returned by POST /uploads/audio")
        String audioObjectKey,

        @Schema(description = "objectKey returned by POST /uploads/image")
        String coverImageObjectKey
) {
}
