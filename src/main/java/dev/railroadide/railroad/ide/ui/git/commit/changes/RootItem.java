package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;

import java.util.function.Consumer;

/**
 * Represents the hidden root of the commit changes tree.
 */
public class RootItem implements ChangeItem {
    /**
     * Shared hidden root entry for the commit tree.
     */
    public static final RootItem INSTANCE = new RootItem();

    @Override
    public Node getIcon() {
        return null;
    }

    @Override
    public String getTitle() {
        return "";
    }

    @Override
    public String getSubtitle() {
        return "";
    }

    @Override
    public ContextMenu getContextMenu(Project project) {
        return null;
    }

    @Override
    public Consumer<Boolean> getSelectionHandler() {
        return null;
    }

    @Override
    public Consumer<MouseEvent> getDoubleClickHandler() {
        return null;
    }

    @Override
    public String getStyleClass() {
        return "git-root-item";
    }

    @Override
    public String toString() {
        return "";
    }
}
