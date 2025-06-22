package io.github.railroad.ide.projectexplorer;

import javafx.scene.control.TreeItem;

import java.io.Serializable;
import java.nio.file.Files;
import java.util.Comparator;

public class PathTreeItemComparator implements Comparator<TreeItem<PathItem>>, Serializable {
    @Override
    public int compare(TreeItem<PathItem> item1, TreeItem<PathItem> item2) {
        boolean isDir1 = Files.isDirectory(item1.getValue().getPath());
        boolean isDir2 = Files.isDirectory(item2.getValue().getPath());

        if (isDir1 && !isDir2) {
            return -1;
        } else if (!isDir1 && isDir2) {
            return 1;
        } else {
            return item1.getValue().getPath().getFileName().toString()
                    .compareToIgnoreCase(item2.getValue().getPath().getFileName().toString());
        }
    }
}