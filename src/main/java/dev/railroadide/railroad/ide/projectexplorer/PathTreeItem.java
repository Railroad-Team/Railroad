package dev.railroadide.railroad.ide.projectexplorer;

import dev.railroadide.railroad.Railroad;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class PathTreeItem extends TreeItem<PathItem> {
    private boolean isLeaf = false;
    private boolean isFirstTimeChildren = true;
    private boolean isFirstTimeLeft = true;

    public PathTreeItem(PathItem pathItem) {
        super(pathItem);
    }

    @Override
    public ObservableList<TreeItem<PathItem>> getChildren() {
        if (isFirstTimeChildren) {
            isFirstTimeChildren = false;
            super.getChildren().setAll(buildChildren(this));
        }

        return super.getChildren();
    }

    @Override
    public boolean isLeaf() {
        if(this.isFirstTimeLeft) {
            this.isFirstTimeLeft = false;
            Path path = getValue().getPath();
            this.isLeaf = !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        }

        return this.isLeaf;
    }

    private ObservableList<TreeItem<PathItem>> buildChildren(TreeItem<PathItem> treeItem) {
        Path path = treeItem.getValue().getPath();
        if(path != null && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            ObservableList<TreeItem<PathItem>> children = FXCollections.observableArrayList();
            try(DirectoryStream<Path> directories = Files.newDirectoryStream(path)) {
                for (Path directory : directories) {
                    children.add(new PathTreeItem(new PathItem(directory)));
                }
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to build children for tree item: {}", treeItem, exception);
            }

            return children;
        }

        return FXCollections.emptyObservableList();
    }
}
