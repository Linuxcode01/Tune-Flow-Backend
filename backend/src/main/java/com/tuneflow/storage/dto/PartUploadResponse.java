package com.tuneflow.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Acknowledgement of one stored part. The client uses {@code receivedBytes} to advance its progress
 * bar and {@code complete} to know whether the last part has landed.
 */
@Schema(name = "PartUploadResponse")
public record PartUploadResponse(
        int partNumber,
        long partSizeBytes,
        long receivedBytes,
        long sizeBytes,
        boolean complete
) {
}
