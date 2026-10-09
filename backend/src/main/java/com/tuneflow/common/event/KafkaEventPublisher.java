package com.tuneflow.common.event;

import com.tuneflow.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka-backed publisher (docs section 23). Playback/analytics events are written asynchronously
 * with the song id as the partition key, so all events for one song stay ordered.
 *
 * <p>Failure policy: Kafka is an analytics/notification transport, not the system of record. If the
 * broker is unreachable the request still succeeds; the event is logged and dispatched in-process
 * so history-like listeners can still run.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tuneflow.events.publisher", havingValue = "kafka")
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    private final AppProperties properties;
    private final ApplicationEventPublisher localPublisher;

    @Override
    public void publish(DomainEvent event) {
        if (!isEnabled()) {
            localPublisher.publishEvent(event);
            return;
        }
        String key = event.songId() != null
                ? event.songId().toString()
                : (event.userId() != null ? event.userId().toString() : event.type());
        CompletableFuture.runAsync(() -> {
            try {
                kafkaTemplate.send(properties.events().topic(), key, event)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("Kafka publish failed for {}: {}", event.type(), ex.getMessage());
                                localPublisher.publishEvent(event);
                            } else if (log.isDebugEnabled()) {
                                log.debug("Published {} to {}", event.type(), properties.events().topic());
                            }
                        });
            } catch (RuntimeException ex) {
                log.warn("Kafka unavailable for {}: {}", event.type(), ex.getMessage());
                localPublisher.publishEvent(event);
            }
        });
    }

    @Override
    public boolean isEnabled() {
        return properties.events().isKafka();
    }
}
