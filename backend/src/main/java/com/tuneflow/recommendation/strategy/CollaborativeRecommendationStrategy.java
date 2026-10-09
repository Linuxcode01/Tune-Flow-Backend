package com.tuneflow.recommendation.strategy;

import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.LikedSongRepository;
import com.tuneflow.music.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Version 2 - collaborative filtering (docs section 25): "similar users -> recommend songs they
 * enjoyed".
 *
 * <p>Implemented as an item-to-item co-like query: songs liked by other listeners who share at
 * least one like with this user, ranked by the number of such listeners, excluding songs the user
 * already has. It degrades silently to an empty list on cold start, which is exactly how a
 * collaborative signal should behave - the hybrid falls back to rule-based results.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborativeRecommendationStrategy implements RecommendationStrategy {

    private final LikedSongRepository likedSongRepository;
    private final SongRepository songRepository;

    @Override
    public String name() {
        return "collaborative";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public List<Song> recommend(UUID userId, int limit) {
        List<Object[]> rows = likedSongRepository.findCollaborativeSongIds(userId, PageRequest.of(0, limit));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> songIds = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            songIds.add((UUID) row[0]);
        }

        // Preserve the ranking returned by the query (the `in` clause does not guarantee order).
        var byId = new java.util.HashMap<UUID, Song>();
        songRepository.findAllByIdIn(songIds).forEach(song -> byId.put(song.getId(), song));

        List<Song> ranked = new ArrayList<>(songIds.size());
        for (UUID id : songIds) {
            Song song = byId.get(id);
            if (song != null && song.isActive()) {
                ranked.add(song);
            }
        }
        log.debug("Collaborative strategy produced {} candidate(s) for user {}", ranked.size(), userId);
        return ranked;
    }
}
