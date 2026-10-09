package com.tuneflow.recommendation.strategy;

import com.tuneflow.music.entity.Song;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Version 3 - hybrid ranking (docs section 25): "genre + history + likes + collaborative signals ->
 * ranking engine -> personalized feed".
 *
 * <p>Composes every registered {@link RecommendationStrategy} by priority and blends their outputs
 * round-robin, so a user with strong collaborative signal gets social picks first while a cold-start
 * user still gets sensible rule-based content. This is the strategy the API uses by default
 * ({@code @Primary}), and adding a new signal later means adding one {@code @Component} here - no
 * controller or service changes.
 */
@Slf4j
@Component
@Primary
public class HybridRecommendationStrategy implements RecommendationStrategy {

    private final List<RecommendationStrategy> delegates;

    public HybridRecommendationStrategy(List<RecommendationStrategy> strategies) {
        // Spring injects every strategy bean; exclude this composer to avoid recursion.
        this.delegates = strategies.stream()
                .filter(strategy -> !(strategy instanceof HybridRecommendationStrategy))
                .sorted(java.util.Comparator.comparingInt(RecommendationStrategy::priority))
                .toList();
        log.info("Hybrid recommendation engine composed of: {}",
                delegates.stream().map(RecommendationStrategy::name).toList());
    }

    @Override
    public String name() {
        return "hybrid";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public List<Song> recommend(UUID userId, int limit) {
        List<List<Song>> candidateLists = new ArrayList<>();
        for (RecommendationStrategy delegate : delegates) {
            try {
                List<Song> candidates = delegate.recommend(userId, limit);
                if (!candidates.isEmpty()) {
                    candidateLists.add(candidates);
                }
            } catch (RuntimeException ex) {
                // One failing signal must never break the personalised feed.
                log.warn("Recommendation strategy {} failed: {}", delegate.name(), ex.getMessage());
            }
        }

        Map<UUID, Song> blended = new LinkedHashMap<>();
        int index = 0;
        while (blended.size() < limit && !candidateLists.isEmpty()) {
            boolean progressed = false;
            for (List<Song> candidates : candidateLists) {
                if (index < candidates.size() && blended.size() < limit) {
                    Song song = candidates.get(index);
                    blended.putIfAbsent(song.getId(), song);
                    progressed = true;
                }
            }
            if (!progressed) {
                break;
            }
            index++;
        }
        return new ArrayList<>(blended.values());
    }
}
