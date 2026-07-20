package dev.railroadide.railroad.ide.sst.document.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Workspace-owned allocator and location registry for document identities.
 * <p>
 * The registry separates logical identity from path spelling. Existing physical files
 * are resolved through their real path, and already registered files are compared with
 * {@link Files#isSameFile(Path, Path)} so aliases such as symbolic or hard links converge
 * on one ID. Missing paths use an absolute normalized spelling until a physical file is
 * available or the owner explicitly rebinds the document.
 * <p>
 * Virtual, generated, in-memory, text, and binary documents may be resolved directly by
 * {@link DocumentUri} or use {@link #create()} before they have a location. Their owner
 * may associate a URI or physical path later.
 * <p>
 * All operations are thread-safe. IDs are never recycled. {@link #release(DocumentId)}
 * only forgets registry associations; existing snapshots and analysis results may safely
 * retain the immutable ID. Registry persistence, when required by a workspace, should
 * store the canonical {@link DocumentId#toString()} value and restore associations with
 * {@link #associate(DocumentId, DocumentUri)}.
 */
public final class DocumentIdentityRegistry {
    private final Map<Path, DocumentId> idsByPath = new HashMap<>();
    private final Map<DocumentUri, DocumentId> idsByUri = new HashMap<>();

    /**
     * Allocates an unbound identity for a new logical document.
     *
     * @return a fresh identity
     */
    public DocumentId create() {
        return DocumentId.create();
    }

    /**
     * Returns the identity associated with {@code uri}, allocating one when necessary.
     * File URIs receive the same physical-alias handling as {@link #getOrCreate(Path)}.
     *
     * @param uri physical or virtual document URI
     * @return stable identity associated with the URI
     */
    public synchronized DocumentId getOrCreate(DocumentUri uri) {
        uri = Objects.requireNonNull(uri, "uri");
        Optional<Path> filePath = uri.filePath();
        if (filePath.isPresent())
            return getOrCreate(filePath.get());

        return idsByUri.computeIfAbsent(uri, ignored -> create());
    }

    /**
     * Returns the identity associated with {@code path}, allocating one when necessary.
     * Equivalent spellings of the same existing physical file return the same identity.
     *
     * @param path physical document path
     * @return stable identity associated with the path
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws UncheckedIOException if an existing path cannot be resolved or compared
     */
    public synchronized DocumentId getOrCreate(Path path) {
        Path normalized = normalize(path);
        Path resolved = resolve(normalized);
        DocumentId existing = findRegistered(normalized, resolved);
        if (existing != null)
            return existing;

        existing = findEquivalentPhysicalFile(resolved);
        if (existing != null) {
            registerSpellings(existing, normalized, resolved);
            return existing;
        }

        DocumentId created = create();
        registerSpellings(created, normalized, resolved);
        return created;
    }

    /**
     * Finds an existing association without allocating an identity.
     *
     * @param path physical document path
     * @return associated identity, or empty when the path is unknown
     */
    public synchronized Optional<DocumentId> find(Path path) {
        Path normalized = normalize(path);
        Path resolved = resolve(normalized);
        DocumentId existing = findRegistered(normalized, resolved);
        if (existing != null)
            return Optional.of(existing);

        existing = findEquivalentPhysicalFile(resolved);
        if (existing != null)
            registerSpellings(existing, normalized, resolved);

        return Optional.ofNullable(existing);
    }

    /**
     * Finds an existing physical or virtual URI association without allocating an ID.
     *
     * @param uri document URI
     * @return associated identity, or empty when the URI is unknown
     */
    public synchronized Optional<DocumentId> find(DocumentUri uri) {
        uri = Objects.requireNonNull(uri, "uri");
        Optional<Path> filePath = uri.filePath();
        return filePath.isPresent() ? find(filePath.get()) : Optional.ofNullable(idsByUri.get(uri));
    }

    /**
     * Associates an existing identity with a physical path.
     *
     * @param documentId identity owned by this workspace
     * @param path       physical document path
     * @throws IllegalStateException if the path is already associated with another ID
     */
    public synchronized void associate(DocumentId documentId, Path path) {
        documentId = Objects.requireNonNull(documentId, "documentId");
        Path normalized = normalize(path);
        Path resolved = resolve(normalized);
        DocumentId existing = findRegistered(normalized, resolved);
        if (existing == null)
            existing = findEquivalentPhysicalFile(resolved);

        if (existing != null && !existing.equals(documentId))
            throw new IllegalStateException("Document path is already associated with another identity: " + path);

        registerSpellings(documentId, normalized, resolved);
    }

    /**
     * Associates an existing identity with a physical or virtual URI.
     *
     * @param documentId identity owned by this workspace
     * @param uri current document URI
     * @throws IllegalStateException if the URI is already associated with another ID
     */
    public synchronized void associate(DocumentId documentId, DocumentUri uri) {
        documentId = Objects.requireNonNull(documentId, "documentId");
        uri = Objects.requireNonNull(uri, "uri");
        Optional<Path> filePath = uri.filePath();
        if (filePath.isPresent()) {
            associate(documentId, filePath.get());
            return;
        }

        DocumentId existing = idsByUri.get(uri);
        if (existing != null && !existing.equals(documentId))
            throw new IllegalStateException("Document URI is already associated with another identity: " + uri);

        idsByUri.put(uri, documentId);
    }

    /**
     * Moves the physical association for a document while preserving its identity.
     * The filesystem move itself remains the caller's responsibility.
     *
     * @param documentId   identity being moved
     * @param previousPath previous physical path
     * @param newPath      new physical path
     * @throws IllegalStateException if the old association is missing or belongs to a
     *                               different document, or the new path belongs to another document
     */
    public synchronized void rebind(DocumentId documentId, Path previousPath, Path newPath) {
        rebind(
            documentId,
            DocumentUri.fromPath(previousPath),
            DocumentUri.fromPath(newPath)
        );
    }

    /**
     * Changes a document's physical or virtual URI while preserving its identity. Any
     * aliases registered for the previous location are forgotten. Moving filesystem
     * content remains the caller's responsibility.
     *
     * @param documentId identity being moved
     * @param previousUri previous document URI
     * @param newUri new document URI
     * @throws IllegalStateException if the previous URI is not owned by the supplied ID,
     * or the new URI belongs to another document
     */
    public synchronized void rebind(DocumentId documentId, DocumentUri previousUri, DocumentUri newUri) {
        documentId = Objects.requireNonNull(documentId, "documentId");
        previousUri = Objects.requireNonNull(previousUri, "previousUri");
        newUri = Objects.requireNonNull(newUri, "newUri");
        DocumentId previous = find(previousUri).orElse(null);
        if (!documentId.equals(previous)) {
            throw new IllegalStateException(
                "Previous URI is not associated with the supplied identity: " + previousUri);
        }

        DocumentId existing = find(newUri).orElse(null);
        if (existing != null && !documentId.equals(existing)) {
            throw new IllegalStateException(
                "New URI is already associated with another identity: " + newUri);
        }

        removeAssociations(documentId);
        associate(documentId, newUri);
    }

    /**
     * Forgets every path and URI association for an identity. The ID remains valid for any
     * immutable snapshots or results that already reference it and is never reused.
     *
     * @param documentId identity whose associations should be removed
     */
    public synchronized void release(DocumentId documentId) {
        documentId = Objects.requireNonNull(documentId, "documentId");
        removeAssociations(documentId);
    }

    private void removeAssociations(DocumentId documentId) {
        idsByPath.values().removeIf(documentId::equals);
        idsByUri.values().removeIf(documentId::equals);
    }

    private DocumentId findEquivalentPhysicalFile(Path path) {
        if (Files.notExists(path))
            return null;

        for (Map.Entry<Path, DocumentId> entry : idsByPath.entrySet()) {
            Path registered = entry.getKey();
            if (Files.notExists(registered))
                continue;

            try {
                if (Files.isSameFile(path, registered))
                    return entry.getValue();
            } catch (IOException exception) {
                throw new UncheckedIOException(
                    "Failed to compare document paths " + path + " and " + registered,
                    exception);
            }
        }

        return null;
    }

    private DocumentId findRegistered(Path normalized, Path resolved) {
        DocumentId existing = idsByPath.get(normalized);
        return existing != null ? existing : idsByPath.get(resolved);
    }

    private void registerSpellings(DocumentId documentId, Path normalized, Path resolved) {
        idsByPath.put(normalized, documentId);
        idsByPath.put(resolved, documentId);
        idsByUri.put(DocumentUri.fromPath(normalized), documentId);
        idsByUri.put(DocumentUri.fromPath(resolved), documentId);
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static Path resolve(Path path) {
        try {
            return path.toRealPath();
        } catch (NoSuchFileException exception) {
            return path;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to resolve document path " + path, exception);
        }
    }
}
