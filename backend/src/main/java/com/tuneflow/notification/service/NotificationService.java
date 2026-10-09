package com.tuneflow.notification.service;

import com.tuneflow.common.response.PageResponse;
import com.tuneflow.notification.dto.NotificationResponse;
import com.tuneflow.notification.entity.Notification;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    PageResponse<NotificationResponse> getMyNotifications(Pageable pageable);

    long getUnreadCount();

    NotificationResponse markRead(UUID notificationId);

    int markAllRead();

    /** Used by event listeners (new releases, recommendations, system messages). */
    void create(UUID userId, Notification.NotificationType type, String title, String body, String data);
}
