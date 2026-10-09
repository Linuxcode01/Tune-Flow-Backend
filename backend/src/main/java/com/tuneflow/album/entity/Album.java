package com.tuneflow.album.entity;

import com.tuneflow.artist.entity.Artist;
import com.tuneflow.common.entity.BaseEntity;
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

import java.time.LocalDate;

/** {@code albums} table - album metadata and artwork (docs sections 10, 11). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "albums", indexes = {
        @Index(name = "idx_albums_artist", columnList = "artist_id"),
        @Index(name = "idx_albums_release_date", columnList = "release_date"),
        @Index(name = "idx_albums_title", columnList = "title")
})
public class Album extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "cover_image_url", columnDefinition = "text")
    private String coverImageUrl;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "song_count", nullable = false)
    @Builder.Default
    private int songCount = 0;
}
