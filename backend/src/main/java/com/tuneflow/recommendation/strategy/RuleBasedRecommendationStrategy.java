package com.tuneflow.recommendation.strategy;

import com.tuneflow.music.entity.Genre;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.LikedSongRepository;
import com.tuneflow.music.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Version 1 - deterministic, rule-based recommendations (docs section 25).
 *
 * <p>Rule: {@code user likes Rock -> recommend popular Rock songs}. Concretely, the genres of the
 * user's recently liked tracks become the affinity profile; popular songs in those genres are the
 * candidates, and trending tracks fill any remaining slots. Cold-start users (no likes yet) get
 * trending music.
 *
 * <p>Being deterministic and dependency-free, this is the strategy that must always work - it is
 * the fallback every richer strategy degrades to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleBasedRecommendationStrategy implements RecommendationStrategy {

    private static final int AFFINITY_SAMPLE = 50;

    private final LikedSongRepository likedSongRepository;
    private final SongRepository songRepository;

    @Override
    public String name() {
        return "rule-based";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public List<Song> recommend(UUID userId, int limit) {
        List<UUID> likedIds = likedSongRepository.findRecentLikedSongIds(userId, PageRequest.of(0, AFFINITY_SAMPLE));

        if (likedIds.isEmpty()) {
            log.debug("Cold-start user {}: falling back to trending", userId);
            // Trim defensively: the strategy contract is "at most `limit` songs" regardless of how a
            // repository implementation honours the Pageable.
            return songRepository.findTrending(PageRequest.of(0, limit)).getContent().stream()
                    .limit(limit)
                    .toList();
        }

        Set<String> affinityGenres = new LinkedHashSet<>();
        for (Song song : songRepository.findAllByIdIn(likedIds)) {
            for (Genre genre : song.getGenres()) {
                affinityGenres.add(genre.getSlug());
            }
        }

        Map<UUID, Song> candidates = new LinkedHashMap<>();
        if (!affinityGenres.isEmpty()) {
            songRepository.findPopularByGenreSlugs(affinityGenres, PageRequest.of(0, limit * 3))
                    .forEach(song -> {
                        if (!likedIds.contains(song.getId())) {
                            candidates.putIfAbsent(song.getId(), song);
                        }
                    });
        }

        // Fill the tail with trending tracks so the rail is never half-empty.
        if (candidates.size() < limit) {
            songRepository.findTrending(PageRequest.of(0, limit * 2)).forEach(song -> {
                if (candidates.size() < limit && !likedIds.contains(song.getId())) {
                    candidates.putIfAbsent(song.getId(), song);
                }
            });
        }

        return candidates.values().stream().limit(limit).toList();
    }
}
