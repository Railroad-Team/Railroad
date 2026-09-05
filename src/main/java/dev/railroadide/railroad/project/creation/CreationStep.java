package dev.railroadide.railroad.project.creation;

import dev.railroadide.railroad.project.ProjectContext;

/**
 * A named operation executed as part of a project creation pipeline.
 */
public interface CreationStep {
    /**
     * Returns the identifier used to locate this step in a registry.
     *
     * @return the step identifier
     */
    String id();

    /**
     * Returns the translation key describing this step to the user.
     *
     * @return the step's translation key
     */
    String translationKey();

    /**
     * Performs this step using the project's configuration and shared state.
     *
     * @param ctx the context of the project being created
     * @param reporter the destination for progress and status updates
     * @throws Exception if the step cannot be completed
     */
    void run(ProjectContext ctx, ProgressReporter reporter) throws Exception;
}
