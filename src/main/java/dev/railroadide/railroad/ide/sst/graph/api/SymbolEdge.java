package dev.railroadide.railroad.ide.sst.graph.api;

import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.semantic.api.TypeReference;
import java.util.List;
import java.util.Objects;

/**
 * A certain directed relation owned by the shard that established it; unresolved guesses are omitted.
 *
 * @param source identity at the start of the directed relation
 * @param target identity of the referenced declaration
 * @param kind semantic category of the declaration or relation
 * @param typeArguments ordered type arguments on the relation, copied on construction
 */
public record SymbolEdge(SymbolId source, SymbolId target, EdgeKindId kind, List<TypeReference> typeArguments) {
    /**
     * Creates a directed relation and copies any type arguments.
     *
     * @param source identity at the start of the directed relation
     * @param target identity of the referenced declaration
     * @param kind semantic category of the declaration or relation
     * @param typeArguments ordered type arguments on the relation, copied on construction
     */
    public SymbolEdge {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        typeArguments = List.copyOf(typeArguments);
    }

    /**
     * Creates a directed relation and copies any type arguments.
     *
     * @param source identity at the start of the directed relation
     * @param target identity of the referenced declaration
     * @param kind semantic category of the declaration or relation
     */
    public SymbolEdge(SymbolId source, SymbolId target, EdgeKindId kind) {
        this(source, target, kind, List.of());
    }
}
