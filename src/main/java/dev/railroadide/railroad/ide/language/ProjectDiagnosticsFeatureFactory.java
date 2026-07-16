package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.diagnostics.ProjectDiagnosticsContext;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Path;

@FunctionalInterface
public interface ProjectDiagnosticsFeatureFactory<T> {
    T create(ProjectDiagnosticsContext context, Path file);
}
