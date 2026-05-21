package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public abstract class BaseLanguageSupport implements LanguageSupport {
    private final String languageId;
    private final String displayName;
    private final Set<String> extensions;

    protected BaseLanguageSupport(String languageId, String displayName, Set<String> extensions) {
        this.languageId = languageId;
        this.displayName = displayName;
        this.extensions = Set.copyOf(extensions);
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
        return extensions;
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
