package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRCheckBoxTreeItem;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import javafx.scene.control.TreeItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Connects a change item to checkbox selection and supports compact directory chains.
 */
public class CommitTreeItem extends RRCheckBoxTreeItem<ChangeItem> {
    /**
     * Creates a checkbox tree entry and connects its selection callback.
     *
     * @param item change model rendered by the tree entry
     */
    public CommitTreeItem(ChangeItem item) {
        super(item);

        Consumer<Boolean> selectionHandler = item.getSelectionHandler();
        if (selectionHandler != null) {
            selectedProperty().addListener((_, _, newValue) -> selectionHandler.accept(newValue));
        }
    }

    /**
     * Recursively merges directory chains with one child into compact path-labelled entries.
     */
    public void collapseSingleChildDirectories() {
        for (TreeItem<ChangeItem> child : new ArrayList<>(getChildren())) {
            if (child instanceof CommitTreeItem commitChild) {
                commitChild.collapseSingleChildDirectories();
            }
        }

        while (true) {
            if (!(getValue() instanceof DirectoryItem parentDir))
                return;

            if (getChildren().size() != 1)
                return;

            TreeItem<ChangeItem> onlyChild = getChildren().getFirst();
            if (!(onlyChild instanceof CommitTreeItem commitChild))
                return;

            if (!(commitChild
                .getValue() instanceof DirectoryItem(Project project, Path path, List<GitFileChange> changes, String displayTitle)))
                return;

            String mergedTitle = parentDir.displayTitle() + "/" + displayTitle;
            var merged = new DirectoryItem(project, path, changes, mergedTitle);
            setValue(merged);
            getChildren().clear();
            getChildren().addAll(commitChild.getChildren());
        }
    }
}
