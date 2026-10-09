package com.tuneflow.analytics.consumer;

import com.tuneflow.analytics.service.PlaybackAnalyticsService;
import com.tuneflow.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Analytics consumer for local development (no broker required). Active when
 * {@code tuneflow.events.publisher=noop} - the default outside production.
 *
 * <p>{@code @Async} keeps the analytics work off the HTTP request thread, mirroring the decoupling
 * that Kafka provides in production (docs section 39: "make playback analytics asynchronous").
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tuneflow.events.publisher", havingValue = "noop", matchIfMissing = true)
@RequiredArgsConstructor
public class InProcessAnalyticsConsumer {

    private final PlaybackAnalyticsService playbackAnalyticsService;

    @Async
    @EventListener
    public void onDomainEvent(DomainEvent event) {
        try {
            playbackAnalyticsService.handle(event);
        } catch (RuntimeException ex) {
            // Analytics failures must never surface to the user.
            log.warn("Analytics handling failed for {}: {}", event.type(), ex.getMessage());
        }
    }
}
