package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.language.index.LanguageIndexContext;

import java.nio.file.Path;
import java.util.List;

public record BasicLanguageIndexContext(
    String languageId,
    List<Path> sourceRoots,
    List<Path> generatedRoots,
    List<Path> dependencyRoots) implements LanguageIndexContext {
}
