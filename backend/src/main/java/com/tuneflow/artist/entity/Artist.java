package com.tuneflow.artist.entity;

import com.tuneflow.common.entity.BaseEntity;
import com.tuneflow.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code artists} table - artist profile linked to a user account where applicable
 * (docs sections 9-10).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "artists",
        uniqueConstraints = @UniqueConstraint(name = "uk_artists_user_id", columnNames = "user_id"),
        indexes = {
                @Index(name = "idx_artists_name", columnList = "name"),
                @Index(name = "idx_artists_verified", columnList = "verified")
        })
public class Artist extends BaseEntity {

    /** Nullable: an artist catalog entry can exist before it is claimed by a user account. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    @Column(name = "country", length = 80)
    private String country;

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "follower_count", nullable = false)
    @Builder.Default
    private long followerCount = 0L;
}
