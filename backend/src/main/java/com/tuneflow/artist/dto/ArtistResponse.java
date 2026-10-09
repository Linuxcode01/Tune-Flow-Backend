package com.tuneflow.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ArtistResponse")
public record ArtistResponse(
        UUID id,
        String name,
        String bio,
        String profileImageUrl,
        String country,
        boolean verified,
        long followerCount,
        long songCount,
        long albumCount,
        @Schema(description = "Linked user account id, when the artist profile is claimed")
        UUID userId,
        @Schema(description = "True when the authenticated user follows this artist; null when anonymous")
        Boolean following,
        Instant createdAt
) {
}
