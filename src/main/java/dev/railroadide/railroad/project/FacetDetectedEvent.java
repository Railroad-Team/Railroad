package dev.railroadide.railroad.project;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.event.Event;
import dev.railroadide.railroad.project.facet.Facet;

public record FacetDetectedEvent(Project project, Facet<?> facet) implements Event {
}
