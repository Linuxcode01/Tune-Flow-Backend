package com.tuneflow.music.service;

import com.tuneflow.common.response.PageResponse;
import com.tuneflow.music.dto.CreateSongRequest;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.music.dto.UpdateSongRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Song service contract (docs section 12). Controllers only validate HTTP input and map responses;
 * every business rule lives behind this interface.
 */
public interface SongService {

    SongResponse createSong(CreateSongRequest request);

    SongResponse getSong(UUID id);

    PageResponse<SongResponse> getSongs(UUID artistId, UUID albumId, String genreSlug, String query, Pageable pageable);

    SongResponse updateSong(UUID id, UpdateSongRequest request);

    void deleteSong(UUID id);

    PageResponse<SongResponse> getTrending(Pageable pageable);

    PageResponse<SongResponse> getNewReleases(Pageable pageable);

    void likeSong(UUID songId);

    void unlikeSong(UUID songId);

    PageResponse<SongResponse> getLikedSongs(Pageable pageable);

    List<SongResponse> getSongsByIds(List<UUID> ids);
}
