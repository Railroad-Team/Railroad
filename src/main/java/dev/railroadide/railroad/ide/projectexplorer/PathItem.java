package dev.railroadide.railroad.ide.projectexplorer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;

/**
 * Filesystem path and observable cut state displayed in the project explorer.
 */
public class PathItem {
    @Setter
    @Getter
    private Path path;
    private int depthCount;

    private final BooleanProperty cutProperty = new SimpleBooleanProperty(false);

    /**
     * Creates an explorer item with an unset cut marker and zero depth counter.
     *
     * @param path filesystem path to operate on
     */
    public PathItem(Path path) {
        this.path = path;
        this.depthCount = 0;
    }

    @Override
    public String toString() {
        if (this.path.getFileName() == null)
            return this.path.toString();
        else
            return this.path.getFileName().toString();
    }

    /**
     * Increments and returns this item's depth counter.
     *
     * @return incremented counter
     */
    public int getNewDepthCount() {
        return ++this.depthCount;
    }

    /**
     * Exposes whether this item is marked for a clipboard move.
     *
     * @return observable cut state
     */
    public BooleanProperty cutProperty() {
        return cutProperty;
    }

    /**
     * Checks whether this item is marked for a clipboard move.
     *
     * @return whether the cut marker is set
     */
    public boolean isCut() {
        return cutProperty.get();
    }

    /**
     * Updates the clipboard move marker for this item.
     *
     * @param cut whether the item is marked for a clipboard move
     */
    public void setCut(boolean cut) {
        cutProperty.set(cut);
    }
}
