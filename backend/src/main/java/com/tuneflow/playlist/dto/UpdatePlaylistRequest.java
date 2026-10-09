package com.tuneflow.playlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Partial playlist update; null fields are left unchanged. */
@Schema(name = "UpdatePlaylistRequest")
public record UpdatePlaylistRequest(
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        @Size(max = 2000)
        String description,

        Boolean publicPlaylist,

        String coverImageObjectKey
) {
}
