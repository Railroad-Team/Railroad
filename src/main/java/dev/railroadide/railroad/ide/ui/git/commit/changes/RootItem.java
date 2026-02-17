package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.railroad.project.Project;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;

import java.util.function.Consumer;

public class RootItem implements ChangeItem {
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
