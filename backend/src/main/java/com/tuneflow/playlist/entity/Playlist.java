package com.tuneflow.playlist.entity;

import com.tuneflow.common.entity.BaseEntity;
import com.tuneflow.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** {@code playlists} table - user-owned playlists (docs sections 10-11). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "playlists", indexes = {
        @Index(name = "idx_playlists_user", columnList = "user_id"),
        @Index(name = "idx_playlists_public", columnList = "is_public"),
        @Index(name = "idx_playlists_name", columnList = "name")
})
public class Playlist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "cover_image_url", columnDefinition = "text")
    private String coverImageUrl;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean publicVisible = false;

    /** System playlists (e.g. "Liked Songs") cannot be deleted or made public by the client. */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean systemPlaylist = false;

    @Column(name = "song_count", nullable = false)
    @Builder.Default
    private int songCount = 0;

    @Column(name = "total_duration_seconds", nullable = false)
    @Builder.Default
    private int totalDurationSeconds = 0;

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @Builder.Default
    private List<PlaylistSong> items = new ArrayList<>();
}
