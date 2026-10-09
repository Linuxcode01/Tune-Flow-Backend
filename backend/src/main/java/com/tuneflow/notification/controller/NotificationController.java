package com.tuneflow.notification.controller;

import com.tuneflow.common.response.ApiResponse;
import com.tuneflow.common.response.PageResponse;
import com.tuneflow.notification.dto.NotificationResponse;
import com.tuneflow.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** {@code /api/v1/notifications} - in-app notification inbox (docs section 28). */
@Tag(name = "Notifications", description = "New releases, recommendations and system messages")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "List notifications")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched successfully",
                notificationService.getMyNotifications(pageable)));
    }

    @Operation(summary = "Unread notification count", description = "Used for the app badge")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success("Unread count fetched successfully",
                Map.of("unread", notificationService.getUnreadCount())));
    }

    @Operation(summary = "Mark one notification as read")
    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read",
                notificationService.markRead(id)));
    }

    @Operation(summary = "Mark every notification as read")
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        int updated = notificationService.markAllRead();
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read",
                Map.of("updated", updated)));
    }
}
