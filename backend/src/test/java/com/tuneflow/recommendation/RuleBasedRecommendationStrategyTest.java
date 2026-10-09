package com.tuneflow.recommendation;

import com.tuneflow.music.entity.Genre;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.LikedSongRepository;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.recommendation.strategy.RuleBasedRecommendationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * Recommendation logic is the most test-heavy service in the docs' testing strategy (section 31):
 * it must be deterministic, and it must never return a song the user already liked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RuleBasedRecommendationStrategyTest {

    @Mock
    private LikedSongRepository likedSongRepository;

    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private RuleBasedRecommendationStrategy strategy;

    @Test
    @DisplayName("cold-start user without likes receives trending music")
    void coldStartFallsBackToTrending() {
        UUID userId = UUID.randomUUID();
        when(likedSongRepository.findRecentLikedSongIds(any(UUID.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(songRepository.findTrending(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(song("Trending One"), song("Trending Two"))));

        List<Song> result = strategy.recommend(userId, 5);

        assertThat(result).extracting(Song::getTitle).containsExactly("Trending One", "Trending Two");
    }

    @Test
    @DisplayName("likes drive genre affinity and already-liked songs are excluded")
    void genreAffinityExcludesLikedSongs() {
        UUID userId = UUID.randomUUID();
        Song liked = song("Liked Rock Track", "rock");
        Song candidate = song("Similar Rock Track", "rock");

        when(likedSongRepository.findRecentLikedSongIds(any(UUID.class), any(Pageable.class)))
                .thenReturn(List.of(liked.getId()));
        when(songRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(liked));
        when(songRepository.findPopularByGenreSlugs(anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(liked, candidate)));
        when(songRepository.findTrending(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<Song> result = strategy.recommend(userId, 3);

        assertThat(result).extracting(Song::getTitle)
                .containsExactly("Similar Rock Track")
                .doesNotContain("Liked Rock Track");
    }

    @Test
    @DisplayName("respects the requested limit")
    void respectsLimit() {
        when(likedSongRepository.findRecentLikedSongIds(any(UUID.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(songRepository.findTrending(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(song("A"), song("B"), song("C"))));

        assertThat(strategy.recommend(UUID.randomUUID(), 2)).hasSize(2);
    }

    private Song song(String title, String... genreSlugs) {
        LinkedHashSet<Genre> genres = new LinkedHashSet<>();
        for (String slug : genreSlugs) {
            genres.add(Genre.builder().name(slug).slug(slug).build());
        }
        Song song = Song.builder()
                .title(title)
                .artist(null)
                .durationSeconds(200)
                .genres(genres)
                .active(true)
                .build();
        song.setId(UUID.randomUUID());
        return song;
    }
}
