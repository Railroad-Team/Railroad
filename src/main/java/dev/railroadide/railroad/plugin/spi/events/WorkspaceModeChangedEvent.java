package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.event.Event;

import java.util.Objects;

/**
 * Published after the active workspace mode changes.
 *
 * @param previousModeId ID of the previously active mode
 * @param currentModeId ID of the newly active mode
 */
public record WorkspaceModeChangedEvent(String previousModeId, String currentModeId) implements Event {
    /**
     * Creates a workspace mode change notification with nonnull mode IDs.
     *
     * @param previousModeId ID of the previously active mode
     * @param currentModeId ID of the newly active mode
     * @throws NullPointerException if a mode ID is null
     */
    public WorkspaceModeChangedEvent {
        Objects.requireNonNull(previousModeId, "Previous mode ID cannot be null");
        Objects.requireNonNull(currentModeId, "Current mode ID cannot be null");
    }
}
