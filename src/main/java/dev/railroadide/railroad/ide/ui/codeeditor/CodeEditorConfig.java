package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.language.LanguageCapabilities;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.provider.api.CodeActionProvider;
import dev.railroadide.railroad.ide.sst.provider.api.NavigationProvider;
import dev.railroadide.railroad.ide.sst.provider.api.RenameProvider;
import dev.railroadide.railroad.ide.sst.provider.api.SstCapabilities;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import dev.railroadide.railroad.ide.sst.provider.api.LanguageSession;

/**
 * Collects a language identifier and optional language services for a code editor.
 *
 * @param languageId identifier of the document language
 * @param completionProvider completion service, or null to disable completion
 * @param diagnosticsProvider diagnostics service, or null when unavailable
 * @param signatureHelpProvider signature help service, or null when unavailable
 * @param navigationProvider navigation service, or null when unavailable
 * @param renameProvider rename service, or null when unavailable
 * @param codeActionProvider code action service, or null when unavailable
 * @param highlightingProvider syntax style provider, or null when unavailable
 * @param providerLifetime cancellation token invalidating providers and their unpublished results on unload
 */
public record CodeEditorConfig(
    String languageId,
    @Nullable CompletionProvider completionProvider,
    @Nullable DiagnosticsProvider<?> diagnosticsProvider,
    @Nullable SignatureHelpProvider signatureHelpProvider,
    @Nullable NavigationProvider navigationProvider,
    @Nullable RenameProvider renameProvider,
    @Nullable CodeActionProvider codeActionProvider,
    @Nullable SyntaxHighlightingProvider highlightingProvider,
    CancellationToken providerLifetime
) {
    /**
     * Creates configuration for directly owned providers with no external unload lifetime, rename or code action
     * service.
     *
     * @param languageId identifier of the document language
     * @param completionProvider completion service, or null to disable completion
     * @param diagnosticsProvider diagnostics service, or null when unavailable
     * @param navigationProvider navigation service, or null when unavailable
     * @param signatureHelpProvider signature help service, or null when unavailable
     * @param highlightingProvider syntax style provider, or null when unavailable
     */
    public CodeEditorConfig(
        String languageId,
        @Nullable CompletionProvider completionProvider,
        @Nullable DiagnosticsProvider<?> diagnosticsProvider,
        @Nullable SignatureHelpProvider signatureHelpProvider,
        @Nullable NavigationProvider navigationProvider,
        @Nullable SyntaxHighlightingProvider highlightingProvider
    ) {
        this(languageId, completionProvider, diagnosticsProvider, signatureHelpProvider, navigationProvider, null,
            null, highlightingProvider, CancellationToken.none());
    }

    /**
     * Creates configuration without rename or code action services.
     *
     * @param languageId identifier of the document language
     * @param completionProvider completion service, or null to disable completion
     * @param diagnosticsProvider diagnostics service, or null when unavailable
     * @param signatureHelpProvider signature help service, or null when unavailable
     * @param navigationProvider navigation service, or null when unavailable
     * @param highlightingProvider syntax style provider, or null when unavailable
     * @param providerLifetime external lifetime shared with captured requests
     */
    public CodeEditorConfig(
        String languageId,
        @Nullable CompletionProvider completionProvider,
        @Nullable DiagnosticsProvider<?> diagnosticsProvider,
        @Nullable SignatureHelpProvider signatureHelpProvider,
        @Nullable NavigationProvider navigationProvider,
        @Nullable SyntaxHighlightingProvider highlightingProvider,
        CancellationToken providerLifetime
    ) {
        this(languageId, completionProvider, diagnosticsProvider, signatureHelpProvider, navigationProvider, null,
            null, highlightingProvider, providerLifetime);
    }

    /**
     * Creates editor configuration with a non-null language identifier and optional providers.
     *
     * @param languageId identifier of the document language
     * @param completionProvider completion service, or null to disable completion
     * @param diagnosticsProvider diagnostics service, or null when unavailable
     * @param signatureHelpProvider signature help service, or null when unavailable
     * @param navigationProvider navigation service, or null when unavailable
     * @param renameProvider rename service, or null when unavailable
     * @param codeActionProvider code action service, or null when unavailable
     * @param highlightingProvider syntax style provider, or null when unavailable
     * @param providerLifetime external lifetime shared with captured requests
     */
    public CodeEditorConfig {
        Objects.requireNonNull(languageId, "languageId");
        Objects.requireNonNull(providerLifetime, "providerLifetime");
    }

    /**
     * Obtains optional providers from the language's owned project session.
     * Converts the path to an absolute, normalized file URI.
     * Use {@link #fromDocument} when the caller already has a document URI.
     *
     * @param project project whose files and workspace are being displayed
     * @param filePath document file for which the view or services are created
     * @param languageSupport registered contribution whose session should be used
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
     * Resolves session providers for a physical or virtual document without opening an editor.
     *
     * @param project project whose files and workspace are being displayed
     * @param documentUri document for which services are created
     * @param languageSupport registered contribution whose session should be used
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

        LanguageSession session = LanguageSupportRegistry.runtime().session(project, languageSupport.id(), "");
        CompletionProvider completionProvider = session.capability(SstCapabilities.COMPLETION).orElse(null);
        DiagnosticsProvider<?> diagnosticsProvider = session.capability(SstCapabilities.DIAGNOSTICS).orElse(null);
        SignatureHelpProvider signatureHelpProvider = session.capability(SstCapabilities.SIGNATURE_HELP).orElse(null);
        NavigationProvider navigationProvider = session.capability(SstCapabilities.NAVIGATION).orElse(null);
        RenameProvider renameProvider = session.capability(SstCapabilities.RENAME).orElse(null);
        CodeActionProvider codeActionProvider = session.capability(SstCapabilities.CODE_ACTIONS).orElse(null);

        if (highlightingProvider == null) {
            highlightingProvider = session.capability(LanguageCapabilities.HIGHLIGHTING).orElse(null);
        }

        return new CodeEditorConfig(
            languageSupport.languageId(),
            completionProvider,
            diagnosticsProvider,
            signatureHelpProvider,
            navigationProvider,
            renameProvider,
            codeActionProvider,
            highlightingProvider,
            session.cancellationToken());
    }
}
