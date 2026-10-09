package com.tuneflow.playlist.service;

import com.tuneflow.common.response.PageResponse;
import com.tuneflow.playlist.dto.AddSongRequest;
import com.tuneflow.playlist.dto.CreatePlaylistRequest;
import com.tuneflow.playlist.dto.PlaylistDetailResponse;
import com.tuneflow.playlist.dto.PlaylistResponse;
import com.tuneflow.playlist.dto.ReorderPlaylistRequest;
import com.tuneflow.playlist.dto.UpdatePlaylistRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PlaylistService {

    PlaylistResponse createPlaylist(CreatePlaylistRequest request);

    PlaylistDetailResponse getPlaylist(UUID id);

    PageResponse<PlaylistResponse> getMyPlaylists(Pageable pageable);

    PageResponse<PlaylistResponse> searchPublicPlaylists(String query, Pageable pageable);

    PlaylistResponse updatePlaylist(UUID id, UpdatePlaylistRequest request);

    void deletePlaylist(UUID id);

    PlaylistDetailResponse addSong(UUID playlistId, AddSongRequest request);

    PlaylistDetailResponse removeSong(UUID playlistId, UUID songId);

    PlaylistDetailResponse reorder(UUID playlistId, ReorderPlaylistRequest request);

    PlaylistDetailResponse clear(UUID playlistId);
}
