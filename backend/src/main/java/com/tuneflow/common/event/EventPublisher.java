package com.tuneflow.common.event;

/**
 * Transport-agnostic event publisher.
 *
 * <p>Two implementations ship with TuneFlow: {@code NoopEventPublisher} (local development, logs
 * and fans out to in-process listeners) and {@code KafkaEventPublisher} (production, publishes to
 * the {@code tuneflow.events} topic). Selecting the implementation is a configuration change, not a
 * code change - this is what keeps the modular monolith extractable into microservices later.
 */
public interface EventPublisher {

    void publish(DomainEvent event);

    boolean isEnabled();
}
