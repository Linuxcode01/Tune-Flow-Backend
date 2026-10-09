package com.tuneflow.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Listening statistics for the current user (docs section 13).
 *
 * <p>Powers the "Your listening" card in the client's Library tab. Counts are computed from the
 * playback history rows, which are cheap aggregates on indexed columns.
 */
@Schema(name = "HistoryStatsResponse", description = "Aggregated listening statistics")
public record HistoryStatsResponse(
        long totalPlays,
        long totalListeningSeconds,
        long completedPlays,
        long uniqueSongs,
        long uniqueArtists,
        List<TopArtist> topArtists,
        List<TopGenre> topGenres,
        List<DailyPlays> lastSevenDays
) {

    @Schema(name = "TopArtist")
    public record TopArtist(UUID id, String name, String imageUrl, long playCount) {
    }

    @Schema(name = "TopGenre")
    public record TopGenre(UUID id, String name, String slug, long playCount) {
    }

    @Schema(name = "DailyPlays")
    public record DailyPlays(LocalDate date, long plays) {
    }
}
