package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.language.index.LanguageIndexContext;

import java.nio.file.Path;
import java.util.List;

public record JavaLanguageIndexContext(
    List<Path> sourceRoots,
    List<Path> generatedRoots,
    List<Path> dependencyRoots,
    List<Path> classpathRoots,
    List<Path> modulePathRoots,
    Path jdkHome) implements LanguageIndexContext {
    @Override
    public String languageId() {
        return "java";
    }
}
