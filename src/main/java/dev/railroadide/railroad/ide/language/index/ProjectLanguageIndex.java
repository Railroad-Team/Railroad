package dev.railroadide.railroad.ide.language.index;

import java.nio.file.Path;

public interface ProjectLanguageIndex<F extends LanguageFileIndex> {
     String languageId();

     F getFileIndex(Path path);
}
