package dev.railroadide.railroad.ide.language.index;

import java.nio.file.Path;

/**
 * Identifies the source file represented by a language-specific index entry.
 */
public interface LanguageFileIndex {
    /**
     * Returns the path represented by this file index.
     *
     * @return the indexed source file path
     */
    Path path();
}
