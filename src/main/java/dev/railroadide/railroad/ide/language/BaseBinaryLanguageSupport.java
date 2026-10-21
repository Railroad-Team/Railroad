package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Provides language identity and extensions for binary editors without text language features.
 */
public abstract class BaseBinaryLanguageSupport implements LanguageSupport {
    private final String languageId;
    private final String displayName;
    private final Set<String> fileExtensions;

    /**
     * Initializes binary language metadata and copies the supported extensions.
     *
     * @param languageId the stable language identifier
     * @param displayName the language name shown to users
     * @param fileExtensions the recognized file extensions without leading dots
     */
    public BaseBinaryLanguageSupport(String languageId, String displayName, String... fileExtensions) {
        this.languageId = languageId;
        this.displayName = displayName;
        this.fileExtensions = Set.of(fileExtensions);
    }

    @Override
    public String languageId() {
        return languageId;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public Set<String> fileExtensions() {
        return fileExtensions;
    }

    @Override
    public boolean isTextBased() {
        return false;
    }

    @Override
    public @Nullable LanguageFeatureFactory<DiagnosticsProvider> diagnosticsFactory() {
        return null;
    }

    @Override
    public @Nullable LanguageFeatureFactory<CompletionProvider> completionFactory() {
        return null;
    }

    @Override
    public @Nullable LanguageFeatureFactory<SignatureHelpProvider> signatureHelpFactory() {
        return null;
    }
}
