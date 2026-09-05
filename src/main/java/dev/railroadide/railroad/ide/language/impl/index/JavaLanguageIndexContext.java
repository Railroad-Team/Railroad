package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.language.index.LanguageIndexContext;

import java.nio.file.Path;
import java.util.List;

/**
 * Stores Java source roots, dependency paths, and the JDK used for semantic indexing.
 *
 * @param sourceRoots the source directories to index
 * @param generatedRoots the generated source directories to index
 * @param dependencyRoots the dependency locations used during analysis
 * @param classpathRoots the classpath directories and archives
 * @param modulePathRoots the module-path locations
 * @param jdkHome the JDK home, or {@code null} to use the running JDK
 */
public record JavaLanguageIndexContext(
    List<Path> sourceRoots,
    List<Path> generatedRoots,
    List<Path> dependencyRoots,
    List<Path> classpathRoots,
    List<Path> modulePathRoots,
    Path jdkHome
) implements LanguageIndexContext {
    @Override
    public String languageId() {
        return "java";
    }
}
