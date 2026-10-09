package com.tuneflow.notification.dto;

import com.tuneflow.notification.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "NotificationResponse")
public record NotificationResponse(
        UUID id,
        Notification.NotificationType type,
        String title,
        String body,
        String data,
        boolean read,
        Instant createdAt,
        Instant readAt
) {
}
