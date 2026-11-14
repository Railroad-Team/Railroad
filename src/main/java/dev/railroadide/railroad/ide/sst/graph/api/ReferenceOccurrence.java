package dev.railroadide.railroad.ide.sst.graph.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

/** A resolved occurrence in one immutable document revision; compound reads/writes use two roles. */
public record ReferenceOccurrence(
    SymbolId target,
    ReferenceKindId kind,
    Location<?> location,
    @Nullable SymbolId enclosingSymbol
) {
    public ReferenceOccurrence {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
    }
}
