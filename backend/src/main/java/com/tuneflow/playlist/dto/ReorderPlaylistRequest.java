package com.tuneflow.playlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Full reorder: the client sends the complete set of song ids in the desired order. Supplying the
 * whole order (instead of "move item 3 to index 7") makes the operation idempotent and lets the
 * server reject partial/duplicated payloads.
 */
@Schema(name = "ReorderPlaylistRequest")
public record ReorderPlaylistRequest(
        @NotEmpty(message = "songIds must contain the playlist order")
        List<UUID> songIds
) {
}
