package com.tuneflow.recommendation.service;

import com.tuneflow.artist.dto.ArtistResponse;
import com.tuneflow.artist.entity.Artist;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.artist.repository.FollowedArtistRepository;
import com.tuneflow.common.config.CacheConfig;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.history.service.HistoryService;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.music.entity.Genre;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.music.service.SongMapper;
import com.tuneflow.recommendation.dto.HomeFeedResponse;
import com.tuneflow.recommendation.strategy.RecommendationStrategy;
import com.tuneflow.storage.service.ArtworkUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Personalisation facade (docs sections 3, 25).
 *
 * <p>Caching note: recommendation results contain the per-user {@code liked} flag, so the cache key
 * includes the user id - a shared cache entry must never be served across users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private static final int MAX_LIMIT = 50;
    private static final int HOME_RAIL_SIZE = 20;

    private final RecommendationStrategy recommendationStrategy;
    private final com.tuneflow.music.repository.LikedSongRepository likedSongRepository;
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final FollowedArtistRepository followedArtistRepository;
    private final HistoryService historyService;
    private final SongMapper songMapper;
    private final ArtworkUrlResolver artworkUrlResolver;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_RECOMMENDATIONS,
            key = "'for:' + #root.target.currentUserIdOrGuest() + ':' + #limit")
    public List<SongResponse> getPersonalized(int limit) {
        UUID userId = SecurityUtils.currentUser().map(u -> u.id()).orElse(null);
        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));

        if (userId == null) {
            // Anonymous listeners still get a discovery feed, just not a personalised one.
            return songRepository.findTrending(PageRequest.of(0, capped)).getContent().stream()
                    .map(song -> songMapper.toResponse(song, null))
                    .toList();
        }

        List<Song> songs = recommendationStrategy.recommend(userId, capped);
        Set<UUID> likedIds = likedIds(userId, songs);
        return songs.stream()
                .map(song -> songMapper.toResponse(song, likedIds.contains(song.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_RECOMMENDATIONS,
            key = "'similar:' + #root.target.currentUserIdOrGuest() + ':' + #songId + ':' + #limit")
    public List<SongResponse> getSimilar(UUID songId, int limit) {
        Song song = songRepository.findByIdWithDetails(songId)
                .filter(Song::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));

        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
        Set<UUID> genreIds = new HashSet<>();
        for (Genre genre : song.getGenres()) {
            genreIds.add(genre.getId());
        }

        List<Song> candidates = genreIds.isEmpty()
                ? songRepository.findTrending(PageRequest.of(0, capped)).getContent()
                : songRepository.findSimilarByGenreIds(genreIds, song.getId(), PageRequest.of(0, capped)).getContent();

        UUID userId = SecurityUtils.currentUser().map(u -> u.id()).orElse(null);
        Set<UUID> likedIds = userId == null ? Set.of() : likedIds(userId, candidates);
        return candidates.stream()
                .map(candidate -> songMapper.toResponse(candidate,
                        userId == null ? null : likedIds.contains(candidate.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HomeFeedResponse getHomeFeed() {
        UUID userId = SecurityUtils.currentUser().map(u -> u.id()).orElse(null);
        List<HomeFeedResponse.Section> sections = new ArrayList<>();

        PageRequest rail = PageRequest.of(0, HOME_RAIL_SIZE);

        if (userId != null) {
            List<SongResponse> recentlyPlayed = historyService.getRecentlyPlayed(10);
            if (!recentlyPlayed.isEmpty()) {
                sections.add(HomeFeedResponse.Section.songs("recently-played", "Recently played",
                        "Pick up where you left off", recentlyPlayed));
            }
        }

        List<SongResponse> madeForYou = getPersonalized(HOME_RAIL_SIZE);
        if (!madeForYou.isEmpty()) {
            sections.add(HomeFeedResponse.Section.songs("made-for-you",
                    userId == null ? "Popular right now" : "Made for you",
                    userId == null ? "Trending on TuneFlow" : "Based on what you like",
                    madeForYou));
        }

        // "Because you listened to X" - seeded from the most recent history entry.
        if (userId != null) {
            List<SongResponse> recent = historyService.getRecentlyPlayed(1);
            if (!recent.isEmpty()) {
                SongResponse seed = recent.get(0);
                List<SongResponse> similar = getSimilar(seed.id(), 12);
                if (!similar.isEmpty()) {
                    sections.add(HomeFeedResponse.Section.songs("because-you-listened",
                            "Because you listened to " + seed.title(), seed.artist() == null ? null : seed.artist().name(),
                            similar));
                }
            }
        }

        sections.add(HomeFeedResponse.Section.songs("trending", "Trending now",
                "What everyone is playing",
                songRepository.findTrending(rail).getContent().stream()
                        .map(song -> songMapper.toResponse(song, null))
                        .toList()));

        if (userId != null) {
            List<ArtistResponse> followed = followedArtistRepository.findById_UserId(userId).stream()
                    .limit(10)
                    .map(follow -> toArtistResponse(follow.getArtist()))
                    .toList();
            if (!followed.isEmpty()) {
                sections.add(HomeFeedResponse.Section.artists("followed-artists", "Artists you follow",
                        "New music from your artists", followed));
            }
        }

        sections.add(HomeFeedResponse.Section.songs("new-releases", "New releases",
                "Freshly added to the catalog",
                songRepository.findNewReleases(rail).getContent().stream()
                        .map(song -> songMapper.toResponse(song, null))
                        .toList()));

        List<ArtistResponse> popularArtists = artistRepository.findPopular(PageRequest.of(0, 12)).getContent().stream()
                .map(this::toArtistResponse)
                .toList();
        sections.add(HomeFeedResponse.Section.artists("popular-artists", "Popular artists",
                "Trending creators", popularArtists));

        return new HomeFeedResponse(sections, userId != null);
    }

    @Override
    public String activeStrategy() {
        return recommendationStrategy.name();
    }

    // ------------------------------------------------------------------------------------ helpers

    /** Referenced from the cache SpEL expression above. */
    public String currentUserIdOrGuest() {
        return SecurityUtils.currentUser().map(user -> user.id().toString()).orElse("guest");
    }

    /** One query for the whole candidate list instead of N. */
    private Set<UUID> likedIds(UUID userId, List<Song> songs) {
        if (songs.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = songs.stream().map(Song::getId).toList();
        return new HashSet<>(likedSongRepository.findLikedSongIds(userId, ids));
    }

    private ArtistResponse toArtistResponse(Artist artist) {
        return new ArtistResponse(
                artist.getId(),
                artist.getName(),
                artist.getBio(),
                artworkUrlResolver.resolve(artist.getProfileImageUrl()),
                artist.getCountry(),
                artist.isVerified(),
                artist.getFollowerCount(),
                songRepository.countByArtist_Id(artist.getId()),
                0,
                artist.getUser() == null ? null : artist.getUser().getId(),
                null,
                artist.getCreatedAt());
    }
}
