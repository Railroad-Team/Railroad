package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.language.index.LanguageIndexContext;

import java.nio.file.Path;
import java.util.List;

/**
 * Stores the source, generated-source, and dependency roots for a language.
 *
 * @param languageId the stable language identifier
 * @param sourceRoots the source directories to index
 * @param generatedRoots the generated source directories to index
 * @param dependencyRoots the dependency locations used during analysis
 */
public record BasicLanguageIndexContext(
    String languageId,
    List<Path> sourceRoots,
    List<Path> generatedRoots,
    List<Path> dependencyRoots
) implements LanguageIndexContext {
}
