package com.tuneflow.music.entity;

import com.tuneflow.album.entity.Album;
import com.tuneflow.artist.entity.Artist;
import com.tuneflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code songs} table - song metadata, duration, storage references and counters
 * (docs sections 10-11).
 *
 * <p>{@code audioUrl} stores the object-storage key or CDN path, never a permanent public URL.
 * Clients receive short-lived signed URLs from {@code GET /api/v1/songs/{id}/stream} instead
 * (docs sections 17, 26, 39).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "songs", indexes = {
        @Index(name = "idx_songs_album", columnList = "album_id"),
        @Index(name = "idx_songs_artist", columnList = "artist_id"),
        @Index(name = "idx_songs_title", columnList = "title"),
        @Index(name = "idx_songs_release_date", columnList = "release_date"),
        @Index(name = "idx_songs_play_count", columnList = "play_count")
})
public class Song extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    /** Object key / CDN-relative path of the processed audio asset. */
    @Column(name = "audio_url", columnDefinition = "text")
    private String audioUrl;

    /** Optional explicit HLS/segmented manifest for adaptive streaming. */
    @Column(name = "stream_manifest_url", columnDefinition = "text")
    private String streamManifestUrl;

    @Column(name = "cover_image_url", columnDefinition = "text")
    private String coverImageUrl;

    @Column(name = "track_number")
    private Integer trackNumber;

    @Column(name = "play_count", nullable = false)
    @Builder.Default
    private long playCount = 0L;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private long likeCount = 0L;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private long downloadCount = 0L;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "explicit_flag", nullable = false)
    @Builder.Default
    private boolean explicit = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "song_genres",
            joinColumns = @JoinColumn(name = "song_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"),
            uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                    name = "uk_song_genres", columnNames = {"song_id", "genre_id"}),
            indexes = @Index(name = "idx_song_genres_genre", columnList = "genre_id"))
    @Builder.Default
    private Set<Genre> genres = new LinkedHashSet<>();
}
