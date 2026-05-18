package dev.railroadide.railroad.ide.language.impl;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.completion.JavaCompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.diagnostics.JavaDiagnosticsProvider;
import dev.railroadide.railroad.ide.language.BaseLanguageSupport;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.language.LanguageFeatureFactory;
import dev.railroadide.railroad.ide.language.impl.index.JavaProjectLanguageIndexer;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexer;
import dev.railroadide.railroad.ide.signature.JdtJavaSignatureHelpProvider;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.ui.JavaCodeEditorPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Path;
import java.util.Set;

public final class JavaLanguageSupport extends BaseLanguageSupport {
    public JavaLanguageSupport() {
        super("java", "Java", Set.of("java"));
    }

    @Override
    public EditorOpenView open(Project project, Path file) {
        var editorPane = new JavaCodeEditorPane(project, file);
        return new EditorOpenView(editorPane, editorPane, languageId());
    }

    @Override
    public LanguageFeatureFactory<DiagnosticsProvider> diagnosticsFactory() {
        return JavaDiagnosticsProvider::new;
    }

    @Override
    public LanguageFeatureFactory<CompletionProvider> completionFactory() {
        return JavaCompletionProvider::new;
    }

    @Override
    public LanguageFeatureFactory<SignatureHelpProvider> signatureHelpFactory() {
        return (project, file) -> new JdtJavaSignatureHelpProvider(file, new String[0]);
    }

    @Override
    public ProjectLanguageIndexer<?, ?> createIndexer() {
        return new JavaProjectLanguageIndexer();
    }
}
