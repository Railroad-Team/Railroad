package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.event.Event;

import java.util.Objects;

/** Published after the active workspace mode changes. */
public record WorkspaceModeChangedEvent(String previousModeId, String currentModeId) implements Event {
    public WorkspaceModeChangedEvent {
        Objects.requireNonNull(previousModeId, "Previous mode ID cannot be null");
        Objects.requireNonNull(currentModeId, "Current mode ID cannot be null");
    }
}
