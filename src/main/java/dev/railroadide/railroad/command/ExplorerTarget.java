package dev.railroadide.railroad.command;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.projectexplorer.FileCreateType;
import dev.railroadide.railroad.ide.projectexplorer.PathItem;
import dev.railroadide.railroad.ide.projectexplorer.PathTreeItem;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.projectexplorer.dialog.CreateFileDialog;
import dev.railroadide.railroad.ide.projectexplorer.dialog.DeleteDialog;
import dev.railroadide.railroad.utility.FileUtils;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An explicit tree entry used by explorer menus and shortcuts independently of selection.
 *
 * @param tree tree containing the entry
 * @param item target entry, or null when no entry is selected
 * @param window owner window for file-operation dialogs
 */
public record ExplorerTarget(TreeView<PathItem> tree, TreeItem<PathItem> item, Window window) {
    /**
     * Checks whether this tree target still contains a value.
     *
     * @return whether the target has a filesystem entry
     */
    public boolean valid() {
        return item != null && item.getValue() != null;
    }

    /**
     * Returns the path represented by the target entry.
     *
     * @return target path
     */
    public Path path() {
        return item.getValue().getPath();
    }

    /**
     * Checks whether the target is marked for a clipboard move.
     *
     * @return whether the target is cut
     */
    public boolean isCut() {
        return valid() && item.getValue().cutProperty().get();
    }

    /**
     * Opens a file or advances the selection for a directory.
     */
    public void open() {
        if (Files.isDirectory(path())) {
            tree.getSelectionModel().selectNext();
        } else {
            Services.EDITOR_TAB_MANAGER.open(path());
        }
    }

    /**
     * Opens the existing delete confirmation for this entry.
     */
    public void delete() {
        DeleteDialog.open(path());
    }

    /**
     * Marks this entry for moving through the clipboard.
     */
    public void cut() {
        ProjectExplorerPane.cut((PathTreeItem) item, tree);
    }

    /**
     * Copies this entry to the filesystem clipboard.
     */
    public void copy() {
        ProjectExplorerPane.copy(item.getValue());
    }

    /**
     * Pastes clipboard files into the target entry.
     */
    public void paste() {
        ProjectExplorerPane.paste(item.getValue());
    }

    /**
     * Opens the creation dialog in the target directory, or the parent of a target file.
     *
     * @param type kind of file or directory to create
     */
    public void create(FileCreateType type) {
        Path directory = Files.isDirectory(path()) ? path() : path().getParent();
        CreateFileDialog.open(window, directory, type);
    }

    /**
     * Requests inline rename for this exact tree entry.
     */
    public void rename() {
        tree.getProperties().put("railroad:rename-item", item);
        tree.edit(item);
    }

    /**
     * Reveals the entry in the operating system's file explorer.
     */
    public void reveal() {
        FileUtils.openInExplorer(path());
    }

    /**
     * Opens a terminal for the filesystem entry.
     */
    public void terminal() {
        FileUtils.openInTerminal(path());
    }

    /**
     * Expands the target entry and its descendants.
     */
    public void expand() {
        ProjectExplorerPane.expandAll(item);
    }

    /**
     * Collapses the target entry and its descendants.
     */
    public void collapse() {
        ProjectExplorerPane.collapseAll(item);
    }
}
