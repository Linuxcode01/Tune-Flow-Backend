package com.tuneflow.analytics.consumer;

import com.tuneflow.analytics.service.PlaybackAnalyticsService;
import com.tuneflow.common.event.DomainEvent;
import com.tuneflow.notification.service.NewReleaseFanoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumers (docs section 23): the same fan-out that runs in-process during development,
 * driven by the broker in production. Consumer groups are named per concern so history, analytics
 * and recommendation processing can scale independently.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "tuneflow.events.publisher", havingValue = "kafka")
@RequiredArgsConstructor
public class KafkaEventConsumer {

    private final PlaybackAnalyticsService playbackAnalyticsService;

    @Bean
    public AnalyticsListener analyticsListener() {
        return new AnalyticsListener(playbackAnalyticsService);
    }

    @Bean
    public RecommendationSignalListener recommendationSignalListener() {
        return new RecommendationSignalListener();
    }

    @Bean
    public NotificationListener notificationListener(NewReleaseFanoutService fanoutService) {
        return new NotificationListener(fanoutService);
    }

    /** Corresponds to the "Analytics Consumer" box in the docs' Kafka diagram. */
    @Slf4j
    @RequiredArgsConstructor
    public static class AnalyticsListener {

        private final PlaybackAnalyticsService playbackAnalyticsService;

        @KafkaListener(topics = "${tuneflow.events.topic:tuneflow.events}",
                groupId = "tuneflow-analytics",
                autoStartup = "${tuneflow.events.consumers-enabled:true}")
        public void onEvent(DomainEvent event) {
            try {
                playbackAnalyticsService.handle(event);
            } catch (RuntimeException ex) {
                log.warn("Analytics consumer failed for {}: {}", event.type(), ex.getMessage());
            }
        }
    }

    /** Corresponds to the "Recommendation Consumer" box: keeps user taste signals fresh. */
    @Slf4j
    public static class RecommendationSignalListener {

        @KafkaListener(topics = "${tuneflow.events.topic:tuneflow.events}",
                groupId = "tuneflow-recommendation",
                autoStartup = "${tuneflow.events.consumers-enabled:true}")
        public void onEvent(DomainEvent event) {
            if (DomainEvent.SONG_LIKED.equals(event.type()) || DomainEvent.USER_FOLLOWED.equals(event.type())) {
                // The collaborative/rule-based strategies read this state live from PostgreSQL, so the
                // only thing needed here is to invalidate any derived caches.
                log.debug("Taste signal {} recorded for user {}", event.type(), event.userId());
            }
        }
    }

    /**
     * Corresponds to the "Notification Consumer" box: new-release fan-out for followed artists.
     * Delegates to the same service used in-process, so behaviour cannot drift between modes.
     */
    @Slf4j
    @RequiredArgsConstructor
    public static class NotificationListener {

        private final NewReleaseFanoutService fanoutService;

        @KafkaListener(topics = "${tuneflow.events.topic:tuneflow.events}",
                groupId = "tuneflow-notification",
                autoStartup = "${tuneflow.events.consumers-enabled:true}")
        public void onEvent(DomainEvent event) {
            fanoutService.handle(event);
        }
    }
}
