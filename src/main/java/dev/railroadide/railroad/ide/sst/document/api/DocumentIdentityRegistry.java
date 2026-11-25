package dev.railroadide.railroad.ide.sst.document.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Workspace-owned allocator and location registry for document identities.
 * <p>
 * The registry separates logical identity from path spelling. Existing physical files
 * are resolved through their real path, and already registered files that share a file key
 * (or, where the platform exposes none, a creation time) are compared with
 * {@link Files#isSameFile(Path, Path)} so aliases such as symbolic or hard links converge
 * on one ID. Those keys are refreshed whenever a registered location is looked up, so a
 * hard link to a file that was replaced since its last lookup may receive its own ID. Missing paths use an absolute
 * normalized spelling until a physical file is
 * available or the owner explicitly rebinds the document.
 * <p>
 * Virtual, generated, in-memory, text, and binary documents may be resolved directly by
 * {@link DocumentUri} or use {@link #create()} before they have a location. Their owner
 * may associate a URI or physical path later.
 * <p>
 * All operations are thread-safe. IDs are never recycled, and content versions advance
 * atomically per ID. {@link #release(DocumentId)} forgets registry associations and live
 * version state; existing snapshots and analysis results may safely retain their
 * immutable ID and version. Registry persistence, when required by a workspace, should
 * store both canonical values and restore them with {@link #restoreVersion(DocumentId,
 * DocumentVersion)} and {@link #associate(DocumentId, DocumentUri)}.
 */
public final class DocumentIdentityRegistry {
    private final Map<Path, DocumentId> idsByPath = new HashMap<>();
    private final Map<Path, Object> fileKeysByPath = new HashMap<>();
    private final Map<Object, Set<Path>> pathsByFileKey = new HashMap<>();
    private final Map<DocumentUri, DocumentId> idsByUri = new HashMap<>();
    private final Map<DocumentId, DocumentVersion> versionsById = new HashMap<>();
    private final Set<DocumentId> releasedIds = new HashSet<>();

    /**
     * Allocates an unbound identity for a new logical document.
     *
     * @return a fresh identity
     */
    public synchronized DocumentId create() {
        DocumentId documentId = DocumentId.create();
        versionsById.put(documentId, DocumentVersion.initial());
        return documentId;
    }

    /**
     * Returns the current content version of an identity owned by this registry.
     *
     * @param documentId logical document identity
     * @return current document version
     * @throws IllegalStateException if the identity is not owned by this registry or has
     *             been released
     */
    public synchronized DocumentVersion currentVersion(DocumentId documentId) {
        documentId = Objects.requireNonNull(documentId, "documentId");
        DocumentVersion version = versionsById.get(documentId);
        if (version == null)
            throw new IllegalStateException("Document identity is not owned by this registry: " + documentId);

        return version;
    }

    /**
     * Atomically advances and returns the content version of an owned document.
     *
     * @param documentId logical document identity
     * @return newly allocated version
     * @throws IllegalStateException if the identity is unknown, released, or its version
     *             is exhausted
     */
    public synchronized DocumentVersion advanceVersion(DocumentId documentId) {
        DocumentVersion next = currentVersion(documentId).next();
        versionsById.put(documentId, next);
        return next;
    }

    /**
     * Restores or advances persisted version state. Existing state may stay equal or move
     * forward but can never move backward.
     *
     * @param documentId logical document identity
     * @param version persisted or externally allocated version
     * @throws IllegalArgumentException if {@code version} is earlier than existing state
     */
    public synchronized void restoreVersion(DocumentId documentId, DocumentVersion version) {
        documentId = Objects.requireNonNull(documentId, "documentId");
        version = Objects.requireNonNull(version, "version");
        rejectReleased(documentId);
        DocumentVersion current = versionsById.get(documentId);
        if (current != null && version.isBefore(current))
            throw new IllegalArgumentException(
                "Document version cannot move backward from " + current + " to " + version);

        versionsById.put(documentId, version);
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

        DocumentId documentId = idsByUri.computeIfAbsent(uri, _ -> create());
        ensureVersion(documentId);
        return documentId;
    }

    /**
     * Resolves a stable identity together with the supplied current URI.
     *
     * @param uri physical or virtual document URI
     * @return logical identity and current address
     */
    public synchronized DocumentIdentity identify(DocumentUri uri) {
        uri = Objects.requireNonNull(uri, "uri");
        return new DocumentIdentity(getOrCreate(uri), canonicalUri(uri));
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
        if (existing != null) {
            ensureVersion(existing);
            refreshFileKey(resolved);
            return existing;
        }

        existing = findEquivalentPhysicalFile(resolved);
        if (existing != null) {
            registerSpellings(existing, normalized, resolved);
            ensureVersion(existing);
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
        if (existing != null) {
            refreshFileKey(resolved);
            return Optional.of(existing);
        }

        existing = findEquivalentPhysicalFile(resolved);
        if (existing != null) {
            registerSpellings(existing, normalized, resolved);
        }

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
     * Finds the current URI associated with a document identity.
     *
     * @param id the document identity to look up
     * @return associated URI, or empty when the identity is unknown
     */
    public synchronized Optional<DocumentUri> find(DocumentId id) {
        Objects.requireNonNull(id, "id");

        return idsByUri.entrySet().stream()
            .filter(entry -> entry.getValue().equals(id))
            .findFirst()
            .map(entry -> entry.getKey());
    }

    /**
     * Finds a registered identity together with its current address without allocating.
     *
     * @param uri physical or virtual document URI
     * @return registered logical identity and current address, if known
     */
    public synchronized Optional<DocumentIdentity> findIdentity(DocumentUri uri) {
        uri = Objects.requireNonNull(uri, "uri");
        DocumentUri canonicalUri = canonicalUri(uri);
        return find(canonicalUri).map(documentId -> new DocumentIdentity(documentId, canonicalUri));
    }

    /**
     * Associates an existing identity with a physical path.
     *
     * @param documentId identity owned by this workspace
     * @param path physical document path
     * @throws IllegalStateException if the path is already associated with another ID
     */
    public synchronized void associate(DocumentId documentId, Path path) {
        documentId = Objects.requireNonNull(documentId, "documentId");
        Path normalized = normalize(path);
        Path resolved = resolve(normalized);
        DocumentId existing = findRegistered(normalized, resolved);
        if (existing == null) {
            existing = findEquivalentPhysicalFile(resolved);
        }

        if (existing != null && !existing.equals(documentId))
            throw new IllegalStateException("Document path is already associated with another identity: " + path);

        ensureVersion(documentId);
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

        ensureVersion(documentId);
        idsByUri.put(uri, documentId);
    }

    /**
     * Moves the physical association for a document while preserving its identity.
     * The filesystem move itself remains the caller's responsibility.
     *
     * @param documentId identity being moved
     * @param previousPath previous physical path
     * @param newPath new physical path
     * @throws IllegalStateException if the old association is missing or belongs to a
     *             different document, or the new path belongs to another document
     */
    public synchronized void rebind(DocumentId documentId, Path previousPath, Path newPath) {
        rebind(
            documentId,
            DocumentUri.fromPath(previousPath),
            DocumentUri.fromPath(newPath));
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
     *             or the new URI belongs to another document
     */
    public synchronized void rebind(DocumentId documentId, DocumentUri previousUri, DocumentUri newUri) {
        documentId = Objects.requireNonNull(documentId, "documentId");
        previousUri = Objects.requireNonNull(previousUri, "previousUri");
        newUri = Objects.requireNonNull(newUri, "newUri");
        DocumentId previous = find(previousUri).orElse(null);
        if (!documentId.equals(previous))
            throw new IllegalStateException(
                "Previous URI is not associated with the supplied identity: " + previousUri);

        DocumentId existing = find(newUri).orElse(null);
        if (existing != null && !documentId.equals(existing))
            throw new IllegalStateException("New URI is already associated with another identity: " + newUri);

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
        currentVersion(documentId);
        removeAssociations(documentId);
        versionsById.remove(documentId);
        releasedIds.add(documentId);
    }

    /**
     * Removes every location association of an owned identity while keeping it owned, so that it can later be
     * associated again. Unlike {@link #release(DocumentId)}, this is reversible.
     *
     * @param documentId owned identity to detach from its locations
     * @throws IllegalStateException if the identity is not owned by this registry
     */
    public synchronized void dissociate(DocumentId documentId) {
        currentVersion(documentId);
        removeAssociations(documentId);
    }

    /**
     * Forgets a deleted location of a document that another of its registered locations still reaches, such as one
     * of several hard links to its file. The identity remains with the surviving locations; spellings of the deleted
     * location and other registered locations that no longer exist are forgotten. Nothing changes when no other
     * registered location exists. A file that has since been created at the deleted location does not count as a
     * surviving location.
     *
     * @param documentId identity that was registered at the deleted location
     * @param deleted location whose file was deleted
     * @return a surviving location, preferring a registered spelling over its resolved real path, or empty when no
     *         other registered location exists
     * @throws UncheckedIOException if a location cannot be resolved
     */
    public synchronized Optional<Path> forgetDeletedAlias(DocumentId documentId, Path deleted) {
        Objects.requireNonNull(documentId, "documentId");
        Path normalized = normalize(deleted);
        Path resolved = resolve(normalized);
        Set<Path> forgotten = new HashSet<>();
        List<Path> surviving = new ArrayList<>();
        for (Map.Entry<Path, DocumentId> entry : idsByPath.entrySet()) {
            Path path = entry.getKey();
            if (!entry.getValue().equals(documentId))
                continue;

            if (path.equals(normalized) || Files.notExists(path) || resolve(path).equals(resolved)) {
                forgotten.add(path);
            } else if (Files.exists(path)) {
                surviving.add(path);
            }
        }

        if (surviving.isEmpty())
            return Optional.empty();

        for (Path path : forgotten) {
            removePath(path);
            idsByUri.remove(DocumentUri.fromPath(path), documentId);
        }

        return Optional.of(preferredSpelling(surviving));
    }

    /**
     * Finds another existing location of a document that is currently the same physical file as {@code path} but a
     * different directory entry, such as another hard link to it. Spellings of {@code path} itself are not aliases.
     *
     * @param documentId identity registered at {@code path}
     * @param path existing location of the document
     * @return another registered location of the same file, preferring a registered spelling over its resolved real
     *         path, or empty when there is none
     * @throws UncheckedIOException if a location cannot be resolved or compared
     */
    public synchronized Optional<Path> findAlias(DocumentId documentId, Path path) {
        Objects.requireNonNull(documentId, "documentId");
        Path normalized = normalize(path);
        Path resolved = resolve(normalized);
        if (Files.notExists(normalized))
            return Optional.empty();

        List<Path> aliases = new ArrayList<>();
        for (Map.Entry<Path, DocumentId> entry : idsByPath.entrySet()) {
            Path candidate = entry.getKey();
            if (!entry.getValue().equals(documentId) || !Files.exists(candidate)
                || resolve(candidate).equals(resolved))
                continue;

            try {
                if (Files.isSameFile(candidate, normalized)) {
                    aliases.add(candidate);
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to compare document paths " + candidate + " and " + path,
                    exception);
            }
        }

        return aliases.isEmpty() ? Optional.empty() : Optional.of(preferredSpelling(aliases));
    }

    private static Path preferredSpelling(List<Path> paths) {
        return paths.stream().filter(path -> !path.equals(resolve(path))).findFirst().orElse(paths.getFirst());
    }

    private void removeAssociations(DocumentId documentId) {
        idsByPath.entrySet().stream().filter(entry -> entry.getValue().equals(documentId)).map(Map.Entry::getKey)
            .toList().forEach(this::removePath);
        idsByUri.values().removeIf(documentId::equals);
    }

    private void ensureVersion(DocumentId documentId) {
        rejectReleased(documentId);
        versionsById.putIfAbsent(documentId, DocumentVersion.initial());
    }

    private void rejectReleased(DocumentId documentId) {
        if (releasedIds.contains(documentId))
            throw new IllegalStateException("Document identity has been released and cannot be reused: " + documentId);
    }

    private DocumentId findEquivalentPhysicalFile(Path path) {
        Object key = fileKey(path);
        if (key == null)
            return null;

        for (Path registered : List.copyOf(pathsByFileKey.getOrDefault(key, Set.of()))) {
            if (Files.notExists(registered))
                continue;

            try {
                if (Files.isSameFile(path, registered))
                    return idsByPath.get(registered);
            } catch (IOException exception) {
                throw new UncheckedIOException(
                    "Failed to compare document paths " + path + " and " + registered,
                    exception);
            }
        }

        return null;
    }

    private void putPath(Path path, DocumentId documentId) {
        idsByPath.put(path, documentId);
        refreshFileKey(path);
    }

    private void removePath(Path path) {
        idsByPath.remove(path);
        unindexFileKey(path);
    }

    private void refreshFileKey(Path path) {
        if (!idsByPath.containsKey(path))
            return;

        unindexFileKey(path);
        Object key = fileKey(path);
        if (key != null) {
            fileKeysByPath.put(path, key);
            pathsByFileKey.computeIfAbsent(key, _ -> new HashSet<>()).add(path);
        }
    }

    private void unindexFileKey(Path path) {
        Object key = fileKeysByPath.remove(path);
        if (key == null)
            return;

        Set<Path> paths = pathsByFileKey.get(key);
        paths.remove(path);
        if (paths.isEmpty()) {
            pathsByFileKey.remove(key);
        }
    }

    /**
     * Returns a value that is equal for every directory entry of one physical file. Windows exposes no file key, so
     * the creation time, which hard links share, narrows the candidates that {@link Files#isSameFile} confirms.
     */
    private static Object fileKey(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return attributes.fileKey() != null ? attributes.fileKey() : attributes.creationTime();
        } catch (NoSuchFileException exception) {
            return null;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read document attributes " + path, exception);
        }
    }

    private DocumentId findRegistered(Path normalized, Path resolved) {
        DocumentId existing = idsByPath.get(normalized);
        return existing != null ? existing : idsByPath.get(resolved);
    }

    private void registerSpellings(DocumentId documentId, Path normalized, Path resolved) {
        putPath(normalized, documentId);
        putPath(resolved, documentId);
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

    private static DocumentUri canonicalUri(DocumentUri uri) {
        return uri.filePath()
            .map(DocumentIdentityRegistry::normalize)
            .map(DocumentIdentityRegistry::resolve)
            .map(DocumentUri::fromPath)
            .orElse(uri);
    }
}
