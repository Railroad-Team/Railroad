package dev.railroadide.railroad.project.creation;

import dev.railroadide.railroad.project.ProjectType;

/**
 * Contributes creation steps for supported project types.
 */
public interface CreationStepProvider {
    /**
     * Adds this provider's steps to the supplied registry.
     *
     * @param services the services available to the steps
     * @param registry the registry to populate
     */
    void provideSteps(ProjectServiceRegistry services, CreationStepRegistry registry);

    /**
     * Tests whether this provider can supply steps for a project type.
     *
     * @param type the project type to test
     * @return {@code true} if the project type is supported
     */
    boolean supports(ProjectType type);
}
