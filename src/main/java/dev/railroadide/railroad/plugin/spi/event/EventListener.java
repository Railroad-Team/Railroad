package dev.railroadide.railroad.plugin.spi.event;

/**
 * An interface for event listeners that handle events of type T.
 *
 * @param <T> the type of event this listener handles
 */
public interface EventListener<T extends Event> {
    /**
     * This method is called when an event of type T is published.
     *
     * @param event the event that was published
     */
    void handle(T event);
}
