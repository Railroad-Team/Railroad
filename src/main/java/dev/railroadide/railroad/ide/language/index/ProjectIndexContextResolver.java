package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.plugin.spi.dto.Project;

public interface ProjectIndexContextResolver {
    ProjectIndexContext resolve(Project project);
}
