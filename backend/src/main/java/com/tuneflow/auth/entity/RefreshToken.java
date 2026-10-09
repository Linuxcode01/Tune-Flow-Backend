package com.tuneflow.auth.entity;

import com.tuneflow.common.entity.BaseEntity;
import com.tuneflow.user.entity.User;
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

import java.time.Instant;

/**
 * {@code refresh_tokens} - refresh-token lifecycle (docs section 10).
 *
 * <p>Only the SHA-256 hash of the token is stored, so a database leak does not hand out sessions.
 * Refresh tokens are rotated on every use and revoked on logout or password change
 * (docs section 15: "persisted server-side or represented with a revocable token strategy").
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_expiry", columnList = "expires_at")
        })
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Set when this token was rotated, so reuse of an old token can be detected. */
    @Column(name = "replaced_by_hash", length = 64)
    private String replacedByHash;

    @Column(name = "device_id", length = 120)
    private String deviceId;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
