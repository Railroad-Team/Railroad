package dev.railroadide.railroad.ide.sst.graph.api;

import java.util.Objects;

/** Directed graph relation; subtype and override point from specialization to base. */
public record EdgeKindId(String id) {
    public static final EdgeKindId SUBTYPE = new EdgeKindId("core:subtype");
    public static final EdgeKindId OVERRIDE = new EdgeKindId("core:override");
    public static final EdgeKindId CALL = new EdgeKindId("core:call");

    public EdgeKindId {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+"))
            throw new IllegalArgumentException("Edge kind must be namespaced: " + id);
    }
}
