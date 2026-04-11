package dev.railroadide.railroad.ide.completion;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticCompletionEngine;
import dev.railroadide.railroad.ide.sst.project.ProjectSemanticIndex;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Completion provider backed by the SST semantic pipeline.
 */
public record SstCompletionProvider(Project project, Path filePath) implements CompletionProvider {
    public SstCompletionProvider {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(filePath, "filePath");
    }

    @Override
    public @Nullable CompletionResult compute(String document, int triggerAt) {
        ProjectSemanticIndex projectIndex = Services.PROJECT_SEMANTIC_SERVICE.current(project);
        return JavaSemanticCompletionEngine.compute(document, triggerAt, projectIndex);
    }
}
