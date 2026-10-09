package com.tuneflow.lyrics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update lyrics. For synchronized lyrics, {@code content} holds LRC lines such as
 * {@code [00:12.50]First line of the chorus}.
 */
@Schema(name = "UpsertLyricsRequest", example = """
        {
          "language": "en",
          "synced": true,
          "content": "[00:00.00]Intro\\n[00:12.40]Neon skyline over me\\n[00:18.10]City hums in harmony"
        }
        """)
public record UpsertLyricsRequest(
        @NotBlank(message = "Language is required")
        @Size(max = 10, message = "Use an ISO 639-1 language code")
        String language,

        @NotBlank(message = "Lyrics content is required")
        @Size(max = 50000, message = "Lyrics are too long")
        String content,

        boolean synced
) {
}
