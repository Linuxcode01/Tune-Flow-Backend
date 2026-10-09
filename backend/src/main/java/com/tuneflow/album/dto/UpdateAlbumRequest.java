package com.tuneflow.album.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Partial album update; null fields are left unchanged. */
@Schema(name = "UpdateAlbumRequest")
public record UpdateAlbumRequest(
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 2000)
        String description,

        @PastOrPresent(message = "Release date cannot be in the future")
        LocalDate releaseDate,

        String coverImageObjectKey
) {
}
