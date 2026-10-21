package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
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
     * Converts the path to an absolute, normalized file URI before invoking the factories.
     * Use {@link #fromDocument} when the caller already has a document URI.
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
        return fromDocument(project, DocumentUri.fromPath(Objects.requireNonNull(filePath, "filePath")),
            languageSupport, highlightingProvider);
    }

    /**
     * Creates providers for a physical or virtual document without opening an editor.
     *
     * @param project project whose files and workspace are being displayed
     * @param documentUri document for which services are created
     * @param languageSupport language support whose provider factories should be used
     * @param highlightingProvider syntax style provider, or null when unavailable
     * @return configuration with null entries for unavailable services
     */
    public static CodeEditorConfig fromDocument(
        Project project,
        DocumentUri documentUri,
        LanguageSupport languageSupport,
        @Nullable SyntaxHighlightingProvider highlightingProvider
    ) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(documentUri, "documentUri");
        Objects.requireNonNull(languageSupport, "languageSupport");

        var completionFactory = languageSupport.completionFactory();
        var diagnosticsFactory = languageSupport.diagnosticsFactory();
        var signatureHelpFactory = languageSupport.signatureHelpFactory();
        CompletionProvider completionProvider = completionFactory == null
            ? null
            : completionFactory.create(project, documentUri);
        DiagnosticsProvider diagnosticsProvider = diagnosticsFactory == null
            ? null
            : diagnosticsFactory.create(project, documentUri);
        SignatureHelpProvider signatureHelpProvider = signatureHelpFactory == null
            ? null
            : signatureHelpFactory.create(project, documentUri);

        return new CodeEditorConfig(
            languageSupport.languageId(),
            completionProvider,
            diagnosticsProvider,
            signatureHelpProvider,
            highlightingProvider);
    }
}
