package dev.railroadide.railroad.ide.language.index;

import java.nio.file.Path;
import java.util.Collection;

public interface ProjectLanguageIndexer<
    I extends ProjectLanguageIndex<F>,
    F extends LanguageFileIndex
> {
    String languageId();

    boolean supports(Path file);

    I build(Path projectRoot, Collection<Path> sourceFiles);

    F indexFile(Path sourceFile, String sourceContent);

    I withUpdatedFile(I index, Path sourceFile, F indexedFile);

    I withRemovedFile(I index, Path sourceFile);

}
