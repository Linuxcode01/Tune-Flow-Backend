package com.tuneflow.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Opens a chunked audio upload ({@code POST /uploads/audio/initiate}).
 *
 * <p>The client announces what it is about to send so the server can reject an unsupported format or
 * an oversized file <em>before</em> any bytes travel - the whole point of chunking is not to waste a
 * mobile connection on an upload that was never going to be accepted.
 */
@Schema(name = "InitiateUploadRequest", example = """
        {
          "fileName": "neon-skyline.mp3",
          "sizeBytes": 7340032,
          "contentType": "audio/mpeg"
        }
        """)
public record InitiateUploadRequest(
        @NotBlank(message = "fileName is required")
        @Size(max = 255, message = "fileName must be at most 255 characters")
        String fileName,

        @NotNull(message = "sizeBytes is required")
        @Min(value = 1, message = "sizeBytes must be greater than zero")
        Long sizeBytes,

        String contentType
) {
}
