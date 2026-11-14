package dev.railroadide.railroad.ide.sst.graph.api;

import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.semantic.api.TypeReference;
import java.util.List;
import java.util.Objects;

/** A certain directed relation owned by the shard that established it; unresolved guesses are omitted. */
public record SymbolEdge(SymbolId source, SymbolId target, EdgeKindId kind, List<TypeReference> typeArguments) {
    public SymbolEdge {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        typeArguments = List.copyOf(typeArguments);
    }

    public SymbolEdge(SymbolId source, SymbolId target, EdgeKindId kind) {
        this(source, target, kind, List.of());
    }
}
