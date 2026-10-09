package com.tuneflow.playlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "PlaylistResponse")
public record PlaylistResponse(
        UUID id,
        String name,
        String description,
        String coverImageUrl,
        boolean publicPlaylist,
        boolean systemPlaylist,
        int songCount,
        int totalDurationSeconds,
        UUID ownerId,
        String ownerName,
        boolean ownedByCurrentUser,
        Instant createdAt,
        Instant updatedAt
) {
}
