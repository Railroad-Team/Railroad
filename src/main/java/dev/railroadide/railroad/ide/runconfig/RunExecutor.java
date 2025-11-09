package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.railroad.project.Project;

@FunctionalInterface
public interface RunExecutor {
    RunExecutor NO_OP = (project, configuration) -> {
    };

    void execute(Project project, RunConfiguration configuration);
}
