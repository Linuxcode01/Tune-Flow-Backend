package com.tuneflow.lyrics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Lyrics for a song. When {@code synced} is true, {@code lines} carries the timestamped lines the
 * player matches against the current position (docs section 27).
 */
@Schema(name = "LyricsResponse")
public record LyricsResponse(
        UUID id,
        UUID songId,
        String language,
        boolean synced,
        String content,
        List<SyncedLine> lines
) {

    @Schema(name = "SyncedLine", description = "One timestamped lyric line")
    public record SyncedLine(long timeMs, String text) {
    }
}
