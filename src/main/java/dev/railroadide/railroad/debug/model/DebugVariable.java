package dev.railroadide.railroad.debug.model;

/**
 * A formatted debugger value with an optional suspension-scoped reference to its children.
 *
 * @param name variable name, field name, array index or watch expression
 * @param type declared or runtime type name
 * @param value formatted value suitable for display
 * @param childrenReference positive expansion handle, or zero for a leaf value
 * @param indexedChildren array length, or zero for non-array values
 */
public record DebugVariable(
    String name,
    String type,
    String value,
    long childrenReference,
    int indexedChildren
) {
    /**
     * Checks whether this value carries an expandable child reference.
     *
     * @return true when the child reference is positive
     */
    public boolean hasChildren() {
        return childrenReference > 0;
    }
}
