package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.railroad.project.Project;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;

import java.util.function.Consumer;

public interface ChangeItem {
    Node getIcon();

    String getTitle();

    String getSubtitle();

    ContextMenu getContextMenu(Project project);

    Consumer<Boolean> getSelectionHandler();

    Consumer<MouseEvent> getDoubleClickHandler();

    String getStyleClass();

    static String formatTitle(String title, String subtitle) {
        return subtitle == null || subtitle.isEmpty()
            ? title
            : title + " (" + subtitle + ")";
    }
}
