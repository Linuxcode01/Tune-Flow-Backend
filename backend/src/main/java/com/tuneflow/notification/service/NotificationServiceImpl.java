package com.tuneflow.notification.service;

import com.tuneflow.common.exception.ApiException;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.common.security.SecurityUtils;
import com.tuneflow.notification.dto.NotificationResponse;
import com.tuneflow.notification.entity.Notification;
import com.tuneflow.notification.repository.NotificationRepository;
import com.tuneflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * In-app notifications (docs section 28). Every notification is persisted first; the push (FCM)
 * delivery is a separate, pluggable concern so the in-app inbox always works even when push is
 * unavailable or disabled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(Pageable pageable) {
        UUID userId = SecurityUtils.requireUserId();
        Page<Notification> page = notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return notificationRepository.countByUser_IdAndReadAtIsNull(SecurityUtils.requireUserId());
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID userId = SecurityUtils.requireUserId();
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(SecurityUtils.requireUserId(), Instant.now());
    }

    @Override
    @Transactional
    public void create(UUID userId, Notification.NotificationType type, String title, String body, String data) {
        if (userId == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .type(type)
                .title(title)
                .body(body)
                .data(data)
                .build());
        log.debug("Created {} notification for user {}", type, userId);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getData(),
                notification.getReadAt() != null,
                notification.getCreatedAt(),
                notification.getReadAt());
    }
}
