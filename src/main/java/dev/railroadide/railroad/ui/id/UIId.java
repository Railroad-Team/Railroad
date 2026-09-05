package dev.railroadide.railroad.ui.id;

import javafx.scene.Node;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Identifies a UI node by a path and its expected JavaFX type.
 * Equality includes both the path and type; constructing an ID directly does not register it with {@link UIIds}.
 *
 * @param <T> type of node identified by this ID
 */
@EqualsAndHashCode
@ToString
public final class UIId<T extends Node> {
    private final String path;
    private final Class<T> type;

    /**
     * Creates a typed identifier without registering its path.
     *
     * @param path nonblank identifier path
     * @param type expected node class
     * @throws IllegalArgumentException if the path is null or blank, or the type is null
     */
    public UIId(String path, Class<T> type) {
        if (path == null || path.isBlank())
            throw new IllegalArgumentException("Path cannot be null or blank");

        if (type == null)
            throw new IllegalArgumentException("Type cannot be null");

        this.path = path;
        this.type = type;
    }

    /**
     * Returns the identifier path.
     *
     * @return the nonblank path supplied at construction
     */
    public String path() {
        return path;
    }

    /**
     * Returns the expected node type.
     *
     * @return the node class supplied at construction
     */
    public Class<T> type() {
        return type;
    }
}
