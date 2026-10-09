package com.tuneflow.recommendation.strategy;

import com.tuneflow.music.entity.Song;

import java.util.List;
import java.util.UUID;

/**
 * Pluggable recommendation algorithm (docs section 25). Implementations are swapped behind this
 * interface - the Strategy pattern keeps the API layer unaware of which algorithm is active.
 */
public interface RecommendationStrategy {

    /** Stable identifier, also reported in logs/metrics so algorithms can be compared offline. */
    String name();

    /**
     * @param userId user to personalise for; never null
     * @param limit  maximum number of songs to return
     * @return candidate songs, best first
     */
    List<Song> recommend(UUID userId, int limit);

    /** Order in the hybrid composition; lower runs first. */
    default int priority() {
        return 100;
    }
}
