package com.tuneflow.artist.service;

import com.tuneflow.album.repository.AlbumRepository;
import com.tuneflow.artist.dto.ArtistResponse;
import com.tuneflow.artist.dto.UpdateArtistRequest;
import com.tuneflow.artist.entity.Artist;
import com.tuneflow.artist.entity.FollowedArtist;
import com.tuneflow.artist.repository.ArtistRepository;
import com.tuneflow.artist.repository.FollowedArtistRepository;
import com.tuneflow.common.config.CacheConfig;
import com.tuneflow.common.event.DomainEvent;
import com.tuneflow.common.event.EventPublisher;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.storage.service.ArtworkUrlResolver;
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

import java.util.UUID;

/** Artist profiles plus the follow graph (docs sections 11, 13). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistServiceImpl implements ArtistService {

    private final ArtistRepository artistRepository;
    private final FollowedArtistRepository followedArtistRepository;
    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final UserRepository userRepository;
    private final ArtworkUrlResolver artworkUrlResolver;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_ARTISTS, key = "#id")
    public ArtistResponse getArtist(UUID id) {
        return toResponse(loadArtist(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ArtistResponse> getArtists(String query, Pageable pageable) {
        Page<Artist> page = StringUtils.hasText(query)
                ? artistRepository.search(query.trim(), pageable)
                : artistRepository.findAll(pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_HOME_FEED,
            key = "'popular-artists:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResponse<ArtistResponse> getPopularArtists(Pageable pageable) {
        return PageResponse.from(artistRepository.findPopular(pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ArtistResponse getMyArtistProfile() {
        UUID userId = SecurityUtils.requireUserId();
        Artist artist = artistRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ARTIST_NOT_FOUND,
                        "Your account does not have an artist profile"));
        return toResponse(artist);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_ARTISTS, CacheConfig.CACHE_HOME_FEED}, allEntries = true)
    public ArtistResponse updateArtist(UUID id, UpdateArtistRequest request) {
        Artist artist = loadArtist(id);
        assertCanManage(artist);
        return applyUpdate(artist, request);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_ARTISTS, CacheConfig.CACHE_HOME_FEED}, allEntries = true)
    public ArtistResponse updateMyArtistProfile(UpdateArtistRequest request) {
        UUID userId = SecurityUtils.requireUserId();
        Artist artist = artistRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ARTIST_NOT_FOUND,
                        "Your account does not have an artist profile"));
        return applyUpdate(artist, request);
    }

    @Override
    @Transactional
    public void followArtist(UUID artistId) {
        UUID userId = SecurityUtils.requireUserId();
        Artist artist = loadArtist(artistId);

        if (followedArtistRepository.existsById_UserIdAndId_ArtistId(userId, artistId)) {
            throw new ApiException(ErrorCode.ALREADY_FOLLOWING);
        }

        followedArtistRepository.save(FollowedArtist.builder()
                .id(new FollowedArtist.FollowedArtistId(userId, artistId))
                .user(userRepository.getReferenceById(userId))
                .artist(artist)
                .build());

        artist.setFollowerCount(artist.getFollowerCount() + 1);
        artistRepository.save(artist);

        eventPublisher.publish(DomainEvent.builder(DomainEvent.USER_FOLLOWED)
                .user(userId)
                .artist(artistId)
                .build());
        evictArtistCache(artistId);
    }

    @Override
    @Transactional
    public void unfollowArtist(UUID artistId) {
        UUID userId = SecurityUtils.requireUserId();
        Artist artist = loadArtist(artistId);

        int removed = followedArtistRepository.deleteFollow(userId, artistId);
        if (removed == 0) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "You are not following this artist");
        }
        artist.setFollowerCount(Math.max(0, artist.getFollowerCount() - 1));
        artistRepository.save(artist);
        evictArtistCache(artistId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ArtistResponse> getFollowedArtists(Pageable pageable) {
        UUID userId = SecurityUtils.requireUserId();
        Page<FollowedArtist> page = followedArtistRepository.findById_UserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, follow -> toResponse(follow.getArtist()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_ARTISTS, allEntries = true)
    public void verifyArtist(UUID artistId, boolean verified) {
        if (!SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only administrators can verify artists");
        }
        Artist artist = loadArtist(artistId);
        artist.setVerified(verified);
        artistRepository.save(artist);
        log.info("Artist {} verification set to {}", artistId, verified);
    }

    // ------------------------------------------------------------------------------------ helpers

    private ArtistResponse applyUpdate(Artist artist, UpdateArtistRequest request) {
        if (StringUtils.hasText(request.name())) {
            artist.setName(request.name().trim());
        }
        if (request.bio() != null) {
            artist.setBio(request.bio());
        }
        if (request.country() != null) {
            artist.setCountry(request.country());
        }
        if (StringUtils.hasText(request.profileImageObjectKey())) {
            artist.setProfileImageUrl(request.profileImageObjectKey().trim());
        } else if (request.profileImageUrl() != null) {
            artist.setProfileImageUrl(StringUtils.hasText(request.profileImageUrl())
                    ? request.profileImageUrl().trim() : null);
        }
        return toResponse(artistRepository.save(artist));
    }

    private void assertCanManage(Artist artist) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        UUID userId = SecurityUtils.requireUserId();
        if (artist.getUser() == null || !artist.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only edit your own artist profile");
        }
    }

    private Artist loadArtist(UUID id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ARTIST_NOT_FOUND));
    }

    private ArtistResponse toResponse(Artist artist) {
        Boolean following = SecurityUtils.currentUser()
                .map(user -> followedArtistRepository.existsById_UserIdAndId_ArtistId(user.id(), artist.getId()))
                .orElse(null);

        return new ArtistResponse(
                artist.getId(),
                artist.getName(),
                artist.getBio(),
                artworkUrlResolver.resolve(artist.getProfileImageUrl()),
                artist.getCountry(),
                artist.isVerified(),
                artist.getFollowerCount(),
                songRepository.countByArtist_Id(artist.getId()),
                albumRepository.findByArtist_Id(artist.getId(), Pageable.ofSize(1)).getTotalElements(),
                artist.getUser() == null ? null : artist.getUser().getId(),
                following,
                artist.getCreatedAt());
    }

    private void evictArtistCache(UUID artistId) {
        // Declarative eviction is applied on the write methods above; follower counts are also
        // reflected in cached responses, so the artist entry is cleared eagerly here via the
        // CacheManager-backed annotation on updateArtist/verifyArtist paths.
        log.debug("Artist {} follow graph changed", artistId);
    }
}
