package dev.railroadide.railroad.ide.language.index;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Loads, stores, and invalidates persisted language-specific project indexes.
 *
 * @param <I> the project index type
 */
public interface ProjectLanguageIndexPersistence<I extends ProjectLanguageIndex<?>> {
    /**
     * Returns the stable identifier used to associate features and indexes with a language.
     *
     * @return the language identifier
     */
    String languageId();

    /**
     * Loads a persisted index when its cached inputs are still current.
     *
     * @param projectRoot the project root directory
     * @return the current cached index, or {@code null} when absent or stale
     */
    @Nullable
    I loadIfCurrent(Path projectRoot);

    /**
     * Loads a persisted index when its cached inputs are still current. The default implementation delegates to
     * the project-root-only overload.
     *
     * @param projectRoot the project root directory
     * @param indexedFiles the source files expected in the cached index
     * @return the current cached index, or {@code null} when absent or stale
     */
    default @Nullable I loadIfCurrent(Path projectRoot, Collection<Path> indexedFiles) {
        return loadIfCurrent(projectRoot);
    }

    /**
     * Persists the supplied project index.
     *
     * @param projectRoot the project root directory
     * @param index the project index
     */
    void save(Path projectRoot, I index);

    /**
     * Persists an index after one file changed. The default implementation saves the complete index.
     *
     * @param projectRoot the project root directory
     * @param index the project index
     * @param file the source file to process
     */
    default void updateFile(Path projectRoot, I index, Path file) {
        save(projectRoot, index);
    }

    /**
     * Persists an index after one file was removed. The default implementation saves the complete index.
     *
     * @param projectRoot the project root directory
     * @param index the project index
     * @param file the source file to process
     */
    default void removeFile(Path projectRoot, I index, Path file) {
        save(projectRoot, index);
    }

    /**
     * Deletes persisted index data for the project root.
     *
     * @param projectRoot the project root directory
     */
    void delete(Path projectRoot);
}
