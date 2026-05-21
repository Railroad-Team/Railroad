package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexPersistence;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexer;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public interface LanguageSupport {
    String languageId();

    String displayName();

    Set<String> fileExtensions();

    default boolean supports(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileExtensions().stream().anyMatch(ext -> name.endsWith("." + ext));
    }

    default boolean supports(File file) {
        return supports(file.toPath());
    }

    EditorOpenView open(Project project, Path file);

    default boolean isTextBased() {
        return true;
    }

    default boolean supportsDiagnostics() {
        return diagnosticsFactory() != null;
    }

    default boolean supportsCompletion() {
        return completionFactory() != null;
    }

    default boolean supportsSignatureHelp() {
        return signatureHelpFactory() != null;
    }

    @Nullable
    LanguageFeatureFactory<DiagnosticsProvider> diagnosticsFactory();

    @Nullable
    LanguageFeatureFactory<CompletionProvider> completionFactory();

    @Nullable
    LanguageFeatureFactory<SignatureHelpProvider> signatureHelpFactory();

    @Nullable
    default ProjectLanguageIndexer<?, ?> createIndexer() {
        return null;
    }

    @Nullable
    default ProjectLanguageIndexPersistence<?> createPersistence() {
        return null;
    }
}
