package io.github.cocosip.polystore.spring;

/**
 * Consumes storage operation events. In a Spring application the auto-configuration wires this to
 * {@code ApplicationEventPublisher#publishEvent}; manual assembly can supply any consumer.
 */
@FunctionalInterface
public interface StorageEventPublisher {

    /**
     * Publishes the given event.
     *
     * @param event a {@code FileSavedEvent} or {@code FileDeletedEvent}
     */
    void publish(Object event);
}
