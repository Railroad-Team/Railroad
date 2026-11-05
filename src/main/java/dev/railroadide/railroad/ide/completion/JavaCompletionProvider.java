package dev.railroadide.railroad.ide.completion;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticCompletionEngine;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;

/**
 * Completion provider backed by the SST semantic pipeline.
 *
 * @param project project whose editor features or debug state are managed
 * @param filePath optional legacy file context; completion uses the supplied snapshot
 */
public record JavaCompletionProvider(Project project, @Nullable Path filePath) implements CompletionProvider {
    @Override
    public @Nullable CompletionResult compute(
        WorkspaceSnapshot workspace,
        DocumentId id,
        int triggerAt
    ) {
        if (!(workspace.document(id).orElseThrow() instanceof TextDocumentSnapshot text)
            || !JavaLanguageSupport.LANGUAGE_ID.equals(text.languageId()))
            return null;
        return JavaSemanticCompletionEngine.compute(text.text(), triggerAt,
            JavaLanguageSupport.analysisContextProvider().index(workspace, id));
    }
    /**
     * Creates a completion provider whose document location is supplied by each snapshot.
     *
     * @param project project supplying the Java symbol index
     */
    public JavaCompletionProvider(Project project) {
        this(project, null);
    }

    /**
     * Creates a completion provider, retaining the file context for compatibility with existing factories.
     *
     * @param project project whose editor features or debug state are managed
     * @param filePath optional legacy file context; completion uses the supplied snapshot
     */
    public JavaCompletionProvider {
        Objects.requireNonNull(project, "project");
    }

    @Override
    public @Nullable CompletionResult compute(DocumentSnapshot snapshot, int triggerAt) {
        if (!(snapshot instanceof TextDocumentSnapshot textSnapshot)
            || !JavaLanguageSupport.LANGUAGE_ID.equals(textSnapshot.languageId()))
            return null;

        JavaProjectSemanticIndex projectIndex = Services.PROJECT_LANGUAGE_INDEX_SERVICE.indexTyped(project,
            JavaLanguageSupport.LANGUAGE_ID);
        return JavaSemanticCompletionEngine.compute(textSnapshot.text(), triggerAt, projectIndex);
    }
}
