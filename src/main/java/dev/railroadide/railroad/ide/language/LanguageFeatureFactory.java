package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Creates language features for physical or virtual documents in a project.
 * New callers use {@link #createForDocument(Project, DocumentUri)}. The Path method
 * remains the single abstract method for existing implementations and lambdas.
 * URI-aware implementations can be created with {@link #fromUri(BiFunction)}.
 *
 * @param <T> the created feature type
 */
@FunctionalInterface
public interface LanguageFeatureFactory<T> {
    /**
     * Compatibility entry point for existing file-based callers and implementations.
     *
     * @param project the project whose files and configuration are used
     * @param file the source file to process
     * @return the feature instance
     */
    T create(Project project, Path file);

    /**
     * Creates a feature for a physical or virtual document.
     * The default bridges physical documents to existing Path-based implementations;
     * virtual documents require an override or a factory created with {@link #fromUri(BiFunction)}.
     *
     * @param project the project whose files and configuration are used
     * @param documentUri the physical or virtual document location
     * @return the feature instance
     * @throws NullPointerException if the URI is null
     * @throws UnsupportedOperationException if a legacy factory receives a virtual document
     */
    default T createForDocument(Project project, DocumentUri documentUri) {
        Objects.requireNonNull(documentUri, "documentUri");
        Path file = documentUri.filePath().orElseThrow(() -> new UnsupportedOperationException(
            "This Path-based language feature factory does not support virtual document " + documentUri
                + ". Override createForDocument() or use LanguageFeatureFactory.fromUri()."));
        return create(project, file);
    }

    /**
     * Adapts URI-based creation logic to both document and legacy Path callers.
     * Physical paths are converted to absolute, normalized file URIs by {@link DocumentUri#fromPath(Path)}.
     *
     * @param <T> the feature type
     * @param factory creation logic that accepts a document URI directly
     * @return a factory whose document entry point supports physical and virtual URIs
     * @throws NullPointerException if the creation function is null
     */
    static <T> LanguageFeatureFactory<T> fromUri(BiFunction<Project, DocumentUri, T> factory) {
        Objects.requireNonNull(factory, "factory");
        return new LanguageFeatureFactory<>() {
            @Override
            public T create(Project project, Path file) {
                return createForDocument(project, DocumentUri.fromPath(file));
            }

            @Override
            public T createForDocument(Project project, DocumentUri documentUri) {
                return factory.apply(project, Objects.requireNonNull(documentUri, "documentUri"));
            }
        };
    }
}
