package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.event.Event;
import java.util.Objects;

/**
 * Announces a change to project build or analysis configuration.
 *
 * @param project owner of the changed configuration
 */
public record ProjectConfigurationChangedEvent(Project project) implements Event {
    /**
     * Creates a configuration invalidation event.
     *
     * @param project configuration owner
     */
    public ProjectConfigurationChangedEvent {
        Objects.requireNonNull(project, "project");
    }
}
