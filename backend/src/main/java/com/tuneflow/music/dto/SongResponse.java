package com.tuneflow.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Song as returned by catalog endpoints.
 *
 * <p>Note what is <b>not</b> here: the audio URL. Clients obtain playback access through
 * {@code GET /api/v1/songs/{id}/stream}, which authorizes the request and returns a short-lived
 * signed URL (docs sections 17 and 39). {@code streamAvailable} tells the UI whether playback is
 * possible before it asks for a URL.
 */
@Schema(name = "SongResponse")
public record SongResponse(
        UUID id,
        String title,
        int durationSeconds,
        Integer trackNumber,
        long playCount,
        long likeCount,
        LocalDate releaseDate,
        boolean explicit,
        String coverImageUrl,
        boolean streamAvailable,
        ArtistSummary artist,
        AlbumSummary album,
        List<GenreResponse> genres,
        @Schema(description = "True when the authenticated user has liked this song; null when anonymous")
        Boolean liked
) {
}
