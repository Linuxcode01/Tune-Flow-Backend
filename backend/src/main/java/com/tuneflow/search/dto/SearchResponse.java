package com.tuneflow.search.dto;

import com.tuneflow.album.dto.AlbumResponse;
import com.tuneflow.artist.dto.ArtistResponse;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.playlist.dto.PlaylistResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Grouped search results (docs section 24). One round trip returns every entity type. */
@Schema(name = "SearchResponse")
public record SearchResponse(
        String query,
        String type,
        List<SongResponse> songs,
        List<ArtistResponse> artists,
        List<AlbumResponse> albums,
        List<PlaylistResponse> playlists,
        long totalResults,
        long tookMs,
        @Schema(description = "True when results came from the tokenized fallback pass rather than an exact match")
        boolean fuzzyMatched
) {

    public static SearchResponse empty(String query, String type, long tookMs) {
        return new SearchResponse(query, type, List.of(), List.of(), List.of(), List.of(), 0, tookMs, false);
    }
}
