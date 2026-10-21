package dev.railroadide.railroad.ide.language.index;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Builds language-specific project indexes and applies incremental file updates.
 *
 * @param <I> the project index type
 * @param <F> the per-file index type
 */
public interface ProjectLanguageIndexer<I extends ProjectLanguageIndex<F>, F extends LanguageFileIndex> {
    /**
     * Returns the stable identifier used to associate features and indexes with a language.
     *
     * @return the language identifier
     */
    String languageId();

    /**
     * Checks whether the supplied file is supported.
     *
     * @param file the source file to process
     * @return {@code true} if this language supports the file
     */
    boolean supports(Path file);

    /**
     * Builds a project index from the supplied source files and indexing context.
     *
     * @param context the project indexing context
     * @param sourceFiles the source files to index
     * @return the new project index
     */
    I build(ProjectIndexContext context, Collection<Path> sourceFiles);

    /**
     * Indexes one source file using the supplied content and project context.
     *
     * @param context the project indexing context
     * @param sourceFile the source file path
     * @param sourceContent the source text to analyze
     * @return the file index entry
     */
    F indexFile(ProjectIndexContext context, Path sourceFile, String sourceContent);

    /**
     * Produces a project index with the supplied file entry added or replaced.
     *
     * @param index the project index
     * @param sourceFile the source file path
     * @param indexedFile the replacement file index entry
     * @return the project index containing the updated file
     */
    I withUpdatedFile(I index, Path sourceFile, F indexedFile);

    /**
     * Produces a project index without the specified source file.
     *
     * @param index the project index
     * @param sourceFile the source file path
     * @return the project index with the file removed
     */
    I withRemovedFile(I index, Path sourceFile);
}
