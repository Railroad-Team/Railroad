package dev.railroadide.railroad.ide.language.index;

import java.nio.file.Path;

/**
 * Represents a language-specific project index with per-file entries.
 *
 * @param <F> the per-file index type
 */
public interface ProjectLanguageIndex<F extends LanguageFileIndex> {
    /**
     * Returns the stable identifier used to associate features and indexes with a language.
     *
     * @return the language identifier
     */
    String languageId();

    /**
     * Looks up the index entry for a source file.
     *
     * @param path the file path to inspect
     * @return the file index, or {@code null} if the file is not indexed
     */
    F getFileIndex(Path path);
}
