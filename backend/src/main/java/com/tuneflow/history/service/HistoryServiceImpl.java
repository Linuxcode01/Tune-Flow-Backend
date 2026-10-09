package com.tuneflow.history.service;

import com.tuneflow.common.config.CacheConfig;
import com.tuneflow.common.event.DomainEvent;
import com.tuneflow.common.event.EventPublisher;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.history.dto.HistoryEntryResponse;
import com.tuneflow.history.dto.HistoryStatsResponse;
import com.tuneflow.history.dto.RecordPlaybackRequest;
import com.tuneflow.history.entity.ListeningHistory;
import com.tuneflow.history.repository.ListeningHistoryRepository;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.music.service.SongMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Playback history (docs sections 3, 10, 23).
 *
 * <p>The write is synchronous because users expect "Recently played" to update immediately, but it
 * is a single cheap insert. Everything derived from it (play counters, analytics, recommendation
 * signals) is dispatched asynchronously through the event publisher, so the request path stays
 * short (docs section 39).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    /** A listen counts as "completed" past this share of the track duration. */
    private static final double COMPLETION_THRESHOLD = 0.9;

    /** Window for the listening chart on the client's Library tab. */
    private static final int DAYS_IN_WINDOW = 7;

    private final ListeningHistoryRepository historyRepository;
    private final SongRepository songRepository;
    private final com.tuneflow.user.repository.UserRepository userRepository;
    private final SongMapper songMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public HistoryEntryResponse recordPlayback(RecordPlaybackRequest request) {
        UUID userId = SecurityUtils.requireUserId();
        Song song = songRepository.findById(request.songId())
                .filter(Song::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));

        double ratio = song.getDurationSeconds() > 0
                ? Math.min(1.0, (double) request.playedSeconds() / song.getDurationSeconds())
                : 0.0;
        boolean completed = request.completed() || ratio >= COMPLETION_THRESHOLD;

        ListeningHistory entry = historyRepository.save(ListeningHistory.builder()
                .user(userRepository.getReferenceById(userId))
                .song(song)
                .startedAt(Instant.now().minusSeconds(Math.max(0, request.playedSeconds())))
                .completedAt(completed ? Instant.now() : null)
                .playedSeconds(request.playedSeconds())
                .completionRatio(ratio)
                .deviceId(request.deviceId())
                .offlinePlayback(request.offline())
                .build());

        eventPublisher.publish(DomainEvent.builder(completed ? DomainEvent.SONG_COMPLETED : DomainEvent.SONG_PLAYED)
                .user(userId)
                .song(song.getId())
                .artist(song.getArtist() == null ? null : song.getArtist().getId())
                .payload(Map.of(
                        "playedSeconds", request.playedSeconds(),
                        "completed", completed,
                        "finalUpdate", request.finalUpdate(),
                        "deviceId", request.deviceId() == null ? "" : request.deviceId()))
                .build());

        return new HistoryEntryResponse(
                entry.getId(),
                songMapper.toResponse(song, null),
                entry.getStartedAt(),
                entry.getCompletedAt(),
                entry.getPlayedSeconds(),
                entry.getCompletionRatio());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HistoryEntryResponse> getHistory(Pageable pageable) {
        UUID userId = SecurityUtils.requireUserId();
        Page<ListeningHistory> page = historyRepository.findByUserWithSong(userId, pageable);
        return PageResponse.from(page, entry -> new HistoryEntryResponse(
                entry.getId(),
                songMapper.toResponse(entry.getSong(), null),
                entry.getStartedAt(),
                entry.getCompletedAt(),
                entry.getPlayedSeconds(),
                entry.getCompletionRatio()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SongResponse> getRecentlyPlayed(int limit) {
        UUID userId = SecurityUtils.requireUserId();
        int capped = Math.max(1, Math.min(limit, 50));
        // Over-fetch then de-duplicate by song so repeated listens do not fill the rail.
        List<ListeningHistory> recent = historyRepository.findRecent(userId, PageRequest.of(0, capped * 4));

        Map<UUID, Song> distinct = new LinkedHashMap<>();
        for (ListeningHistory entry : recent) {
            distinct.putIfAbsent(entry.getSong().getId(), entry.getSong());
            if (distinct.size() >= capped) {
                break;
            }
        }
        return distinct.values().stream()
                .map(song -> songMapper.toResponse(song, Boolean.TRUE))
                .toList();
    }

    @Override
    @Transactional
    public void clearHistory() {
        UUID userId = SecurityUtils.requireUserId();
        historyRepository.findRecent(userId, PageRequest.of(0, 500))
                .forEach(historyRepository::delete);
        log.info("Cleared listening history for user {}", userId);
    }

    /**
     * Aggregated listening statistics (docs section 13). Deliberately assembled from a handful of
     * indexed aggregate queries rather than loading history rows into memory: the client only needs
     * the totals plus the top five artists/genres.
     */
    @Override
    @Transactional(readOnly = true)
    public HistoryStatsResponse getStats() {
        UUID userId = SecurityUtils.requireUserId();

        long totalPlays = historyRepository.countByUser_Id(userId);
        long totalSeconds = historyRepository.sumPlayedSeconds(userId);
        long completedPlays = historyRepository.countCompletedPlays(userId);
        long uniqueSongs = historyRepository.countDistinctSongs(userId);
        long uniqueArtists = historyRepository.findListenedArtistIds(userId).size();

        int topLimit = 5;
        PageRequest topRequest = PageRequest.of(0, topLimit);

        List<HistoryStatsResponse.TopArtist> topArtists = historyRepository
                .findTopArtists(userId, topRequest).stream()
                .map(row -> new HistoryStatsResponse.TopArtist(
                        (UUID) row[0], (String) row[1], (String) row[2], ((Number) row[3]).longValue()))
                .toList();

        List<HistoryStatsResponse.TopGenre> topGenres = historyRepository
                .findTopGenres(userId, topRequest).stream()
                .map(row -> new HistoryStatsResponse.TopGenre(
                        (UUID) row[0], (String) row[1], (String) row[2], ((Number) row[3]).longValue()))
                .toList();

        return new HistoryStatsResponse(totalPlays, totalSeconds, completedPlays, uniqueSongs,
                uniqueArtists, topArtists, topGenres, lastSevenDays(userId));
    }

    /**
     * Buckets the trailing week in the service rather than with a database date function, so the
     * same code works on PostgreSQL and on the H2 profile used for local development and CI.
     * Days with no listening are returned as zeroes, which keeps the chart's x-axis stable.
     */
    private List<HistoryStatsResponse.DailyPlays> lastSevenDays(UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant since = today.minusDays(DAYS_IN_WINDOW - 1L)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        Map<LocalDate, Long> playsByDay = historyRepository.findStartedAtSince(userId, since).stream()
                .collect(Collectors.groupingBy(
                        startedAt -> startedAt.atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()));

        List<HistoryStatsResponse.DailyPlays> days = new ArrayList<>(DAYS_IN_WINDOW);
        for (int offset = DAYS_IN_WINDOW - 1; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            days.add(new HistoryStatsResponse.DailyPlays(date, playsByDay.getOrDefault(date, 0L)));
        }
        return days;
    }

    /**
     * Counter update driven by the analytics consumer. The cached song payload embeds
     * {@code playCount}, so the song entry is evicted to avoid serving a stale counter.
     * Trending/home rails are intentionally left to their short TTLs: evicting them on every play
     * would destroy the cache's value for essentially no freshness gain.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_SONGS, key = "#songId")
    public void incrementPlayCount(UUID songId) {
        songRepository.incrementPlayCount(songId);
    }
}
