package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

public record CodeEditorConfig(
    String languageId,
    @Nullable CompletionProvider completionProvider,
    @Nullable DiagnosticsProvider diagnosticsProvider,
    @Nullable SignatureHelpProvider signatureHelpProvider,
    @Nullable SyntaxHighlightingProvider highlightingProvider
) {
    public CodeEditorConfig {
        Objects.requireNonNull(languageId, "languageId");
    }

    public static CodeEditorConfig fromLanguageSupport(
        Project project,
        Path filePath,
        LanguageSupport languageSupport,
        @Nullable SyntaxHighlightingProvider highlightingProvider
    ) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(languageSupport, "languageSupport");

        CompletionProvider completionProvider = languageSupport.completionFactory() == null
            ? null
            : languageSupport.completionFactory().create(project, filePath);
        DiagnosticsProvider diagnosticsProvider = languageSupport.diagnosticsFactory() == null
            ? null
            : languageSupport.diagnosticsFactory().create(project, filePath);
        SignatureHelpProvider signatureHelpProvider = languageSupport.signatureHelpFactory() == null
            ? null
            : languageSupport.signatureHelpFactory().create(project, filePath);

        return new CodeEditorConfig(
            languageSupport.languageId(),
            completionProvider,
            diagnosticsProvider,
            signatureHelpProvider,
            highlightingProvider
        );
    }
}
