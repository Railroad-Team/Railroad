package dev.railroadide.railroad.ide.sst.graph.api;

import java.util.Objects;

/** Extensible, namespaced role of a resolved reference occurrence. */
public record ReferenceKindId(String id) {
    public static final ReferenceKindId DECLARATION = new ReferenceKindId("core:declaration");
    public static final ReferenceKindId READ = new ReferenceKindId("core:read");
    public static final ReferenceKindId WRITE = new ReferenceKindId("core:write");
    public static final ReferenceKindId CALL = new ReferenceKindId("core:call");
    public static final ReferenceKindId TYPE_USE = new ReferenceKindId("core:type-use");
    public static final ReferenceKindId IMPORT = new ReferenceKindId("core:import");
    public static final ReferenceKindId INHERITANCE = new ReferenceKindId("core:inheritance");
    public static final ReferenceKindId ANNOTATION = new ReferenceKindId("core:annotation");
    public static final ReferenceKindId METHOD_REFERENCE = new ReferenceKindId("core:method-reference");
    public static final ReferenceKindId DOCUMENTATION = new ReferenceKindId("core:documentation");

    public ReferenceKindId {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+"))
            throw new IllegalArgumentException("Reference kind must be namespaced: " + id);
    }
}
