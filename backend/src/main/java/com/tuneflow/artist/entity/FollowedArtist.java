package com.tuneflow.artist.entity;

import com.tuneflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** {@code followed_artists} join table (User N---N Artist, docs section 11). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "followed_artists", indexes = {
        @Index(name = "idx_followed_artists_user", columnList = "user_id"),
        @Index(name = "idx_followed_artists_artist", columnList = "artist_id")
})
public class FollowedArtist {

    @EmbeddedId
    private FollowedArtistId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("artistId")
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** Followers are notified about new releases (docs section 28). */
    @Column(name = "notify_new_releases", nullable = false)
    @Builder.Default
    private boolean notifyNewReleases = true;

    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class FollowedArtistId implements Serializable {
        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "artist_id")
        private UUID artistId;
    }
}
