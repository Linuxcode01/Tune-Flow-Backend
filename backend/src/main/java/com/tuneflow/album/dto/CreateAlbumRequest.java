package com.tuneflow.album.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "CreateAlbumRequest")
public record CreateAlbumRequest(
        @Schema(description = "Required for admins; artists may omit it (their own profile is used)")
        UUID artistId,

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 2000)
        String description,

        @PastOrPresent(message = "Release date cannot be in the future")
        LocalDate releaseDate,

        String coverImageObjectKey
) {
}
