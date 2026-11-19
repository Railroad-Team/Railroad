package dev.railroadide.railroad.ide.language.index;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Consumer;

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
     * Loads an index with cooperative controls. Expensive adapters must forward the check into their scans.
     *
     * @param projectRoot project root directory
     * @param indexedFiles source files expected in the cache
     * @param activityCheck owning request or index-operation activity check
     * @return current cached index, or null when absent or stale
     */
    default @Nullable I loadIfCurrent(Path projectRoot, Collection<Path> indexedFiles, Runnable activityCheck) {
        activityCheck.run();
        I result = loadIfCurrent(projectRoot, indexedFiles);
        activityCheck.run();
        return result;
    }

    /**
     * Persists the supplied project index.
     *
     * @param projectRoot the project root directory
     * @param index the project index
     */
    void save(Path projectRoot, I index);

    /**
     * Prepares a disposable snapshot, then submits only its final atomic replacement to the ownership guard.
     * Adapters without guarded replacement skip caching rather than commit stale data or block project close.
     *
     * @param projectRoot project root
     * @param index published index
     * @param commit invokes the replacement only while its session and generation remain current
     */
    default void save(Path projectRoot, I index, Consumer<Runnable> commit) {
        // Persistence is optional. Legacy adapters can opt into guarded replacement independently.
    }

    /**
     * Prepares a guarded replacement with cooperative controls throughout expensive serialization.
     *
     * @param projectRoot project root directory
     * @param index complete index to persist
     * @param commit ownership guard for the final atomic replacement
     * @param activityCheck owning request or index-operation activity check
     */
    default void save(Path projectRoot, I index, Consumer<Runnable> commit, Runnable activityCheck) {
        activityCheck.run();
        save(projectRoot, index, commit);
        activityCheck.run();
    }

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

    /**
     * Deletes only through a short ownership-checked final commit; unsupported adapters skip deletion.
     *
     * @param projectRoot the project root directory
     * @param commit invokes deletion only while its session and generation remain current
     */
    default void delete(Path projectRoot, Consumer<Runnable> commit) {
    }
}
