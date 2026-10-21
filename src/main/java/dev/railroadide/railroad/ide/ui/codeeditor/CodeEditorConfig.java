package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Collects a language identifier and optional language services for a code editor.
 *
 * @param languageId identifier of the document language
 * @param completionProvider completion service, or null to disable completion
 * @param diagnosticsProvider diagnostics service, or null when unavailable
 * @param signatureHelpProvider signature help service, or null when unavailable
 * @param highlightingProvider syntax style provider, or null when unavailable
 */
public record CodeEditorConfig(
    String languageId,
    @Nullable CompletionProvider completionProvider,
    @Nullable DiagnosticsProvider diagnosticsProvider,
    @Nullable SignatureHelpProvider signatureHelpProvider,
    @Nullable SyntaxHighlightingProvider highlightingProvider
) {
    /**
     * Creates editor configuration with a non-null language identifier and optional providers.
     *
     * @param languageId identifier of the document language
     * @param completionProvider completion service, or null to disable completion
     * @param diagnosticsProvider diagnostics service, or null when unavailable
     * @param signatureHelpProvider signature help service, or null when unavailable
     * @param highlightingProvider syntax style provider, or null when unavailable
     */
    public CodeEditorConfig {
        Objects.requireNonNull(languageId, "languageId");
    }

    /**
     * Creates document-specific providers from the available language-support factories.
     *
     * @param project project whose files and workspace are being displayed
     * @param filePath document file for which the view or services are created
     * @param languageSupport language support whose provider factories should be used
     * @param highlightingProvider syntax style provider, or null when unavailable
     * @return configuration with null entries for unavailable services
     */
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
            highlightingProvider);
    }
}
