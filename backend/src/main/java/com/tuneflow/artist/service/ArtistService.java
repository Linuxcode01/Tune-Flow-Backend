package com.tuneflow.artist.service;

import com.tuneflow.artist.dto.ArtistResponse;
import com.tuneflow.artist.dto.UpdateArtistRequest;
import com.tuneflow.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ArtistService {

    ArtistResponse getArtist(UUID id);

    PageResponse<ArtistResponse> getArtists(String query, Pageable pageable);

    PageResponse<ArtistResponse> getPopularArtists(Pageable pageable);

    /** The artist profile owned by the authenticated user (null-safe: 404 when absent). */
    ArtistResponse getMyArtistProfile();

    ArtistResponse updateArtist(UUID id, UpdateArtistRequest request);

    ArtistResponse updateMyArtistProfile(UpdateArtistRequest request);

    void followArtist(UUID artistId);

    void unfollowArtist(UUID artistId);

    PageResponse<ArtistResponse> getFollowedArtists(Pageable pageable);

    void verifyArtist(UUID artistId, boolean verified);
}
