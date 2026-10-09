package com.tuneflow.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "GenreResponse")
public record GenreResponse(
        UUID id,
        String name,
        String slug,
        String coverImageUrl
) {
}
