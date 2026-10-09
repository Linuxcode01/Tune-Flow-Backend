package com.tuneflow.search.service;

import com.tuneflow.album.repository.AlbumRepository;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.common.config.CacheConfig;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.playlist.repository.PlaylistRepository;
import com.tuneflow.search.dto.SearchResponse;
import com.tuneflow.search.dto.SearchSuggestionResponse;
import com.tuneflow.storage.service.ArtworkUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Cached search facade. Search is the most cache-friendly read in the app: same query, same result. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SearchProvider searchProvider;
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final PlaylistRepository playlistRepository;
    private final ArtworkUrlResolver artworkUrlResolver;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_SEARCH, key = "#query.toLowerCase() + ':' + #type + ':' + #limit")
    public SearchResponse search(String query, String type, int limit) {
        return searchProvider.search(query, type, limit);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_SEARCH, key = "'suggest:' + #query.toLowerCase() + ':' + #limit")
    public SearchSuggestionResponse suggest(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return new SearchSuggestionResponse(normalized, List.of(), List.of());
        }
        int capped = Math.max(1, Math.min(limit, 15));
        PageRequest page = PageRequest.of(0, capped);

        List<SearchSuggestionResponse.Suggestion> items = new ArrayList<>();
        List<String> terms = new ArrayList<>();

        songRepository.search(normalized, page).forEach(song -> {
            items.add(new SearchSuggestionResponse.Suggestion(
                    "song",
                    song.getId().toString(),
                    song.getTitle(),
                    song.getArtist() == null ? null : song.getArtist().getName(),
                    artworkUrlResolver.resolve(song.getCoverImageUrl() != null
                            ? song.getCoverImageUrl()
                            : (song.getAlbum() == null ? null : song.getAlbum().getCoverImageUrl()))));
            terms.add(song.getTitle());
        });

        artistRepository.search(normalized, page).forEach(artist -> {
            items.add(new SearchSuggestionResponse.Suggestion(
                    "artist",
                    artist.getId().toString(),
                    artist.getName(),
                    "Artist",
                    artworkUrlResolver.resolve(artist.getProfileImageUrl())));
            terms.add(artist.getName());
        });

        albumRepository.search(normalized, page).forEach(album -> {
            items.add(new SearchSuggestionResponse.Suggestion(
                    "album",
                    album.getId().toString(),
                    album.getTitle(),
                    album.getArtist() == null ? "Album" : album.getArtist().getName(),
                    artworkUrlResolver.resolve(album.getCoverImageUrl())));
            terms.add(album.getTitle());
        });

        playlistRepository.searchPublic(normalized, page).forEach(playlist -> items.add(
                new SearchSuggestionResponse.Suggestion(
                        "playlist",
                        playlist.getId().toString(),
                        playlist.getName(),
                        "Playlist by " + playlist.getOwner().getName(),
                        artworkUrlResolver.resolve(playlist.getCoverImageUrl()))));

        List<String> uniqueTerms = terms.stream().distinct().limit(capped).toList();
        List<SearchSuggestionResponse.Suggestion> trimmed = items.stream().limit(capped).toList();
        return new SearchSuggestionResponse(normalized, uniqueTerms, trimmed);
    }
}
