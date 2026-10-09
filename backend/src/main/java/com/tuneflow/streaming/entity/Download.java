package com.tuneflow.streaming.entity;

import com.tuneflow.common.entity.BaseEntity;
import com.tuneflow.music.entity.Song;
import com.tuneflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * {@code downloads} - offline download metadata and device ownership (docs section 26).
 * Offline licences are modelled per device so a download can be revoked without exposing permanent
 * public object-storage URLs.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "downloads",
        uniqueConstraints = @UniqueConstraint(name = "uk_downloads_user_song_device",
                columnNames = {"user_id", "song_id", "device_id"}),
        indexes = {
                @Index(name = "idx_downloads_user", columnList = "user_id"),
                @Index(name = "idx_downloads_status", columnList = "status")
        })
public class Download extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DownloadStatus status = DownloadStatus.PENDING;

    /** Bitrate of the delivered asset, e.g. 128/320 kbps. */
    @Column(name = "bitrate_kbps")
    private Integer bitrateKbps;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;

    /** Expiry of the offline licence; the client re-authorises after this point. */
    @Column(name = "licence_expires_at")
    private Instant licenceExpiresAt;

    public enum DownloadStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        REVOKED
    }
}
