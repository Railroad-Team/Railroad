package dev.railroadide.railroad.ide.completion;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticCompletionEngine;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Completion provider backed by the SST semantic pipeline.
 */
public record JavaCompletionProvider(Project project, Path filePath) implements CompletionProvider {
    public JavaCompletionProvider {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(filePath, "filePath");
    }

    @Override
    public @Nullable CompletionResult compute(String document, int triggerAt) {
        JavaProjectSemanticIndex projectIndex =
            Services.PROJECT_LANGUAGE_INDEX_SERVICE.indexTyped(project.getPath(), JavaLanguageSupport.LANGUAGE_ID);
        return JavaSemanticCompletionEngine.compute(document, triggerAt, projectIndex);
    }
}
