package dev.railroadide.railroad.ide.sst.graph.api;

import java.util.Objects;

/**
 * Extensible, namespaced role of a resolved reference occurrence.
 *
 * @param id namespaced identifier
 */
public record ReferenceKindId(String id) {
    /**
     * Occurrence that declares the target symbol.
     */
    public static final ReferenceKindId DECLARATION = new ReferenceKindId("core:declaration");
    /**
     * Occurrence that reads a value.
     */
    public static final ReferenceKindId READ = new ReferenceKindId("core:read");
    /**
     * Occurrence that writes a value.
     */
    public static final ReferenceKindId WRITE = new ReferenceKindId("core:write");
    /**
     * Resolved invocation of a callable declaration.
     */
    public static final ReferenceKindId CALL = new ReferenceKindId("core:call");
    /**
     * Occurrence using a type in source.
     */
    public static final ReferenceKindId TYPE_USE = new ReferenceKindId("core:type-use");
    /**
     * Occurrence naming an imported declaration.
     */
    public static final ReferenceKindId IMPORT = new ReferenceKindId("core:import");
    /**
     * Occurrence naming a base type in an inheritance clause.
     */
    public static final ReferenceKindId INHERITANCE = new ReferenceKindId("core:inheritance");
    /**
     * Occurrence naming an annotation type.
     */
    public static final ReferenceKindId ANNOTATION = new ReferenceKindId("core:annotation");
    /**
     * Occurrence referring to a method or constructor without invoking it.
     */
    public static final ReferenceKindId METHOD_REFERENCE = new ReferenceKindId("core:method-reference");
    /**
     * Resolved link to a declaration in documentation.
     */
    public static final ReferenceKindId DOCUMENTATION = new ReferenceKindId("core:documentation");

    /**
     * Creates a validated namespaced reference role.
     *
     * @param id namespaced identifier
     */
    public ReferenceKindId {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+"))
            throw new IllegalArgumentException("Reference kind must be namespaced: " + id);
    }
}
