package dev.railroadide.railroad.ide.sst.document.api;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable description of a document's backing and how its content was obtained.
 * Backing and provenance are independent: generated content can live in a file,
 * while original content can live only in memory.
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
     * Supplies original provenance for compatibility callers that only have a location.
     * Generated, decompiled, and attached sources must supply their origin explicitly.
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
        BackingKind backing = switch (Objects.requireNonNull(uri, "uri").value().getScheme().toLowerCase(Locale.ROOT)) {
            case "file" -> BackingKind.FILE;
            case "memory" -> BackingKind.MEMORY;
            case "jar", "archive" -> BackingKind.ARCHIVE_ENTRY;
            default -> BackingKind.VIRTUAL;
        };
        return new DocumentOrigin(backing, ProvenanceKind.ORIGINAL);
    }
}
