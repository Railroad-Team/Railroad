package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.railroad.project.RailroadProject;

@FunctionalInterface
public interface RunExecutor {
    RunExecutor NO_OP = (project, configuration) -> {
    };

    void execute(RailroadProject project, RunConfiguration configuration);
}
