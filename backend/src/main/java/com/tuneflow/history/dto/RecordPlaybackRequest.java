package com.tuneflow.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Playback progress event (docs section 13: {@code POST /history}).
 *
 * <p>The Flutter player batches these. {@code sessionId} groups progress updates for one playback
 * so history has one row per listen rather than one row per seek.
 */
@Schema(name = "RecordPlaybackRequest", example = """
        {
          "songId": "9c0f…",
          "sessionId": "4f8b…",
          "playedSeconds": 143,
          "completed": true,
          "deviceId": "pixel-8-abc",
          "offline": false
        }
        """)
public record RecordPlaybackRequest(
        @NotNull(message = "songId is required")
        UUID songId,

        @Schema(description = "Client-generated id for this playback session")
        UUID sessionId,

        @Min(value = 0, message = "playedSeconds cannot be negative")
        @Max(value = 86400, message = "playedSeconds is implausible")
        int playedSeconds,

        boolean completed,

        @Size(max = 120)
        String deviceId,

        @Schema(description = "True when the playback happened from an offline download")
        boolean offline,

        @Schema(description = """
                Set by the client on the last progress update for a playback session (pause, skip or
                end). Analytics uses it to count a play exactly once instead of once per progress tick.
                """)
        boolean finalUpdate
) {
}
