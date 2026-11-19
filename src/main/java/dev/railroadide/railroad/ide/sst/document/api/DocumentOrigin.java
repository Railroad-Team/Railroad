package dev.railroadide.railroad.ide.sst.document.api;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable description of a document's backing and how its content was obtained.
 * Backing and provenance are independent: generated content can live in a file,
 * while original content can live only in memory.
 * <p>
 * For example, generated source written to disk uses {@link BackingKind#FILE} with
 * {@link ProvenanceKind#GENERATED}, attached source in a source archive uses
 * {@link BackingKind#ARCHIVE_ENTRY} with {@link ProvenanceKind#ATTACHED_SOURCE}, and
 * a decompiled view can use {@link BackingKind#VIRTUAL} with {@link ProvenanceKind#DECOMPILED}.
 * Original project and external files share the same provenance; workspace ownership
 * distinguishes them. Unsaved edits to file-backed content retain file backing, while
 * scratch buffers without a backing file use {@link BackingKind#MEMORY}.
 * <p>
 * Origin does not grant editability, persistence, indexing, navigation, or disposal
 * rights. Those decisions belong to provider capabilities and workspace policy;
 * a generated file may be read-only, and a virtual document may support edits.
 * Source references describe related inputs without providing stable navigation,
 * symbol links, or source-range mappings. No plugin-specific metadata extension or
 * provenance graph is implemented by this value.
 *
 * @param backing storage or addressing mechanism
 * @param provenance manner in which content was obtained
 * @param sources ordered source references, copied into an immutable list
 */
public record DocumentOrigin(BackingKind backing, ProvenanceKind provenance, List<OriginReference> sources) {
    /**
     * Creates an origin with an immutable copy of its source references.
     *
     * @param backing storage or addressing mechanism
     * @param provenance manner in which content was obtained
     * @param sources ordered source references
     * @throws NullPointerException if any argument or source reference is {@code null}
     */
    public DocumentOrigin {
        backing = Objects.requireNonNull(backing, "backing");
        provenance = Objects.requireNonNull(provenance, "provenance");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    }

    /**
     * Creates an origin without source references.
     *
     * @param backing storage or addressing mechanism
     * @param provenance manner in which content was obtained
     * @throws NullPointerException if either argument is {@code null}
     */
    public DocumentOrigin(BackingKind backing, ProvenanceKind provenance) {
        this(backing, provenance, List.of());
    }

    /**
     * Creates an origin for content the caller knows to be original.
     * The URI determines backing only; it does not establish provenance.
     * <p>
     * The {@code file}, {@code memory}, and {@code jar}/{@code archive} schemes map to
     * their corresponding backing kinds; all other schemes use {@link BackingKind#VIRTUAL}.
     * Scheme matching ignores case. The result has no source references.
     *
     * @param uri document location used to determine backing
     * @return origin with {@link ProvenanceKind#ORIGINAL} provenance
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public static DocumentOrigin original(DocumentUri uri) {
        return new DocumentOrigin(backingFor(uri), ProvenanceKind.ORIGINAL);
    }

    /**
     * Creates an origin for compatibility callers that only know the document location.
     * Uses the same scheme-to-backing mapping as {@link #original(DocumentUri)}, with
     * no source references and no assumption about how the content was obtained.
     *
     * @param uri document location used to determine backing
     * @return origin with {@link ProvenanceKind#UNSPECIFIED} provenance
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public static DocumentOrigin unspecified(DocumentUri uri) {
        return new DocumentOrigin(backingFor(uri), ProvenanceKind.UNSPECIFIED);
    }

    private static BackingKind backingFor(DocumentUri uri) {
        return switch (Objects.requireNonNull(uri, "uri").value().getScheme().toLowerCase(Locale.ROOT)) {
            case "file" -> BackingKind.FILE;
            case "memory" -> BackingKind.MEMORY;
            case "jar", "archive" -> BackingKind.ARCHIVE_ENTRY;
            default -> BackingKind.VIRTUAL;
        };
    }
}
