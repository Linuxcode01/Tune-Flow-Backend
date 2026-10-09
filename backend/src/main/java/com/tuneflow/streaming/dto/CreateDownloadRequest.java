package com.tuneflow.streaming.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "CreateDownloadRequest", description = "Authorize an offline download for a device")
public record CreateDownloadRequest(
        @NotNull(message = "songId is required")
        UUID songId,

        @NotBlank(message = "deviceId is required")
        @Size(max = 120, message = "deviceId must be at most 120 characters")
        String deviceId,

        @Schema(description = "Requested rendition bitrate", allowableValues = {"96", "128", "320"})
        Integer bitrateKbps
) {
}
