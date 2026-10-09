package com.tuneflow.playlist.dto;

import com.tuneflow.music.dto.SongResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Playlist with its ordered track list (docs section 20: cover, title, owner, ordered tracks). */
@Schema(name = "PlaylistDetailResponse")
public record PlaylistDetailResponse(
        PlaylistResponse playlist,
        List<SongResponse> songs
) {
}
