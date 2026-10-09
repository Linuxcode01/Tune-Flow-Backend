package com.tuneflow.notification.consumer;

import com.tuneflow.common.event.DomainEvent;
import com.tuneflow.notification.service.NewReleaseFanoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * In-process new-release listener, active when {@code tuneflow.events.publisher=noop} (local
 * development, tests). Production uses the Kafka consumer instead; both delegate to
 * {@link NewReleaseFanoutService}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tuneflow.events.publisher", havingValue = "noop", matchIfMissing = true)
@RequiredArgsConstructor
public class NewReleaseNotificationListener {

    private final NewReleaseFanoutService fanoutService;

    @Async
    @EventListener
    public void onSongCreated(DomainEvent event) {
        try {
            fanoutService.handle(event);
        } catch (RuntimeException ex) {
            log.warn("New-release fan-out failed for {}: {}", event.type(), ex.getMessage());
        }
    }
}
