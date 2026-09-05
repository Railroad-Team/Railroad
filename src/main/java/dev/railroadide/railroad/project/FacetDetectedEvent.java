package dev.railroadide.railroad.project;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.event.Event;
import dev.railroadide.railroad.project.facet.Facet;

/**
 * Event that is fired when a facet is detected in a project.
 *
 * @param project The project in which the facet was detected.
 * @param facet The detected facet.
 */
public record FacetDetectedEvent(Project project, Facet<?> facet) implements Event {
}
