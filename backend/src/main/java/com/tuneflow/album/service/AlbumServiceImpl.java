package com.tuneflow.album.service;

import com.tuneflow.album.dto.AlbumResponse;
import com.tuneflow.album.dto.CreateAlbumRequest;
import com.tuneflow.album.dto.UpdateAlbumRequest;
import com.tuneflow.album.entity.Album;
import com.tuneflow.album.repository.AlbumRepository;
import com.tuneflow.artist.entity.Artist;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.artist.repository.FollowedArtistRepository;
import com.tuneflow.common.config.CacheConfig;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.music.dto.ArtistSummary;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.music.service.SongMapper;
import com.tuneflow.storage.service.ArtworkUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/** Album use cases. Ownership rules mirror songs: artists manage their own albums, admins any. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumServiceImpl implements AlbumService {

    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final SongRepository songRepository;
    private final FollowedArtistRepository followedArtistRepository;
    private final SongMapper songMapper;
    private final ArtworkUrlResolver artworkUrlResolver;

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_ALBUMS, CacheConfig.CACHE_HOME_FEED}, allEntries = true)
    public AlbumResponse createAlbum(CreateAlbumRequest request) {
        Artist artist = resolveTargetArtist(request.artistId());

        if (albumRepository.existsByArtist_IdAndTitleIgnoreCase(artist.getId(), request.title().trim())) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "An album with this title already exists for this artist");
        }

        Album album = albumRepository.save(Album.builder()
                .artist(artist)
                .title(request.title().trim())
                .description(request.description())
                .releaseDate(request.releaseDate())
                .coverImageUrl(request.coverImageObjectKey())
                .build());

        log.info("Created album {} for artist {}", album.getId(), artist.getId());
        return toResponse(album);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_ALBUMS, key = "#id")
    public AlbumResponse getAlbum(UUID id) {
        Album album = albumRepository.findByIdWithArtist(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ALBUM_NOT_FOUND));
        return toResponse(album);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlbumResponse> getAlbums(UUID artistId, String query, Pageable pageable) {
        Page<Album> page;
        if (StringUtils.hasText(query)) {
            page = albumRepository.search(query.trim(), pageable);
        } else if (artistId != null) {
            page = albumRepository.findByArtist_Id(artistId, pageable);
        } else {
            page = albumRepository.findByReleaseDateIsNotNullOrderByReleaseDateDesc(pageable);
        }
        return PageResponse.from(page, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_HOME_FEED,
            key = "'albums-new-releases:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResponse<AlbumResponse> getNewReleases(Pageable pageable) {
        return PageResponse.from(albumRepository.findNewReleases(pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SongResponse> getAlbumSongs(UUID albumId, Pageable pageable) {
        albumRepository.findById(albumId)
                .orElseThrow(() -> new ApiException(ErrorCode.ALBUM_NOT_FOUND));
        // Tracks are ordered by track number, then title, so an album reads in listening order.
        Page<Song> songs = songRepository.findByAlbum_IdAndActiveTrue(
                albumId,
                org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        org.springframework.data.domain.Sort.by("trackNumber").and(
                                org.springframework.data.domain.Sort.by("title"))));
        return PageResponse.from(songs, song -> songMapper.toResponse(song, null));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_ALBUMS, CacheConfig.CACHE_HOME_FEED}, allEntries = true)
    public AlbumResponse updateAlbum(UUID id, UpdateAlbumRequest request) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ALBUM_NOT_FOUND));
        assertCanManage(album);

        if (request.title() != null) {
            if (!StringUtils.hasText(request.title())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Title cannot be blank");
            }
            album.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            album.setDescription(request.description());
        }
        if (request.releaseDate() != null) {
            album.setReleaseDate(request.releaseDate());
        }
        if (request.coverImageObjectKey() != null) {
            album.setCoverImageUrl(StringUtils.hasText(request.coverImageObjectKey())
                    ? request.coverImageObjectKey() : null);
        }
        return toResponse(albumRepository.save(album));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_ALBUMS, CacheConfig.CACHE_HOME_FEED}, allEntries = true)
    public void deleteAlbum(UUID id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ALBUM_NOT_FOUND));
        assertCanManage(album);

        // Songs are catalog entries in their own right; detach rather than cascade-delete so
        // playlists and listening history remain valid (docs section 39: preserve referential
        // integrity rather than silently destroying user data).
        songRepository.findByAlbum_IdAndActiveTrue(id, Pageable.ofSize(500))
                .forEach(song -> song.setAlbum(null));
        albumRepository.delete(album);
        log.info("Deleted album {} (songs detached, not deleted)", id);
    }

    // ------------------------------------------------------------------------------------ helpers

    private void assertCanManage(Album album) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        UUID ownArtistId = SecurityUtils.currentUser()
                .flatMap(user -> artistRepository.findByUser_Id(user.id()))
                .map(Artist::getId)
                .orElse(null);
        if (ownArtistId == null || album.getArtist() == null || !ownArtistId.equals(album.getArtist().getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only manage albums on your own artist profile");
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
                            "artistId is required when creating an album as an admin"));
            return artistRepository.findById(targetId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ARTIST_NOT_FOUND));
        }
        if (ownArtist == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Your account has no artist profile");
        }
        if (requestedArtistId != null && !ownArtist.getId().equals(requestedArtistId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only create albums for your own artist profile");
        }
        return ownArtist;
    }

    private AlbumResponse toResponse(Album album) {
        int totalDuration = songRepository.findByAlbum_IdAndActiveTrue(album.getId(), Pageable.ofSize(200))
                .getContent().stream()
                .mapToInt(Song::getDurationSeconds)
                .sum();

        ArtistSummary artistSummary = album.getArtist() == null ? null : new ArtistSummary(
                album.getArtist().getId(),
                album.getArtist().getName(),
                artworkUrlResolver.resolve(album.getArtist().getProfileImageUrl()),
                album.getArtist().isVerified());

        Boolean following = SecurityUtils.currentUser()
                .map(user -> followedArtistRepository.existsById_UserIdAndId_ArtistId(
                        user.id(), album.getArtist() == null ? null : album.getArtist().getId()))
                .orElse(null);

        return new AlbumResponse(
                album.getId(),
                album.getTitle(),
                album.getDescription(),
                artworkUrlResolver.resolve(album.getCoverImageUrl()),
                album.getReleaseDate(),
                album.getSongCount(),
                totalDuration,
                artistSummary,
                following);
    }
}
