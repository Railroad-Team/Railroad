package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.event.Event;

import java.util.Objects;

/** Published when a workspace mode becomes available or unavailable. */
public record WorkspaceModeAvailabilityChangedEvent(String modeId, boolean available) implements Event {
    public WorkspaceModeAvailabilityChangedEvent {
        Objects.requireNonNull(modeId, "Mode ID cannot be null");
    }
}
