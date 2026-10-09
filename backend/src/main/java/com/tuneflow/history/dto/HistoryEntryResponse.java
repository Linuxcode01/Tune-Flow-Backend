package com.tuneflow.history.dto;

import com.tuneflow.music.dto.SongResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "HistoryEntryResponse")
public record HistoryEntryResponse(
        UUID id,
        SongResponse song,
        Instant startedAt,
        Instant completedAt,
        int playedSeconds,
        Double completionRatio
) {
}
