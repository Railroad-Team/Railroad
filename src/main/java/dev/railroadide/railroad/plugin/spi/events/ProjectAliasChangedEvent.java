package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.event.Event;

/**
 * Represents an event that is triggered when a project's alias is changed.
 * This event contains the project whose alias has changed, the old alias, and the new alias.
 */
public record ProjectAliasChangedEvent(Project project, String oldAlias, String newAlias) implements Event {
    /**
     * Creates a new ProjectAliasChangedEvent.
     *
     * @param project  the project whose alias has changed
     * @param oldAlias the previous alias of the project
     * @param newAlias the new alias of the project
     */
    public ProjectAliasChangedEvent {
        if (project == null) {
            throw new IllegalArgumentException("Project cannot be null");
        }
        if (oldAlias == null || newAlias == null) {
            throw new IllegalArgumentException("Aliases cannot be null");
        }
    }
}
