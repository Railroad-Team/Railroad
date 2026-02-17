package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;

import java.util.function.Consumer;

public class ChangesRootItem implements ChangeItem {
    public static final ChangesRootItem INSTANCE = new ChangesRootItem();

    @Override
    public Node getIcon() {
        return null;
    }

    @Override
    public String getTitle() {
        return L18n.localize("git.commit.changes.root.title");
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
        return "git-changes-root-item";
    }

    @Override
    public String toString() {
        return getTitle();
    }
}
