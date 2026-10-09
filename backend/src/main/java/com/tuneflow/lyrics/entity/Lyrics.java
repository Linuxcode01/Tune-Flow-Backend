package com.tuneflow.lyrics.entity;

import com.tuneflow.common.entity.BaseEntity;
import com.tuneflow.music.entity.Song;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * {@code lyrics} table (docs section 27). When {@code synced} is true, {@code content} holds LRC-
 * style timestamped lines ({@code [mm:ss.xx] lyric text}) which the Flutter player matches against
 * the current playback position.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lyrics",
        uniqueConstraints = @UniqueConstraint(name = "uk_lyrics_song_language",
                columnNames = {"song_id", "language"}),
        indexes = @Index(name = "idx_lyrics_song", columnList = "song_id"))
public class Lyrics extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    /** ISO 639-1 code, e.g. {@code en}, {@code hi}. */
    @Column(name = "language", nullable = false, length = 10)
    @Builder.Default
    private String language = "en";

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "synced", nullable = false)
    @Builder.Default
    private boolean synced = false;

    @Column(name = "source", length = 200)
    private String source;
}
