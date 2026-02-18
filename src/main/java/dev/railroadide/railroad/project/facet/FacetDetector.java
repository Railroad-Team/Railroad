package dev.railroadide.railroad.project.facet;

import dev.railroadide.railroad.project.RailroadProject;

import java.util.Optional;

/**
 * Interface for detecting facets in a project based on a given path.
 * Implementations analyze a project directory and return a detected facet if applicable.
 *
 * @param <D> the type of data associated with the detected facet
 */
@FunctionalInterface
public interface FacetDetector<D> {
    /**
     * Detects a facet based on the provided path.
     *
     * @param project the project context for detection
     * @return an {@link Optional} containing the detected facet if found, or an {@link Optional#empty} if no facet is detected
     */
    Optional<Facet<D>> detect(RailroadProject project);
}
