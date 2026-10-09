package com.tuneflow.artist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial artist profile update; null fields are left unchanged. */
@Schema(name = "UpdateArtistRequest")
public record UpdateArtistRequest(
        @Size(min = 2, max = 160, message = "Name must be between 2 and 160 characters")
        String name,

        @Size(max = 4000, message = "Bio is too long")
        String bio,

        @Size(max = 80, message = "Country must be at most 80 characters")
        String country,

        @Pattern(regexp = "^$|^(https?://).*", message = "Profile image URL must be an absolute http(s) URL")
        String profileImageUrl,

        @Schema(description = "objectKey returned by POST /uploads/image; takes precedence over profileImageUrl")
        String profileImageObjectKey
) {
}
