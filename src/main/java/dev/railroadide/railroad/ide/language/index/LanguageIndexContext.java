package dev.railroadide.railroad.ide.language.index;

import java.nio.file.Path;
import java.util.List;

/**
 * Supplies language-specific source and dependency roots for project indexing.
 */
public interface LanguageIndexContext {
    /**
     * Returns the stable identifier used to associate features and indexes with a language.
     *
     * @return the language identifier
     */
    String languageId();

    /**
     * Returns roots containing source files to index.
     *
     * @return the source root paths
     */
    List<Path> sourceRoots();

    /**
     * Returns roots containing generated source files to index.
     *
     * @return the generated source root paths
     */
    List<Path> generatedRoots();

    /**
     * Returns dependency locations available to language analysis.
     *
     * @return the dependency root paths
     */
    List<Path> dependencyRoots();
}
