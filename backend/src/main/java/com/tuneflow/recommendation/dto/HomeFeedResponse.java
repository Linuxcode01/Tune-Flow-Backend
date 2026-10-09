package com.tuneflow.recommendation.dto;

import com.tuneflow.artist.dto.ArtistResponse;
import com.tuneflow.music.dto.SongResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Home feed sections (docs sections 3, 20). One request populates the whole Home screen, which keeps
 * the mobile app's first paint to a single round trip.
 */
@Schema(name = "HomeFeedResponse")
public record HomeFeedResponse(
        List<Section> sections,
        @Schema(description = "True when recommendations are personalised for the current user")
        boolean personalised
) {

    @Schema(name = "HomeSection", description = "A titled rail of items")
    public record Section(
            String key,
            String title,
            String subtitle,
            String type,
            List<SongResponse> songs,
            List<ArtistResponse> artists
    ) {
        public static Section songs(String key, String title, String subtitle, List<SongResponse> songs) {
            return new Section(key, title, subtitle, "songs", songs, List.of());
        }

        public static Section artists(String key, String title, String subtitle, List<ArtistResponse> artists) {
            return new Section(key, title, subtitle, "artists", List.of(), artists);
        }
    }
}
