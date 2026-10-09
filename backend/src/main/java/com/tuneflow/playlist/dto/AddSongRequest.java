package com.tuneflow.playlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(name = "AddSongRequest")
public record AddSongRequest(
        @NotNull(message = "songId is required")
        UUID songId,

        @Schema(description = "Optional 0-based insertion index; appended to the end when omitted")
        Integer position
) {
}
