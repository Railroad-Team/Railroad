package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.core.ui.RRCheckBoxTreeItem;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import javafx.scene.control.TreeItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CommitTreeItem extends RRCheckBoxTreeItem<ChangeItem> {
    public CommitTreeItem(ChangeItem item) {
        super(item);

        Consumer<Boolean> selectionHandler = item.getSelectionHandler();
        if (selectionHandler != null) {
            selectedProperty().addListener((observable, oldValue, newValue) -> selectionHandler.accept(newValue));
        }
    }

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

            if (!(commitChild.getValue() instanceof DirectoryItem(
                Project project, Path path, List<GitFileChange> changes, String displayTitle
            )))
                return;

            String mergedTitle = parentDir.displayTitle() + "/" + displayTitle;
            var merged = new DirectoryItem(project, path, changes, mergedTitle);
            setValue(merged);
            getChildren().clear();
            getChildren().addAll(commitChild.getChildren());
        }
    }
}
