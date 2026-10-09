package com.tuneflow.notification.entity;

import com.tuneflow.common.entity.BaseEntity;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * {@code notifications} - in-app notification record (docs section 28). The same record is pushed
 * to Firebase Cloud Messaging when a device token is registered; the in-app copy guarantees the
 * user still sees the message when push is unavailable.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notifications_unread", columnList = "user_id, read_at")
})
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    /** Deep-link payload, e.g. {@code {"songId": "...", "screen": "player"}}. */
    @Column(name = "data", columnDefinition = "text")
    private String data;

    @Column(name = "read_at")
    private Instant readAt;

    public enum NotificationType {
        NEW_RELEASE,
        RECOMMENDATION,
        SYSTEM
    }
}
