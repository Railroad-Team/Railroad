package dev.railroadide.railroad.ide.language.index;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;

public interface ProjectLanguageIndexPersistence<I extends ProjectLanguageIndex<?>> {
    String languageId();

    @Nullable
    I loadIfCurrent(Path projectRoot);

    default @Nullable I loadIfCurrent(Path projectRoot, Collection<Path> indexedFiles) {
        return loadIfCurrent(projectRoot);
    }

    void save(Path projectRoot, I index);

    /** Persists an index after one file changed. Implementations may update only that entry. */
    default void updateFile(Path projectRoot, I index, Path file) {
        save(projectRoot, index);
    }

    /** Persists an index after one file was removed. Implementations may update only the manifest. */
    default void removeFile(Path projectRoot, I index, Path file) {
        save(projectRoot, index);
    }

    void delete(Path projectRoot);
}
