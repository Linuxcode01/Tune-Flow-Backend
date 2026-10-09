package com.tuneflow.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Default publisher: dispatches events to in-process {@code @EventListener}s without requiring a
 * Kafka broker. Used for local development and for the H2 test profile.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tuneflow.events.publisher", havingValue = "noop", matchIfMissing = true)
@RequiredArgsConstructor
public class NoopEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(DomainEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("Dispatching in-process event {} for user {}", event.type(), event.userId());
        }
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
