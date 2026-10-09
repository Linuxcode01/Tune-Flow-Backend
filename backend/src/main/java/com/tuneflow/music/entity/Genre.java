package com.tuneflow.music.entity;

import com.tuneflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code genres} table. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "genres",
        uniqueConstraints = @UniqueConstraint(name = "uk_genres_slug", columnNames = "slug"),
        indexes = @Index(name = "idx_genres_name", columnList = "name"))
public class Genre extends BaseEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** URL-safe identifier used by the Flutter client for genre browsing. */
    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "cover_image_url", columnDefinition = "text")
    private String coverImageUrl;
}
