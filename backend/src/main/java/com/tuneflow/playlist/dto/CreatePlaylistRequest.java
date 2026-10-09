package com.tuneflow.playlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreatePlaylistRequest")
public record CreatePlaylistRequest(
        @NotBlank(message = "Playlist name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        @Size(max = 2000)
        String description,

        boolean publicPlaylist,

        String coverImageObjectKey
) {
}
