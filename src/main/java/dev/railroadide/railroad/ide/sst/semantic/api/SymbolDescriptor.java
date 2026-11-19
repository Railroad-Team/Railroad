package dev.railroadide.railroad.ide.sst.semantic.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, node-free declaration facts shared by source and binary indexes and independent
 * language providers. Identity is {@link #id()}, not record equality; facts and locations can change
 * in a later snapshot. The publishing shard owns revisions and retention. Alternate locations are
 * ordered by provider preference and do not allocate additional identities.
 *
 * @param id provider-owned canonical declaration identity
 * @param kind semantic category of the declaration or relation
 * @param simpleName nonblank unqualified declaration name
 * @param qualifiedName qualified declaration name, or {@code null} when unavailable
 * @param ownerId containing declaration identity, or {@code null} for an unowned symbol
 * @param signature structured declaration signature, or {@code null} when unavailable
 * @param declaredType declared value type, or {@code null} when unavailable or inapplicable
 * @param metadata immutable modifiers, visibility and provider properties
 * @param locations declaration views in provider preference order, copied on construction
 */
public record SymbolDescriptor(
    SymbolId id,
    SymbolKindId kind,
    String simpleName,
    @Nullable String qualifiedName,
    @Nullable SymbolId ownerId,
    @Nullable SymbolSignature signature,
    @Nullable TypeReference declaredType,
    SymbolMetadata metadata,
    List<SymbolLocation> locations
) {
    /**
     * Creates validated declaration facts and copies location views.
     *
     * @param id provider-owned canonical declaration identity
     * @param kind semantic category of the declaration or relation
     * @param simpleName nonblank unqualified declaration name
     * @param qualifiedName qualified declaration name, or {@code null} when unavailable
     * @param ownerId containing declaration identity, or {@code null} for an unowned symbol
     * @param signature structured declaration signature, or {@code null} when unavailable
     * @param declaredType declared value type, or {@code null} when unavailable or inapplicable
     * @param metadata immutable modifiers, visibility and provider properties
     * @param locations declaration views in provider preference order, copied on construction
     */
    public SymbolDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(simpleName, "simpleName");
        if (simpleName.isBlank())
            throw new IllegalArgumentException("Simple name cannot be blank");
        if (qualifiedName != null && qualifiedName.isBlank())
            throw new IllegalArgumentException("Qualified name cannot be blank");
        if (id.equals(ownerId))
            throw new IllegalArgumentException("A symbol cannot own itself");
        Objects.requireNonNull(metadata, "metadata");
        locations = List.copyOf(locations);
    }
}
