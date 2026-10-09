package com.tuneflow.recommendation.service;

import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.recommendation.dto.HomeFeedResponse;

import java.util.List;
import java.util.UUID;

public interface RecommendationService {

    /** Personalised recommendations for the signed-in user (docs sections 13, 25). */
    List<SongResponse> getPersonalized(int limit);

    /** "More like this" for the currently playing song. */
    List<SongResponse> getSimilar(UUID songId, int limit);

    /** The full Home screen: personalised rails plus cached catalog rails. */
    HomeFeedResponse getHomeFeed();

    /** Name of the strategy currently answering requests - surfaced for observability. */
    String activeStrategy();
}
