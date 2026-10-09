package com.tuneflow.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A chunked upload session: what the server expects next, and where it already is.
 *
 * <p>Returned by both {@code initiate} and {@code GET /uploads/audio/{uploadId}} - the status form is
 * what makes the flow resumable, because it reports which parts already landed. A client that was
 * killed mid-upload can reopen the session and send only the missing parts instead of starting over.
 */
@Schema(name = "UploadSessionResponse", description = """
        Chunked upload session. The client slices the file into `partCount` parts of at most
        `chunkSizeBytes`, PUTs each one to `/uploads/audio/{uploadId}/parts/{partNumber}`, then calls
        complete. `receivedParts` lists the parts the server already has, so a resumed upload can skip
        them.
        """)
public record InitiateUploadResponse(
        UUID uploadId,
        int chunkSizeBytes,
        int partCount,
        long sizeBytes,
        List<Integer> receivedParts,
        Instant expiresAt
) {
    public InitiateUploadResponse withReceivedParts(List<Integer> parts) {
        return new InitiateUploadResponse(uploadId, chunkSizeBytes, partCount, sizeBytes, parts, expiresAt);
    }
}
