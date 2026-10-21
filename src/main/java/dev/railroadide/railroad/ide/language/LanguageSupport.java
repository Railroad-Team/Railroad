package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.language.index.LanguageIndexContextContributor;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexPersistence;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexer;
import dev.railroadide.railroad.ide.language.ProjectDiagnosticsFeatureFactory;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Defines file recognition, editor creation, optional language features, and project indexing integration.
 */
public interface LanguageSupport {
    /**
     * Returns the stable identifier used to associate features and indexes with a language.
     *
     * @return the language identifier
     */
    String languageId();

    /**
     * Returns the language name presented to users.
     *
     * @return the display name
     */
    String displayName();

    /**
     * Returns the extensions recognized by the default filename matcher.
     *
     * @return supported lowercase extensions without leading dots
     */
    Set<String> fileExtensions();

    /**
     * Checks whether the supplied file is supported.
     *
     * @param path the file path to inspect
     * @return {@code true} if this language supports the file
     */
    default boolean supports(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileExtensions().stream().anyMatch(ext -> name.endsWith("." + ext));
    }

    /**
     * Checks whether the supplied file is supported.
     *
     * @param file the source file to process
     * @return {@code true} if this language supports the file
     */
    default boolean supports(File file) {
        return supports(file.toPath());
    }

    /**
     * Creates the editor view for a file in the supplied project.
     *
     * @param project the project whose files and configuration are used
     * @param file the source file to process
     * @return the opened content and optional active text editor
     */
    EditorOpenView open(Project project, Path file);

    /**
     * Reports whether this language uses text editing; the default is true.
     *
     * @return {@code true} for text-based editor content
     */
    default boolean isTextBased() {
        return true;
    }

    /**
     * Checks whether a file diagnostics factory is available.
     *
     * @return {@code true} if diagnostics are supported
     */
    default boolean supportsDiagnostics() {
        return diagnosticsFactory() != null;
    }

    /**
     * Checks whether a completion factory is available.
     *
     * @return {@code true} if completion is supported
     */
    default boolean supportsCompletion() {
        return completionFactory() != null;
    }

    /**
     * Checks whether a signature help factory is available.
     *
     * @return {@code true} if signature help is supported
     */
    default boolean supportsSignatureHelp() {
        return signatureHelpFactory() != null;
    }

    /**
     * Returns a factory for file diagnostics.
     *
     * @return the feature factory, or {@code null} if unsupported
     */
    @Nullable
    LanguageFeatureFactory<DiagnosticsProvider> diagnosticsFactory();

    /**
     * Returns a factory for diagnostics sharing a project context.
     *
     * @return the feature factory, or {@code null} if unsupported
     */
    @Nullable
    default ProjectDiagnosticsFeatureFactory<DiagnosticsProvider> projectDiagnosticsFactory() {
        return null;
    }

    /**
     * Returns a factory for completion.
     *
     * @return the feature factory, or {@code null} if unsupported
     */
    @Nullable
    LanguageFeatureFactory<CompletionProvider> completionFactory();

    /**
     * Returns a factory for signature help.
     *
     * @return the feature factory, or {@code null} if unsupported
     */
    @Nullable
    LanguageFeatureFactory<SignatureHelpProvider> signatureHelpFactory();

    /**
     * Creates an indexer for project files in this language.
     *
     * @return the indexer, or {@code null} if indexing is unsupported
     */
    @Nullable
    default ProjectLanguageIndexer<?, ?> createIndexer() {
        return null;
    }

    /**
     * Creates persistence support for this language's project index.
     *
     * @return the persistence adapter, or {@code null} if unsupported
     */
    @Nullable
    default ProjectLanguageIndexPersistence<?> createPersistence() {
        return null;
    }

    /**
     * Creates a contributor that resolves language-specific project indexing inputs.
     *
     * @return the context contributor, or {@code null} if unsupported
     */
    @Nullable
    default LanguageIndexContextContributor createIndexContextContributor() {
        return null;
    }

    /**
     * Warms auxiliary indexes needed by this language; the default implementation does nothing.
     *
     * @param context the project indexing context
     */
    default void warmAdditionalIndexes(ProjectIndexContext context) {
    }
}
