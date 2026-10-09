package com.tuneflow.streaming.service;

import com.tuneflow.common.config.AppProperties;
import com.tuneflow.common.event.DomainEvent;
import com.tuneflow.common.event.EventPublisher;
import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.music.entity.Song;
import com.tuneflow.music.repository.SongRepository;
import com.tuneflow.music.service.SongMapper;
import com.tuneflow.storage.service.StorageService;
import com.tuneflow.streaming.dto.CreateDownloadRequest;
import com.tuneflow.streaming.dto.DownloadResponse;
// response mapping happens before counter updates - see completeDownload()
import com.tuneflow.streaming.dto.StreamUrlResponse;
import com.tuneflow.streaming.entity.Download;
import com.tuneflow.streaming.repository.DownloadRepository;
import com.tuneflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Playback authorization and offline downloads (docs sections 17, 26, 40).
 *
 * <p>Flow: the client authenticates, this service authorizes the request and mints a short-lived
 * signed URL, then the CDN/object store delivers the bytes. Download records exist so the offline
 * licence can be revoked per device without exposing permanent public URLs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingServiceImpl implements StreamingService {

    private static final Duration OFFLINE_LICENCE_TTL = Duration.ofDays(30);

    private final SongRepository songRepository;
    private final DownloadRepository downloadRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final SongMapper songMapper;
    private final AppProperties appProperties;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public StreamUrlResponse createStreamUrl(UUID songId) {
        Song song = songRepository.findById(songId)
                .filter(Song::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));

        // Prefer the HLS manifest when the media pipeline produced one (adaptive bitrate).
        String objectKey = Optional.ofNullable(song.getStreamManifestUrl())
                .filter(key -> !key.isBlank())
                .orElse(song.getAudioUrl());

        if (objectKey == null || objectKey.isBlank()) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Audio for this track has not been processed yet");
        }

        Duration ttl = appProperties.media().signedUrlTtl();
        String url;
        try {
            url = storageService.generateSignedUrl(objectKey, ttl);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.STORAGE_ERROR, "Unable to authorize playback right now");
        }

        boolean hls = objectKey.endsWith(".m3u8");
        Instant expiresAt = Instant.now().plus(ttl);

        // Play counts are incremented asynchronously off the playback event, never here.
        eventPublisher.publish(DomainEvent.builder(DomainEvent.SONG_PLAYED)
                .user(SecurityUtils.currentUser().map(u -> u.id()).orElse(null))
                .song(song.getId())
                .artist(song.getArtist() == null ? null : song.getArtist().getId())
                .payload(Map.of("stage", "stream-authorised"))
                .build());

        return new StreamUrlResponse(
                song.getId(),
                song.getTitle(),
                url,
                hls,
                expiresAt,
                ttl.toSeconds(),
                hls ? 128 : 320,
                contentTypeFor(objectKey, hls));
    }

    @Override
    @Transactional
    public DownloadResponse startDownload(CreateDownloadRequest request) {
        UUID userId = SecurityUtils.requireUserId();
        Song song = songRepository.findById(request.songId())
                .filter(Song::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.SONG_NOT_FOUND));

        if (song.getAudioUrl() == null || song.getAudioUrl().isBlank()) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "This track is not available for download");
        }

        int bitrate = request.bitrateKbps() == null
                ? appProperties.media().defaultBitrateKbps()
                : request.bitrateKbps();

        Download download = downloadRepository
                .findByUser_IdAndSong_IdAndDeviceId(userId, song.getId(), request.deviceId())
                .map(existing -> {
                    // Re-issuing a download refreshes the licence instead of creating a duplicate.
                    existing.setStatus(Download.DownloadStatus.IN_PROGRESS);
                    existing.setLicenceExpiresAt(Instant.now().plus(OFFLINE_LICENCE_TTL));
                    existing.setBitrateKbps(bitrate);
                    return existing;
                })
                .orElseGet(() -> Download.builder()
                        .user(userRepository.getReferenceById(userId))
                        .song(song)
                        .deviceId(request.deviceId())
                        .status(Download.DownloadStatus.IN_PROGRESS)
                        .bitrateKbps(bitrate)
                        .licenceExpiresAt(Instant.now().plus(OFFLINE_LICENCE_TTL))
                        .build());

        try {
            download = downloadRepository.save(download);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent requests for the same song+device; the unique constraint wins.
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "A download for this song already exists on this device");
        }

        String url = storageService.generateSignedUrl(song.getAudioUrl(), appProperties.media().signedUrlTtl());
        return toResponse(download, url);
    }

    @Override
    @Transactional
    public DownloadResponse completeDownload(UUID downloadId) {
        UUID userId = SecurityUtils.requireUserId();
        Download download = downloadRepository.findById(downloadId)
                .filter(d -> d.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Download not found"));

        download.setStatus(Download.DownloadStatus.COMPLETED);
        download.setDownloadedAt(Instant.now());
        if (download.getSizeBytes() == null) {
            // Estimated from the rendition bitrate; the client reports the exact byte count.
            int bitrate = download.getBitrateKbps() == null ? 128 : download.getBitrateKbps();
            download.setSizeBytes((long) (bitrate * 1000 / 8) * Math.max(1, download.getSong().getDurationSeconds()));
        }
        download = downloadRepository.save(download);

        // Build the response while the entity graph is still populated, then run the counter update.
        DownloadResponse response = toResponse(download, null);

        songRepository.incrementDownloadCount(download.getSong().getId());
        eventPublisher.publish(DomainEvent.builder(DomainEvent.SONG_DOWNLOADED)
                .user(userId)
                .song(download.getSong().getId())
                .payload(Map.of("deviceId", download.getDeviceId()))
                .build());

        log.info("Download {} completed for song {}", downloadId, download.getSong().getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DownloadResponse> getDownloads(Pageable pageable) {
        UUID userId = SecurityUtils.requireUserId();
        Page<Download> page = downloadRepository.findByUserWithSong(userId, pageable);
        // Listing returns metadata only; a fresh signed URL is minted per download on demand.
        return PageResponse.from(page, download -> toResponse(download, null));
    }

    @Override
    @Transactional
    public void revokeDownload(UUID downloadId) {
        UUID userId = SecurityUtils.requireUserId();
        Download download = downloadRepository.findById(downloadId)
                .filter(d -> d.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Download not found"));

        download.setStatus(Download.DownloadStatus.REVOKED);
        download.setLicenceExpiresAt(Instant.now());
        downloadRepository.save(download);
        log.info("Download {} revoked; the client must delete its local copy", downloadId);
    }

    /**
     * Content type of the stored object, derived from its extension.
     *
     * <p>It used to be hard-coded to {@code audio/mpeg}, which is a lie for anything an artist
     * uploaded as wav/flac/m4a: a player that trusts the header can refuse to decode a track that is
     * perfectly fine, and the transcoding pipeline is optional, so the stored object really is the
     * original format.
     */
    static String contentTypeFor(String objectKey, boolean hls) {
        if (hls || objectKey.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        int dot = objectKey.lastIndexOf('.');
        String extension = dot < 0 ? "" : objectKey.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return switch (extension) {
            case "mp3" -> "audio/mpeg";
            case "m4a", "aac", "mp4", "m4b" -> "audio/mp4";
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            case "ogg", "oga", "opus" -> "audio/ogg";
            default -> "application/octet-stream";
        };
    }

    private DownloadResponse toResponse(Download download, String url) {
        return new DownloadResponse(
                download.getId(),
                songMapper.toResponse(download.getSong(), null),
                download.getDeviceId(),
                download.getStatus(),
                download.getBitrateKbps(),
                download.getSizeBytes(),
                url,
                download.getLicenceExpiresAt(),
                download.getDownloadedAt(),
                download.getCreatedAt());
    }
}
