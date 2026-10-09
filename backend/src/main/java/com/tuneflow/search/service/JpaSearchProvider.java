package com.tuneflow.search.service;

import com.tuneflow.album.repository.AlbumRepository;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.music.service.SongMapper;
import com.tuneflow.playlist.repository.PlaylistRepository;
import com.tuneflow.search.dto.SearchResponse;
import com.tuneflow.storage.service.ArtworkUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * PostgreSQL/JPQL search for the MVP (docs section 24: "PostgreSQL can power the MVP").
 *
 * <p>Two passes: an exact {@code LIKE %query%} pass, and - only when that returns nothing - a
 * tokenized pass that matches any individual word, which gives useful tolerance for long or
 * slightly-off queries. Results flag {@code fuzzyMatched} so the UI can say "showing results for…".
 *
 * <p>Per-user flags (liked/following) are deliberately omitted so results are identical for every
 * caller and therefore cacheable.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "tuneflow.search.provider", havingValue = "jpa", matchIfMissing = true)
@RequiredArgsConstructor
public class JpaSearchProvider implements SearchProvider {

    private static final int MAX_LIMIT = 50;
    private static final int MIN_TOKEN_LENGTH = 3;

    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final PlaylistRepository playlistRepository;
    private final SongMapper songMapper;
    private final ArtworkUrlResolver artworkUrlResolver;

    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(String query, String type, int limit) {
        long started = System.nanoTime();
        String normalized = query == null ? "" : query.trim();
        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
        String entityType = type == null || type.isBlank() ? "all" : type.trim().toLowerCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            return SearchResponse.empty(normalized, entityType, elapsedMs(started));
        }

        SearchResponse exact = runPass(normalized, entityType, capped, false, started);
        if (exact.totalResults() > 0) {
            return exact;
        }

        /*
         * Second pass: match the leading token instead of the whole phrase, which absorbs typos in
         * the remaining words ("neon skylne" -> "neon"). `fuzzyMatched` is only reported when this
         * pass actually finds something - claiming a fuzzy match for a query that matched nothing
         * would mislead the client into showing "results for …" with an empty list.
         */
        List<String> tokens = tokens(normalized);
        if (tokens.isEmpty()) {
            return exact;
        }
        SearchResponse fallback = runPass(normalized, entityType, capped, true, started);
        if (fallback.totalResults() == 0) {
            return exact;
        }
        log.debug("Fuzzy fallback for '{}' using token '{}'", normalized, primaryToken(normalized));
        return fallback;
    }

    private SearchResponse runPass(String query, String type, int limit, boolean fuzzy, long started) {
        PageRequest page = PageRequest.of(0, limit);
        boolean all = "all".equals(type);

        List<com.tuneflow.music.dto.SongResponse> songs = List.of();
        List<com.tuneflow.artist.dto.ArtistResponse> artists = List.of();
        List<com.tuneflow.album.dto.AlbumResponse> albums = List.of();
        List<com.tuneflow.playlist.dto.PlaylistResponse> playlists = List.of();

        String term = fuzzy ? primaryToken(query) : query;

        if (all || "songs".equals(type) || "song".equals(type)) {
            songs = songRepository.search(term, page).getContent().stream()
                    .map(song -> songMapper.toResponse(song, null))
                    .toList();
        }
        if (all || "artists".equals(type) || "artist".equals(type)) {
            artists = artistRepository.search(term, page).getContent().stream()
                    .map(artist -> new com.tuneflow.artist.dto.ArtistResponse(
                            artist.getId(),
                            artist.getName(),
                            artist.getBio(),
                            artworkUrlResolver.resolve(artist.getProfileImageUrl()),
                            artist.getCountry(),
                            artist.isVerified(),
                            artist.getFollowerCount(),
                            0, 0,
                            artist.getUser() == null ? null : artist.getUser().getId(),
                            null,
                            artist.getCreatedAt()))
                    .toList();
        }
        if (all || "albums".equals(type) || "album".equals(type)) {
            albums = albumRepository.search(term, page).getContent().stream()
                    .map(album -> new com.tuneflow.album.dto.AlbumResponse(
                            album.getId(),
                            album.getTitle(),
                            album.getDescription(),
                            artworkUrlResolver.resolve(album.getCoverImageUrl()),
                            album.getReleaseDate(),
                            album.getSongCount(),
                            0,
                            album.getArtist() == null ? null : new com.tuneflow.music.dto.ArtistSummary(
                                    album.getArtist().getId(),
                                    album.getArtist().getName(),
                                    artworkUrlResolver.resolve(album.getArtist().getProfileImageUrl()),
                                    album.getArtist().isVerified()),
                            null))
                    .toList();
        }
        if (all || "playlists".equals(type) || "playlist".equals(type)) {
            playlists = playlistRepository.searchPublic(term, page).getContent().stream()
                    .map(playlist -> new com.tuneflow.playlist.dto.PlaylistResponse(
                            playlist.getId(),
                            playlist.getName(),
                            playlist.getDescription(),
                            artworkUrlResolver.resolve(playlist.getCoverImageUrl()),
                            playlist.isPublicVisible(),
                            playlist.isSystemPlaylist(),
                            playlist.getSongCount(),
                            playlist.getTotalDurationSeconds(),
                            playlist.getOwner().getId(),
                            playlist.getOwner().getName(),
                            false,
                            playlist.getCreatedAt(),
                            playlist.getUpdatedAt()))
                    .toList();
        }

        long total = songs.size() + artists.size() + albums.size() + playlists.size();
        return new SearchResponse(query, type, songs, artists, albums, playlists, total,
                elapsedMs(started), fuzzy);
    }

    /**
     * Splits on any run of non-alphanumeric characters, so "lo-fi", "hip hop" and "neon+skylne" all
     * tokenize sensibly regardless of how the client encoded the query.
     */
    private static List<String> tokens(String query) {
        return java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .map(token -> token.replaceAll("[^\\p{L}\\p{N}]", ""))
                .filter(token -> token.length() >= MIN_TOKEN_LENGTH)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private static String primaryToken(String query) {
        List<String> tokens = tokens(query);
        return tokens.isEmpty() ? query : tokens.get(0);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    @Override
    public String name() {
        return "jpa";
    }
}
