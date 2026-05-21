package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Path;

@FunctionalInterface
public interface LanguageFeatureFactory<T> {
    T create(Project project, Path file);
}
