package com.tuneflow.music.service;

import com.tuneflow.album.entity.Album;
import com.tuneflow.album.repository.AlbumRepository;
import com.tuneflow.artist.entity.Artist;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.common.config.CacheConfig;
import com.tuneflow.common.event.DomainEvent;
import com.tuneflow.common.event.EventPublisher;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.music.dto.CreateSongRequest;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.music.dto.UpdateSongRequest;
import com.tuneflow.music.entity.Genre;
import com.tuneflow.music.entity.LikedSong;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.GenreRepository;
import com.tuneflow.music.repository.LikedSongRepository;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Catalog business rules (docs sections 3, 12-13).
 *
 * <ul>
 *   <li>Hot, low-volatility catalog reads are cached (docs section 22).</li>
 *   <li>Mutations evict the affected caches so trending/home rails stay fresh.</li>
 *   <li>Likes are written transactionally and reported to Kafka asynchronously for the
 *       analytics/recommendation consumers (docs section 23).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceImpl implements SongService {

    private static final String EVENT_SONG_CREATED = "song.created";

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final LikedSongRepository likedSongRepository;
    private final UserRepository userRepository;
    private final SongMapper songMapper;
    private final EventPublisher eventPublisher;

    // ------------------------------------------------------------------ create / update / delete

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_SONGS, CacheConfig.CACHE_TRENDING,
            CacheConfig.CACHE_HOME_FEED, CacheConfig.CACHE_ALBUMS}, allEntries = true)
    public SongResponse createSong(CreateSongRequest request) {
        Artist artist = resolveTargetArtist(request.artistId());
        Album album = resolveAlbum(request.albumId(), artist, true);

        Song song = Song.builder()
                .title(request.title().trim())
                .artist(artist)
                .album(album)
                .durationSeconds(request.durationSeconds())
                .trackNumber(request.trackNumber())
                .releaseDate(request.releaseDate())
                .explicit(request.explicit())
                .audioUrl(request.audioObjectKey())
                .coverImageUrl(request.coverImageObjectKey())
                .genres(resolveGenres(request.genres()))
                .build();

        song = songRepository.save(song);
        if (album != null) {
            albumRepository.refreshSongCount(album.getId());
        }

        eventPublisher.publish(DomainEvent.builder(EVENT_SONG_CREATED)
                .song(song.getId())
                .artist(artist.getId())
                .user(SecurityUtils.currentUser().map(u -> u.id()).orElse(null))
                .payload(album == null ? Map.of() : Map.of("albumId", album.getId().toString()))
                .build());

        log.info("Created song {} by artist {}", song.getId(), artist.getId());
        return songMapper.toResponse(song, Boolean.FALSE);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_SONGS, key = "#id")
    public SongResponse getSong(UUID id) {
        Song song = songRepository.findByIdWithDetails(id)
                .filter(Song::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));
        return songMapper.toResponse(song, likedFlag(song.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SongResponse> getSongs(UUID artistId, UUID albumId, String genreSlug, String query, Pageable pageable) {
        Page<Song> page;
        if (StringUtils.hasText(query)) {
            page = songRepository.search(query.trim(), pageable);
        } else if (albumId != null) {
            page = songRepository.findByAlbum_IdAndActiveTrue(albumId, pageable);
        } else if (artistId != null) {
            page = songRepository.findByArtist_IdAndActiveTrue(artistId, pageable);
        } else if (StringUtils.hasText(genreSlug)) {
            page = songRepository.findPopularByGenreSlugs(List.of(genreSlug), pageable);
        } else {
            page = songRepository.findByActiveTrue(pageable);
        }
        return toPageResponse(page);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_SONGS, CacheConfig.CACHE_TRENDING,
            CacheConfig.CACHE_HOME_FEED, CacheConfig.CACHE_ALBUMS}, allEntries = true)
    public SongResponse updateSong(UUID id, UpdateSongRequest request) {
        Song song = loadActiveSong(id);
        assertCanManage(song);

        if (request.title() != null) {
            if (!StringUtils.hasText(request.title())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Title cannot be blank");
            }
            song.setTitle(request.title().trim());
        }
        if (request.durationSeconds() != null) {
            song.setDurationSeconds(request.durationSeconds());
        }
        if (request.trackNumber() != null) {
            song.setTrackNumber(request.trackNumber());
        }
        if (request.releaseDate() != null) {
            song.setReleaseDate(request.releaseDate());
        }
        if (request.explicit() != null) {
            song.setExplicit(request.explicit());
        }
        if (request.audioObjectKey() != null) {
            song.setAudioUrl(StringUtils.hasText(request.audioObjectKey()) ? request.audioObjectKey() : null);
        }
        if (request.coverImageObjectKey() != null) {
            song.setCoverImageUrl(StringUtils.hasText(request.coverImageObjectKey())
                    ? request.coverImageObjectKey() : null);
        }
        if (request.genres() != null) {
            song.setGenres(resolveGenres(request.genres()));
        }
        if (request.albumId() != null) {
            Album previous = song.getAlbum();
            Album album = resolveAlbum(request.albumId(), song.getArtist(), false);
            song.setAlbum(album);
            if (previous != null) {
                albumRepository.refreshSongCount(previous.getId());
            }
            if (album != null) {
                albumRepository.refreshSongCount(album.getId());
            }
        }

        return songMapper.toResponse(songRepository.save(song), likedFlag(song.getId()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_SONGS, CacheConfig.CACHE_TRENDING,
            CacheConfig.CACHE_HOME_FEED, CacheConfig.CACHE_ALBUMS}, allEntries = true)
    public void deleteSong(UUID id) {
        Song song = loadActiveSong(id);
        assertCanManage(song);
        // Soft delete: listening history, playlists and play counts stay intact.
        song.setActive(false);
        songRepository.save(song);
        if (song.getAlbum() != null) {
            albumRepository.refreshSongCount(song.getAlbum().getId());
        }
        log.info("Soft-deleted song {}", id);
    }

    // ---------------------------------------------------------------------------------- discovery

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_TRENDING,
            key = "'trending:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResponse<SongResponse> getTrending(Pageable pageable) {
        return toPageResponse(songRepository.findTrending(pageable), false);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_HOME_FEED,
            key = "'new-releases:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResponse<SongResponse> getNewReleases(Pageable pageable) {
        return toPageResponse(songRepository.findNewReleases(pageable), false);
    }

    // ------------------------------------------------------------------------------------- library

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_TRENDING}, allEntries = true)
    public void likeSong(UUID songId) {
        UUID userId = SecurityUtils.requireUserId();
        Song song = loadActiveSong(songId);

        if (likedSongRepository.existsById_UserIdAndId_SongId(userId, songId)) {
            throw new ApiException(ErrorCode.ALREADY_LIKED);
        }

        likedSongRepository.save(LikedSong.builder()
                .id(new LikedSong.LikedSongId(userId, songId))
                .user(userRepository.getReferenceById(userId))
                .song(song)
                .build());
        songRepository.incrementLikeCount(songId);

        eventPublisher.publish(DomainEvent.builder(DomainEvent.SONG_LIKED)
                .user(userId)
                .song(songId)
                .artist(song.getArtist() == null ? null : song.getArtist().getId())
                .build());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_TRENDING}, allEntries = true)
    public void unlikeSong(UUID songId) {
        UUID userId = SecurityUtils.requireUserId();
        int removed = likedSongRepository.deleteLike(userId, songId);
        if (removed == 0) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Song is not in your liked songs");
        }
        songRepository.decrementLikeCount(songId);
        eventPublisher.publish(DomainEvent.builder(DomainEvent.SONG_UNLIKED)
                .user(userId)
                .song(songId)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SongResponse> getLikedSongs(Pageable pageable) {
        UUID userId = SecurityUtils.requireUserId();
        Page<LikedSong> page = likedSongRepository.findById_UserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, liked -> songMapper.toResponse(liked.getSong(), Boolean.TRUE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SongResponse> getSongsByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> liked = likedSongIds(ids);
        return songRepository.findAllByIdIn(ids).stream()
                .map(song -> songMapper.toResponse(song, liked.contains(song.getId())))
                .toList();
    }

    // ------------------------------------------------------------------------------------ helpers

    private Song loadActiveSong(UUID id) {
        return songRepository.findById(id)
                .filter(Song::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));
    }

    /** Artists may only manage songs on their own profile; admins may manage any song. */
    private void assertCanManage(Song song) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        UUID artistId = SecurityUtils.currentUser()
                .flatMap(user -> artistRepository.findByUser_Id(user.id()))
                .map(Artist::getId)
                .orElse(null);
        if (artistId == null || song.getArtist() == null || !artistId.equals(song.getArtist().getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only manage songs on your own artist profile");
        }
    }

    private Artist resolveTargetArtist(UUID requestedArtistId) {
        Artist ownArtist = SecurityUtils.currentUser()
                .flatMap(user -> artistRepository.findByUser_Id(user.id()))
                .orElse(null);

        if (SecurityUtils.isAdmin()) {
            UUID targetId = requestedArtistId != null
                    ? requestedArtistId
                    : Optional.ofNullable(ownArtist).map(Artist::getId).orElseThrow(() ->
                    new ApiException(ErrorCode.VALIDATION_FAILED,
                            "artistId is required when creating a song as an admin"));
            return artistRepository.findById(targetId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ARTIST_NOT_FOUND));
        }

        if (ownArtist == null) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Your account has no artist profile. Register as an artist to publish music.");
        }
        if (requestedArtistId != null && !ownArtist.getId().equals(requestedArtistId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only publish under your own artist profile");
        }
        return ownArtist;
    }

    private Album resolveAlbum(UUID albumId, Artist artist, boolean strictArtistMatch) {
        if (albumId == null) {
            return null;
        }
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new ApiException(ErrorCode.ALBUM_NOT_FOUND));
        if (strictArtistMatch && !SecurityUtils.isAdmin()
                && album.getArtist() != null && !album.getArtist().getId().equals(artist.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only add songs to your own albums");
        }
        return album;
    }

    private Set<Genre> resolveGenres(Set<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<String> normalized = slugs.stream()
                .filter(StringUtils::hasText)
                .map(slug -> slug.trim().toLowerCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Genre> found = genreRepository.findBySlugIn(normalized);
        if (found.size() != normalized.size()) {
            Set<String> foundSlugs = found.stream().map(Genre::getSlug).collect(Collectors.toSet());
            List<String> missing = normalized.stream().filter(s -> !foundSlugs.contains(s)).toList();
            throw new ApiException(ErrorCode.GENRE_NOT_FOUND, "Unknown genre(s): " + missing);
        }
        return new LinkedHashSet<>(found);
    }

    private PageResponse<SongResponse> toPageResponse(Page<Song> page) {
        return toPageResponse(page, true);
    }

    private PageResponse<SongResponse> toPageResponse(Page<Song> page, boolean resolveLikes) {
        Set<UUID> likedIds = resolveLikes
                ? likedSongIds(page.getContent().stream().map(Song::getId).toList())
                : Set.of();
        return PageResponse.from(page, song -> songMapper.toResponse(
                song,
                resolveLikes ? likedIds.contains(song.getId()) : null));
    }

    /** One query per page instead of N - avoids the classic liked-songs N+1. */
    private Set<UUID> likedSongIds(List<UUID> songIds) {
        Optional<UUID> userId = SecurityUtils.currentUser().map(u -> u.id());
        if (userId.isEmpty() || songIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(likedSongRepository.findLikedSongIds(userId.get(), songIds));
    }

    private Boolean likedFlag(UUID songId) {
        return SecurityUtils.currentUser()
                .map(user -> likedSongRepository.existsById_UserIdAndId_SongId(user.id(), songId))
                .orElse(null);
    }
}
