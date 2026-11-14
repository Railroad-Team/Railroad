package dev.railroadide.railroad.ide.sst.graph.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

/**
 * A resolved occurrence in one immutable document revision; compound reads/writes use two roles.
 *
 * @param target identity of the referenced declaration
 * @param kind semantic category of the declaration or relation
 * @param location occurrence range in its owning document revision
 * @param enclosingSymbol containing declaration identity, or {@code null} when unavailable
 */
public record ReferenceOccurrence(
    SymbolId target,
    ReferenceKindId kind,
    Location<?> location,
    @Nullable SymbolId enclosingSymbol
) {
    /**
     * Creates a resolved occurrence tied to a document revision.
     *
     * @param target identity of the referenced declaration
     * @param kind semantic category of the declaration or relation
     * @param location occurrence range in its owning document revision
     * @param enclosingSymbol containing declaration identity, or {@code null} when unavailable
     */
    public ReferenceOccurrence {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
    }
}
