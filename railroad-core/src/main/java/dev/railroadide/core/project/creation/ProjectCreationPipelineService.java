package dev.railroadide.core.project.creation;

import dev.railroadide.core.project.ProjectCreationPipeline;
import dev.railroadide.core.project.ProjectType;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for services that manage the creation of projects using a pipeline approach.
 */
public interface ProjectCreationPipelineService {
    /**
     * Creates a new project based on the specified project type and service registry.
     *
     * @param type     The type of the project to be created.
     * @param services The registry of services required for project creation.
     * @return A `ProjectCreationPipeline` instance representing the created project.
     */
    ProjectCreationPipeline createProject(@NotNull ProjectType type, @NotNull ProjectServiceRegistry services);

    /**
     * Adds a new creation step provider to the pipeline service.
     *
     * @param provider The `CreationStepProvider` to be added.
     */
    void addProvider(@NotNull CreationStepProvider provider);

    /**
     * Registers the default creation step providers for the specified project type and service registry.
     *
     * @param registry The registry where the default providers will be registered.
     * @param type     The type of the project for which default providers are registered.
     * @param services The registry of services required for the default providers.
     */
    void registerDefaultProviders(@NotNull CreationStepRegistry registry, @NotNull ProjectType type, @NotNull ProjectServiceRegistry services);
}
