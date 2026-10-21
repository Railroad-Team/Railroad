package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.event.Event;

import java.util.Objects;

/**
 * Published when a workspace mode becomes available or unavailable.
 *
 * @param modeId ID of the mode whose availability changed
 * @param available whether the mode can now be activated
 */
public record WorkspaceModeAvailabilityChangedEvent(String modeId, boolean available) implements Event {
    /**
     * Creates a workspace mode change notification with nonnull mode IDs.
     *
     * @param modeId ID of the mode whose availability changed
     * @param available whether the mode can now be activated
     * @throws NullPointerException if a mode ID is null
     */
    public WorkspaceModeAvailabilityChangedEvent {
        Objects.requireNonNull(modeId, "Mode ID cannot be null");
    }
}
