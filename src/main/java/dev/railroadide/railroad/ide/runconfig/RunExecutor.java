package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.railroad.plugin.spi.dto.Project;

/**
 * Executes an action for a run configuration within a project.
 */
@FunctionalInterface
public interface RunExecutor {
    /**
     * Executor that accepts a project and configuration without performing an action.
     */
    RunExecutor NO_OP = (project, configuration) -> {
    };

    /**
     * Performs this executor's action on a configuration in the supplied project.
     *
     * @param project the project owning the configuration
     * @param configuration the run configuration to operate on
     */
    void execute(Project project, RunConfiguration<?> configuration);
}
