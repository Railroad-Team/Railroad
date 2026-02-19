package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.railroad.plugin.spi.dto.Project;

@FunctionalInterface
public interface RunExecutor {
    RunExecutor NO_OP = (project, configuration) -> {
    };

    void execute(Project project, RunConfiguration<?> configuration);
}
