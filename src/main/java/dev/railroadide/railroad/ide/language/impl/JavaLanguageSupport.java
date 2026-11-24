package dev.railroadide.railroad.ide.language.impl;

import dev.railroadide.railroad.ide.completion.JavaCompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.JavaDiagnosticsProvider;
import dev.railroadide.railroad.ide.language.BaseLanguageSupport;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.language.LanguageCapabilities;
import dev.railroadide.railroad.ide.language.impl.index.JavaAnalysisContextProvider;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySources;
import dev.railroadide.railroad.ide.language.impl.index.JavaLanguageIndexContextContributor;
import dev.railroadide.railroad.ide.language.impl.index.JavaProjectLanguageIndexer;
import dev.railroadide.railroad.ide.signature.JavaSignatureHelpProvider;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSstServices;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticPersistence;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.syntaxhighlighting.TreeSitterJavaSyntaxHighlighting;
import dev.railroadide.railroad.ide.ui.JavaCodeEditorPane;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorConfig;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.ShutdownHooks;

import java.nio.file.Path;
import java.util.Set;

/**
 * Integrates Java editing, diagnostics, completion, signature help, and semantic project indexes.
 */
public final class JavaLanguageSupport extends BaseLanguageSupport {
    /**
     * Stable identifier for Java language features and indexes.
     */
    public static final String LANGUAGE_ID = "java";
    private static final JavaAnalysisContextProvider ANALYSIS_CONTEXT_PROVIDER = new JavaAnalysisContextProvider();

    static {
        ShutdownHooks.addHook(ANALYSIS_CONTEXT_PROVIDER::close);
    }

    /**
     * Creates support for Java source files with the standard Java language identifier.
     */
    public JavaLanguageSupport() {
        super(LANGUAGE_ID, "Java", Set.of("java"));
    }

    @Override
    public EditorOpenView open(Project project, DocumentId documentId, Path file) {
        var editorPane = new JavaCodeEditorPane(
            project,
            documentId,
            file,
            CodeEditorConfig.fromLanguageSupport(project, file, this,
                TreeSitterJavaSyntaxHighlighting::computeHighlighting));
        return new EditorOpenView(editorPane, editorPane, languageId());
    }

    @Override
    public Set<CapabilityKey<?>> supportedCapabilities() {
        return Set.of(SstCapabilities.PARSING, SstCapabilities.SEMANTICS, SstCapabilities.INDEX_EXTRACTION,
            SstCapabilities.INDEX_QUERY, SstCapabilities.DIAGNOSTICS,
            SstCapabilities.COMPLETION, SstCapabilities.SIGNATURE_HELP, LanguageCapabilities.INDEXER,
            LanguageCapabilities.PERSISTENCE, LanguageCapabilities.INDEX_CONTEXT, LanguageCapabilities.INDEX_WARMUP,
            LanguageCapabilities.HIGHLIGHTING, SstCapabilities.NAVIGATION, SstCapabilities.RENAME,
            SstCapabilities.CODE_ACTIONS);
    }

    @Override
    public LanguageSession openSession(LanguageSessionContext context) {
        Project project = context.services().find(LanguageCapabilities.PROJECT).orElseThrow();
        var semantics = new JavaSstServices(JavaLibrarySources.shared(), ANALYSIS_CONTEXT_PROVIDER::index);
        return new LanguageSession(CapabilityRegistry.builder()
            .add(LanguageCapabilities.HIGHLIGHTING, TreeSitterJavaSyntaxHighlighting::computeHighlighting)
            .add(SstCapabilities.DIAGNOSTICS, new JavaDiagnosticsProvider(project, null, semantics::capture))
            .add(SstCapabilities.COMPLETION, new JavaCompletionProvider(project, null, semantics::capture))
            .add(SstCapabilities.SIGNATURE_HELP, new JavaSignatureHelpProvider(semantics::capture))
            .add(SstCapabilities.PARSING, request -> new FeatureResult<>(request, JavaSyntaxParser.parse(request)))
            .add(SstCapabilities.SEMANTICS, semantics)
            .add(SstCapabilities.INDEX_EXTRACTION, semantics)
            .add(SstCapabilities.INDEX_QUERY, semantics)
            .add(LanguageCapabilities.INDEXER, new JavaProjectLanguageIndexer())
            .add(LanguageCapabilities.PERSISTENCE, new JavaProjectSemanticPersistence())
            .add(LanguageCapabilities.INDEX_CONTEXT, new JavaLanguageIndexContextContributor())
            .add(LanguageCapabilities.INDEX_WARMUP, ANALYSIS_CONTEXT_PROVIDER::index)
            .add(SstCapabilities.NAVIGATION, semantics)
            .add(SstCapabilities.RENAME, semantics)
            .add(SstCapabilities.CODE_ACTIONS, semantics)
            .build());
    }

    /**
     * Returns the shared provider combining Java project, library, and JDK indexes.
     *
     * @return the shared analysis context provider
     */
    public static JavaAnalysisContextProvider analysisContextProvider() {
        return ANALYSIS_CONTEXT_PROVIDER;
    }
}
