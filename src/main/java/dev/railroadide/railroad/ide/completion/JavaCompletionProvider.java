package dev.railroadide.railroad.ide.completion;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticCompletionEngine;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import dev.railroadide.railroad.ide.sst.request.api.RequestContext;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;

/**
 * Completion provider backed by the SST semantic pipeline.
 *
 * @param project project whose editor features or debug state are managed
 * @param filePath optional legacy file context; completion uses the supplied snapshot
 * @param analysis captures Java analysis for the request workspace and cancellation controls
 */
public record JavaCompletionProvider(
    Project project,
    @Nullable Path filePath,
    Function<RequestContext, JavaWorkspaceAnalysis> analysis
) implements CompletionProvider {
    @Override
    public boolean shouldTrigger(String insertedText) {
        return insertedText.endsWith(".");
    }

    /**
     * Creates a completion provider using the shared Java analysis context.
     *
     * @param project project owning the language services
     * @param filePath optional file context; requests use their captured document snapshot
     */
    public JavaCompletionProvider(Project project, @Nullable Path filePath) {
        this(project, filePath, JavaLanguageSupport.analysisContextProvider()::index);
    }

    @Override
    public FeatureResult<CompletionResult> compute(FeatureRequestContext context, int triggerAt) {
        context.checkActive();
        if (!(context.documentSnapshot() instanceof TextDocumentSnapshot text)
            || !JavaLanguageSupport.LANGUAGE_ID.equals(text.languageId()))
            return new FeatureResult<>(context, null);
        CompletionResult result = JavaSemanticCompletionEngine.compute(text.text(), triggerAt,
            analysis.apply(context), context::checkActive);
        context.checkActive();
        return new FeatureResult<>(context, result);
    }

    @Override
    public @Nullable CompletionResult compute(
        WorkspaceSnapshot workspace,
        DocumentId id,
        int triggerAt
    ) {
        if (!(workspace.document(id).orElseThrow() instanceof TextDocumentSnapshot text)
            || !JavaLanguageSupport.LANGUAGE_ID.equals(text.languageId()))
            return null;
        return compute(new FeatureRequestContext(workspace, text, CancellationToken.none(), Deadline.none()), triggerAt)
            .value();
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
     * @param analysis captures Java analysis for the request workspace and cancellation controls
     */
    public JavaCompletionProvider {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(analysis, "analysis");
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
