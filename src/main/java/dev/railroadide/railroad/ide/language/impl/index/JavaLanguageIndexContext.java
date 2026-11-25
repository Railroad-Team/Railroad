package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.language.index.LanguageIndexContext;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;

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
 * @param gradleConfiguration imported Gradle module settings, or {@code null} for other projects
 */
public record JavaLanguageIndexContext(
    List<Path> sourceRoots,
    List<Path> generatedRoots,
    List<Path> dependencyRoots,
    List<Path> classpathRoots,
    List<Path> modulePathRoots,
    Path jdkHome,
    WorkspaceConfigurationSnapshot gradleConfiguration
) implements LanguageIndexContext {
    /**
     * Creates a context for a project with one set of binary roots.
     *
     * @param sourceRoots the source directories to index
     * @param generatedRoots the generated source directories to index
     * @param dependencyRoots the dependency locations used during analysis
     * @param classpathRoots the classpath directories and archives
     * @param modulePathRoots the module-path locations
     * @param jdkHome the JDK home, or {@code null} to use the running JDK
     */
    public JavaLanguageIndexContext(
        List<Path> sourceRoots,
        List<Path> generatedRoots,
        List<Path> dependencyRoots,
        List<Path> classpathRoots,
        List<Path> modulePathRoots,
        Path jdkHome
    ) {
        this(sourceRoots, generatedRoots, dependencyRoots, classpathRoots, modulePathRoots, jdkHome, null);
    }

    @Override
    public String languageId() {
        return "java";
    }
}
