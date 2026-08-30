package dev.railroadide.railroad.ui.id;

import javafx.scene.Node;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public final class UIId<T extends Node> {
    private final String path;
    private final Class<T> type;

    UIId(String path, Class<T> type) {
        if (path == null || path.isBlank())
            throw new IllegalArgumentException("Path cannot be null or blank");

        if (type == null)
            throw new IllegalArgumentException("Type cannot be null");

        this.path = path;
        this.type = type;
    }

    public String path() {
        return path;
    }

    public Class<T> type() {
        return type;
    }
}
