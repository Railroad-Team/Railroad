package dev.railroadide.railroad.ide.language.index;

import java.nio.file.Path;
import java.util.List;

public interface LanguageIndexContext {
    String languageId();

    List<Path> sourceRoots();

    List<Path> generatedRoots();

    List<Path> dependencyRoots();
}
