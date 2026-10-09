package com.tuneflow.history.entity;

import com.tuneflow.common.entity.BaseEntity;
import com.tuneflow.music.entity.Song;
import com.tuneflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * {@code listening_history} - playback history with completion/progress (docs section 10).
 * Written asynchronously from playback events so the API request that reports progress never waits
 * on analytics (docs sections 23, 39).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "listening_history", indexes = {
        @Index(name = "idx_history_user_started", columnList = "user_id, started_at"),
        @Index(name = "idx_history_song", columnList = "song_id")
})
public class ListeningHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "played_seconds", nullable = false)
    @Builder.Default
    private int playedSeconds = 0;

    /** Percentage of the track played, used for "scrobble" thresholds and recommendations. */
    @Column(name = "completion_ratio")
    private Double completionRatio;

    @Column(name = "device_id", length = 120)
    private String deviceId;

    @Column(name = "offline_playback", nullable = false)
    @Builder.Default
    private boolean offlinePlayback = false;
}
