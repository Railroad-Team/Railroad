package dev.railroadide.railroad.ide.language.impl;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.completion.JavaCompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.diagnostics.JavaDiagnosticsProvider;
import dev.railroadide.railroad.ide.language.BaseLanguageSupport;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.language.LanguageFeatureFactory;
import dev.railroadide.railroad.ide.language.ProjectDiagnosticsFeatureFactory;
import dev.railroadide.railroad.ide.language.impl.index.JavaAnalysisContextProvider;
import dev.railroadide.railroad.ide.language.impl.index.JavaLanguageIndexContextContributor;
import dev.railroadide.railroad.ide.language.impl.index.JavaProjectLanguageIndexer;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.ide.language.index.LanguageIndexContextContributor;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexPersistence;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexer;
import dev.railroadide.railroad.ide.signature.JdtJavaSignatureHelpProvider;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticPersistence;
import dev.railroadide.railroad.ide.syntaxhighlighting.TreeSitterJavaSyntaxHighlighting;
import dev.railroadide.railroad.ide.ui.JavaCodeEditorPane;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorConfig;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.Set;

public final class JavaLanguageSupport extends BaseLanguageSupport {
    public static final String LANGUAGE_ID = "java";
    private static final JavaAnalysisContextProvider ANALYSIS_CONTEXT_PROVIDER = new JavaAnalysisContextProvider();

    public JavaLanguageSupport() {
        super(LANGUAGE_ID, "Java", Set.of("java"));
    }

    @Override
    public EditorOpenView open(Project project, Path file) {
        var editorPane = new JavaCodeEditorPane(
            project,
            file,
            CodeEditorConfig.fromLanguageSupport(project, file, this,
                TreeSitterJavaSyntaxHighlighting::computeHighlighting));
        return new EditorOpenView(editorPane, editorPane, languageId());
    }

    @Override
    public LanguageFeatureFactory<DiagnosticsProvider> diagnosticsFactory() {
        return (project, _path) -> new JavaDiagnosticsProvider(project);
    }

    @Override
    public ProjectDiagnosticsFeatureFactory<DiagnosticsProvider> projectDiagnosticsFactory() {
        return (context, _) -> new JavaDiagnosticsProvider(context);
    }

    @Override
    public LanguageFeatureFactory<CompletionProvider> completionFactory() {
        return JavaCompletionProvider::new;
    }

    @Override
    public LanguageFeatureFactory<SignatureHelpProvider> signatureHelpFactory() {
        return (project, file) -> new JdtJavaSignatureHelpProvider(file, JavaCodeEditorPane.resolveSystemModules());
    }

    @Override
    public ProjectLanguageIndexer<?, ?> createIndexer() {
        return new JavaProjectLanguageIndexer();
    }

    @Override
    public ProjectLanguageIndexPersistence<?> createPersistence() {
        return new JavaProjectSemanticPersistence();
    }

    @Override
    public @NonNull LanguageIndexContextContributor createIndexContextContributor() {
        return new JavaLanguageIndexContextContributor();
    }

    @Override
    public void warmAdditionalIndexes(ProjectIndexContext context) {
        ANALYSIS_CONTEXT_PROVIDER.index(context);
    }

    public static JavaAnalysisContextProvider analysisContextProvider() {
        return ANALYSIS_CONTEXT_PROVIDER;
    }
}
