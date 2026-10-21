package dev.railroadide.railroad.ide.sst.document.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable address of a physical or virtual document.
 * <p>
 * A URI describes where a document is currently addressed; it is not the document's
 * stable logical identity. Moving or renaming a document can therefore change its URI
 * while retaining the same {@link DocumentId}.
 * <p>
 * Both hierarchical URIs such as {@code file:///workspace/Main.java} and opaque URIs
 * such as {@code jar:file:///library.jar!/pkg/Type.class} are supported. Application and
 * language providers may define schemes for generated, decompiled, archive-entry, or
 * other virtual documents. Instances are immutable and thread-safe.
 *
 * @param value absolute URI value
 */
public record DocumentUri(URI value) {
    /**
     * Creates a document address, requiring an absolute URI with a nonblank resource identifier.
     *
     * @param value the absolute URI identifying the resource
     */
    public DocumentUri {
        value = Objects.requireNonNull(value, "value");
        if (!value.isAbsolute() || value.getScheme() == null || value.getScheme().isBlank())
            throw new IllegalArgumentException("Document URI must be absolute: " + value);
        if (value.getRawSchemeSpecificPart() == null || value.getRawSchemeSpecificPart().isBlank())
            throw new IllegalArgumentException("Document URI must identify a resource: " + value);
    }

    /**
     * Parses an absolute physical or virtual document URI.
     *
     * @param value URI text
     * @return parsed document URI
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is blank, malformed, or relative
     */
    public static DocumentUri parse(String value) {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty())
            throw new IllegalArgumentException("value cannot be blank");

        return new DocumentUri(URI.create(value));
    }

    /**
     * Creates a file URI from an absolute normalized spelling of {@code path}. Physical
     * alias resolution remains the responsibility of the document identity owner.
     *
     * @param path physical document path
     * @return absolute file document URI
     */
    public static DocumentUri fromPath(Path path) {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        return new DocumentUri(path.toUri());
    }

    /**
     * Creates a hierarchical URI for a virtual document. The supplied path is encoded as
     * a URI path and need not refer to the local filesystem.
     *
     * @param scheme provider-owned URI scheme
     * @param path non-empty virtual path
     * @return virtual document URI
     */
    public static DocumentUri virtual(String scheme, String path) {
        scheme = requireComponent(scheme, "scheme").toLowerCase(Locale.ROOT);
        path = requireComponent(path, "path");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        try {
            return new DocumentUri(new URI(scheme, null, path, null));
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid virtual document URI", exception);
        }
    }

    /**
     * Creates the anonymous in-memory location used by compatibility entry points that
     * have a document identity but no owner-supplied URI.
     *
     * @param documentId logical document identity
     * @return unique in-memory document URI
     */
    public static DocumentUri inMemory(DocumentId documentId) {
        return virtual("memory", Objects.requireNonNull(documentId, "documentId").toString());
    }

    /**
     * Returns whether this URI uses the {@code file} scheme.
     *
     * @return {@code true} for physical file URIs
     */
    public boolean isFile() {
        return "file".equalsIgnoreCase(value.getScheme());
    }

    /**
     * Converts a physical file URI back to a path.
     *
     * @return physical path, or empty for non-file document schemes
     * @throws IllegalArgumentException if a malformed file URI cannot be represented by
     *             the current filesystem provider
     */
    public Optional<Path> filePath() {
        return isFile() ? Optional.of(Path.of(value)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value.toString();
    }

    private static String requireComponent(String value, String name) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty())
            throw new IllegalArgumentException(name + " cannot be blank");
        return value;
    }
}
