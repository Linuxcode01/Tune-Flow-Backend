package com.tuneflow.album.service;

import com.tuneflow.album.dto.AlbumResponse;
import com.tuneflow.album.dto.CreateAlbumRequest;
import com.tuneflow.album.dto.UpdateAlbumRequest;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.music.dto.SongResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AlbumService {

    AlbumResponse createAlbum(CreateAlbumRequest request);

    AlbumResponse getAlbum(UUID id);

    PageResponse<AlbumResponse> getAlbums(UUID artistId, String query, Pageable pageable);

    PageResponse<AlbumResponse> getNewReleases(Pageable pageable);

    PageResponse<SongResponse> getAlbumSongs(UUID albumId, Pageable pageable);

    AlbumResponse updateAlbum(UUID id, UpdateAlbumRequest request);

    void deleteAlbum(UUID id);
}
