package dev.railroadide.railroad.ide.sst.graph.api;

import java.util.Objects;

/**
 * Directed graph relation; subtype and override point from specialization to base.
 *
 * @param id namespaced identifier
 */
public record EdgeKindId(String id) {
    /**
     * Relation from a subtype to its direct base type.
     */
    public static final EdgeKindId SUBTYPE = new EdgeKindId("core:subtype");
    /**
     * Relation from an overriding method to the overridden declaration.
     */
    public static final EdgeKindId OVERRIDE = new EdgeKindId("core:override");
    /**
     * Resolved invocation of a callable declaration.
     */
    public static final EdgeKindId CALL = new EdgeKindId("core:call");

    /**
     * Creates a validated namespaced relation kind.
     *
     * @param id namespaced identifier
     */
    public EdgeKindId {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+"))
            throw new IllegalArgumentException("Edge kind must be namespaced: " + id);
    }
}
