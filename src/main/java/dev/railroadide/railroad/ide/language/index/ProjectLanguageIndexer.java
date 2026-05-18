package dev.railroadide.railroad.ide.language.index;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;

public interface ProjectLanguageIndexer<
    I extends ProjectLanguageIndex<F>,
    F extends LanguageFileIndex
> {
    static ProjectLanguageIndexer<?, ?> noop() {
        return new ProjectLanguageIndexer<>() {
            @Override
            public String languageId() {
                return "noop";
            }

            @Override
            public boolean supports(Path file) {
                return false;
            }

            @Override
            public ProjectLanguageIndex<LanguageFileIndex> build(Path projectRoot, Collection<Path> sourceFiles) {
                return null;
            }

            @Override
            public LanguageFileIndex indexFile(Path sourceFile, String sourceContent) {
                return null;
            }

            @Override
            public ProjectLanguageIndex<LanguageFileIndex> withUpdatedFile(ProjectLanguageIndex<LanguageFileIndex> index, Path sourceFile, LanguageFileIndex indexedFile) {
                return null;
            }

            @Override
            public ProjectLanguageIndex<LanguageFileIndex> withRemovedFile(ProjectLanguageIndex<LanguageFileIndex> index, Path sourceFile) {
                return null;
            }
        };
    }

    String languageId();

    boolean supports(Path file);

    I build(Path projectRoot, Collection<Path> sourceFiles);

    F indexFile(Path sourceFile, String sourceContent);

    I withUpdatedFile(I index, Path sourceFile, F indexedFile);

    I withRemovedFile(I index, Path sourceFile);

    @Nullable
    default I emptyIndex() {
        return null;
    }
}
