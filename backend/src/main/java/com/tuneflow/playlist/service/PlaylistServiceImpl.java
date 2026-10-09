package com.tuneflow.playlist.service;

import com.tuneflow.common.event.DomainEvent;
import com.tuneflow.common.event.EventPublisher;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.music.dto.SongResponse;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.music.service.SongMapper;
import com.tuneflow.playlist.dto.AddSongRequest;
import com.tuneflow.playlist.dto.CreatePlaylistRequest;
import com.tuneflow.playlist.dto.PlaylistDetailResponse;
import com.tuneflow.playlist.dto.PlaylistResponse;
import com.tuneflow.playlist.dto.ReorderPlaylistRequest;
import com.tuneflow.playlist.dto.UpdatePlaylistRequest;
import com.tuneflow.playlist.entity.Playlist;
import com.tuneflow.playlist.entity.PlaylistSong;
import com.tuneflow.playlist.repository.PlaylistRepository;
import com.tuneflow.playlist.repository.PlaylistSongRepository;
import com.tuneflow.storage.service.ArtworkUrlResolver;
import com.tuneflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Playlist rules (docs sections 3, 13, 20-21).
 *
 * <p>Visibility: a private playlist is invisible to anyone but its owner (404, not 403, so the
 * existence of other users' private data is not leaked). Ownership is checked on every mutation.
 * Ordering is explicit via {@code position}, which is rewritten in one pass on reorder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistServiceImpl implements PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final SongRepository songRepository;
    private final com.tuneflow.music.repository.LikedSongRepository likedSongRepository;
    private final UserRepository userRepository;
    private final SongMapper songMapper;
    private final ArtworkUrlResolver artworkUrlResolver;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public PlaylistResponse createPlaylist(CreatePlaylistRequest request) {
        UUID userId = SecurityUtils.requireUserId();
        Playlist playlist = playlistRepository.save(Playlist.builder()
                .owner(userRepository.getReferenceById(userId))
                .name(request.name().trim())
                .description(request.description())
                .publicVisible(request.publicPlaylist())
                .coverImageUrl(request.coverImageObjectKey())
                .build());

        eventPublisher.publish(DomainEvent.builder(DomainEvent.PLAYLIST_CREATED)
                .user(userId)
                .playlist(playlist.getId())
                .build());
        return toResponse(playlist);
    }

    @Override
    @Transactional(readOnly = true)
    public PlaylistDetailResponse getPlaylist(UUID id) {
        Playlist playlist = loadVisiblePlaylist(id);
        List<PlaylistSong> items = playlistSongRepository.findItemsWithSongs(id);
        Set<UUID> liked = likedSongIds(items.stream().map(item -> item.getSong().getId()).toList());
        List<SongResponse> songs = items.stream()
                .map(item -> songMapper.toResponse(item.getSong(), liked.contains(item.getSong().getId())))
                .toList();
        return new PlaylistDetailResponse(toResponse(playlist), songs);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlaylistResponse> getMyPlaylists(Pageable pageable) {
        UUID userId = SecurityUtils.requireUserId();
        Page<Playlist> page = playlistRepository.findByOwner_Id(userId, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlaylistResponse> searchPublicPlaylists(String query, Pageable pageable) {
        Page<Playlist> page = StringUtils.hasText(query)
                ? playlistRepository.searchPublic(query.trim(), pageable)
                : playlistRepository.findPublicPlaylists(pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Override
    @Transactional
    public PlaylistResponse updatePlaylist(UUID id, UpdatePlaylistRequest request) {
        Playlist playlist = loadOwnedPlaylist(id);

        if (request.name() != null) {
            if (!StringUtils.hasText(request.name())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Playlist name cannot be blank");
            }
            playlist.setName(request.name().trim());
        }
        if (request.description() != null) {
            playlist.setDescription(request.description());
        }
        if (request.publicPlaylist() != null) {
            if (playlist.isSystemPlaylist() && request.publicPlaylist()) {
                throw new ApiException(ErrorCode.FORBIDDEN, "System playlists cannot be made public");
            }
            playlist.setPublicVisible(request.publicPlaylist());
        }
        if (request.coverImageObjectKey() != null) {
            playlist.setCoverImageUrl(StringUtils.hasText(request.coverImageObjectKey())
                    ? request.coverImageObjectKey() : null);
        }

        Playlist saved = playlistRepository.save(playlist);
        eventPublisher.publish(DomainEvent.builder(DomainEvent.PLAYLIST_UPDATED)
                .user(SecurityUtils.requireUserId())
                .playlist(saved.getId())
                .build());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deletePlaylist(UUID id) {
        Playlist playlist = loadOwnedPlaylist(id);
        if (playlist.isSystemPlaylist()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "System playlists cannot be deleted");
        }
        playlistSongRepository.deleteAllItems(id);
        playlistRepository.delete(playlist);
        log.info("Deleted playlist {}", id);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse addSong(UUID playlistId, AddSongRequest request) {
        Playlist playlist = loadOwnedPlaylist(playlistId);
        Song song = songRepository.findById(request.songId())
                .filter(Song::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));

        if (playlistSongRepository.findById_PlaylistIdAndId_SongId(playlistId, song.getId()).isPresent()) {
            throw new ApiException(ErrorCode.SONG_ALREADY_IN_PLAYLIST);
        }

        List<PlaylistSong> existing = playlistSongRepository.findItemsWithSongs(playlistId);
        int insertAt = request.position() == null
                ? existing.size()
                : Math.max(0, Math.min(request.position(), existing.size()));

        // Shift the tail down to open a slot, then insert.
        for (int i = existing.size() - 1; i >= insertAt; i--) {
            PlaylistSong item = existing.get(i);
            item.setPosition(item.getPosition() + 1);
            playlistSongRepository.save(item);
        }

        playlistSongRepository.save(PlaylistSong.builder()
                .id(new PlaylistSong.PlaylistSongId(playlistId, song.getId()))
                .playlist(playlist)
                .song(song)
                .position(insertAt)
                .addedBy(SecurityUtils.currentUser().map(u -> u.email()).orElse(null))
                .build());

        refreshAggregates(playlist);
        eventPublisher.publish(DomainEvent.builder(DomainEvent.PLAYLIST_UPDATED)
                .user(SecurityUtils.requireUserId())
                .playlist(playlistId)
                .song(song.getId())
                .build());
        return getPlaylist(playlistId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse removeSong(UUID playlistId, UUID songId) {
        Playlist playlist = loadOwnedPlaylist(playlistId);
        int removed = playlistSongRepository.deleteItem(playlistId, songId);
        if (removed == 0) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Song is not part of this playlist");
        }
        renumber(playlistId);
        refreshAggregates(playlist);
        eventPublisher.publish(DomainEvent.builder(DomainEvent.PLAYLIST_UPDATED)
                .user(SecurityUtils.requireUserId())
                .playlist(playlistId)
                .song(songId)
                .build());
        return getPlaylist(playlistId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse reorder(UUID playlistId, ReorderPlaylistRequest request) {
        Playlist playlist = loadOwnedPlaylist(playlistId);
        List<PlaylistSong> items = playlistSongRepository.findItemsWithSongs(playlistId);

        Map<UUID, PlaylistSong> bySong = new LinkedHashMap<>();
        items.forEach(item -> bySong.put(item.getSong().getId(), item));

        List<UUID> requested = request.songIds();
        if (requested.size() != items.size()
                || new HashSet<>(requested).size() != requested.size()
                || !bySong.keySet().containsAll(requested)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "songIds must list every track in the playlist exactly once");
        }

        for (int index = 0; index < requested.size(); index++) {
            PlaylistSong item = bySong.get(requested.get(index));
            item.setPosition(index);
            playlistSongRepository.save(item);
        }

        refreshAggregates(playlist);
        return getPlaylist(playlistId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse clear(UUID playlistId) {
        Playlist playlist = loadOwnedPlaylist(playlistId);
        playlistSongRepository.deleteAllItems(playlistId);
        refreshAggregates(playlist);
        return getPlaylist(playlistId);
    }

    // ------------------------------------------------------------------------------------ helpers

    private Playlist loadVisiblePlaylist(UUID id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PLAYLIST_NOT_FOUND));
        UUID currentUserId = SecurityUtils.currentUser().map(u -> u.id()).orElse(null);
        boolean owner = currentUserId != null && playlist.getOwner().getId().equals(currentUserId);
        if (!playlist.isPublicVisible() && !owner && !SecurityUtils.isAdmin()) {
            // 404 rather than 403 so private playlists cannot be probed for existence.
            throw new ApiException(ErrorCode.PLAYLIST_NOT_FOUND);
        }
        return playlist;
    }

    private Playlist loadOwnedPlaylist(UUID id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PLAYLIST_NOT_FOUND));
        UUID currentUserId = SecurityUtils.requireUserId();
        if (!playlist.getOwner().getId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only modify your own playlists");
        }
        return playlist;
    }

    /** Re-packs positions to 0..n-1 after a removal. */
    private void renumber(UUID playlistId) {
        List<PlaylistSong> items = playlistSongRepository.findItemsWithSongs(playlistId);
        for (int index = 0; index < items.size(); index++) {
            PlaylistSong item = items.get(index);
            if (item.getPosition() != index) {
                item.setPosition(index);
                playlistSongRepository.save(item);
            }
        }
    }

    private void refreshAggregates(Playlist playlist) {
        List<PlaylistSong> items = playlistSongRepository.findItemsWithSongs(playlist.getId());
        playlist.setSongCount(items.size());
        playlist.setTotalDurationSeconds(items.stream().mapToInt(item -> item.getSong().getDurationSeconds()).sum());
        playlistRepository.save(playlist);
    }

    private PlaylistResponse toResponse(Playlist playlist) {
        UUID currentUserId = SecurityUtils.currentUser().map(u -> u.id()).orElse(null);
        boolean owned = currentUserId != null && playlist.getOwner().getId().equals(currentUserId);
        return new PlaylistResponse(
                playlist.getId(),
                playlist.getName(),
                playlist.getDescription(),
                playlist.getCoverImageUrl() != null
                        ? artworkUrlResolver.resolve(playlist.getCoverImageUrl())
                        : pickCoverFromSongs(playlist.getId()),
                playlist.isPublicVisible(),
                playlist.isSystemPlaylist(),
                playlist.getSongCount(),
                playlist.getTotalDurationSeconds(),
                playlist.getOwner().getId(),
                playlist.getOwner().getName(),
                owned,
                playlist.getCreatedAt(),
                playlist.getUpdatedAt());
    }

    /** A playlist without explicit artwork borrows the first track's cover. */
    private String pickCoverFromSongs(UUID playlistId) {
        return playlistSongRepository.findItemsWithSongs(playlistId).stream()
                .map(item -> item.getSong().getCoverImageUrl() != null
                        ? item.getSong().getCoverImageUrl()
                        : (item.getSong().getAlbum() != null ? item.getSong().getAlbum().getCoverImageUrl() : null))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(artworkUrlResolver::resolve)
                .orElse(null);
    }

    /** One query for the whole track list instead of N. */
    private Set<UUID> likedSongIds(List<UUID> songIds) {
        UUID userId = SecurityUtils.currentUser().map(u -> u.id()).orElse(null);
        if (userId == null || songIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(likedSongRepository.findLikedSongIds(userId, songIds));
    }
}
