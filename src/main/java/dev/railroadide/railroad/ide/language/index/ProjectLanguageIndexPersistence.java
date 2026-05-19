package dev.railroadide.railroad.ide.language.index;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public interface ProjectLanguageIndexPersistence<I extends ProjectLanguageIndex<?>> {
    String languageId();

    @Nullable
    I loadIfCurrent(Path projectRoot);

    void save(Path projectRoot, I index);

    void delete(Path projectRoot);
}
