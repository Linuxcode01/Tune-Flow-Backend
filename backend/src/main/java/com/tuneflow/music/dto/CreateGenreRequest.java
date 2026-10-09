package com.tuneflow.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateGenreRequest")
public record CreateGenreRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must be at most 80 characters")
        String name,

        @Schema(description = "Optional; derived from the name when omitted")
        @Size(max = 80, message = "Slug must be at most 80 characters")
        String slug,

        @Size(max = 500)
        String description,

        String coverImageObjectKey
) {
}
