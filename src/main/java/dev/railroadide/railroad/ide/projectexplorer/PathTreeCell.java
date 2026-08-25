package dev.railroadide.railroad.ide.projectexplorer;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.projectexplorer.dialog.CreateFileDialog;
import dev.railroadide.railroad.ide.projectexplorer.dialog.DeleteDialog;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.DocumentRenamedEvent;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.utility.FileUtils;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// TODO: Tons of localization issues here, need to fix that
public class PathTreeCell extends TreeCell<PathItem> {
    private final StringProperty messageProperty;
    private TextField textField;
    private Path editingPath;
    private boolean allowEdit = false;
    private final Project project;

    public PathTreeCell(Project project, StringProperty messageProperty) {
        super();

        this.project = project;
        this.messageProperty = messageProperty;
    }

    private static ContextMenu createContextMenu(PathTreeCell cell) {
        Path currentPath = cell.getItem().getPath();
        Path directoryPath = Files.isDirectory(currentPath) ? currentPath : currentPath.getParent();
        Window window = cell.getScene().getWindow();

        var menu = new ContextMenu();

        var newMenu = new Menu("New");
        var newFile = new MenuItem("File");
        var newFolder = new MenuItem("Folder");
        var newClass = new MenuItem("Java Class");
        var newJson = new MenuItem("JSON File");
        var newTxt = new MenuItem("Text File");

        newFile.setOnAction(_ -> CreateFileDialog.open(window, directoryPath, FileCreateType.FILE));
        newFolder.setOnAction(_ -> CreateFileDialog.open(window, directoryPath, FileCreateType.FOLDER));
        newClass.setOnAction(_ -> CreateFileDialog.open(window, directoryPath, FileCreateType.JAVA_CLASS));
        newJson.setOnAction(_ -> CreateFileDialog.open(window, directoryPath, FileCreateType.JSON));
        newTxt.setOnAction(_ -> CreateFileDialog.open(window, directoryPath, FileCreateType.TXT));

        var cut = new MenuItem("Cut");
        var copy = new MenuItem("Copy");
        var paste = new MenuItem("Paste");
        cell.itemProperty().flatMap(PathItem::cutProperty).addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue) {
                cut.setDisable(true);
                copy.setDisable(true);
            } else {
                cut.setDisable(false);
                copy.setDisable(false);
            }
        });

        cut.setOnAction(_ -> ProjectExplorerPane.cut((PathTreeItem) cell.getTreeItem(), cell.getTreeView()));
        copy.setOnAction(_ -> ProjectExplorerPane.copy(cell.getItem()));
        paste.setOnAction(_ -> ProjectExplorerPane.paste(cell.getItem()));

        var rename = new MenuItem("Rename");
        var delete = new MenuItem("Delete");

        rename.setOnAction(_ -> {
            cell.allowEdit = true;
            cell.startEdit();
        });
        delete.setOnAction(_ -> DeleteDialog.open(currentPath));

        var openIn = new Menu("Open In");
        var openInExplorer = new MenuItem("Explorer");
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            openInExplorer.setText("Finder");
        } else if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            openInExplorer.setText("File Manager");
        }
        var openInTerminal = new MenuItem("Terminal");

        openInExplorer.setOnAction(_ -> ProjectExplorerPane.openInExplorer(currentPath));
        openInTerminal.setOnAction(_ -> ProjectExplorerPane.openInTerminal(cell.getItem()));

        newMenu.getItems().addAll(newFile, newFolder, newClass, newJson, newTxt);
        openIn.getItems().addAll(openInExplorer, openInTerminal);

        menu.getItems().addAll(newMenu, cut, copy, paste, rename, delete, openIn);

        if (Files.isDirectory(currentPath)) {
            var expandAll = new MenuItem("Expand All");
            expandAll.setOnAction(_ -> ProjectExplorerPane.expandAll(cell.getTreeItem()));

            var collapseAll = new MenuItem("Collapse All");
            collapseAll.setOnAction(_ -> ProjectExplorerPane.collapseAll(cell.getTreeItem()));

            menu.getItems().addAll(new SeparatorMenuItem(), expandAll, collapseAll);
        }

        menu.setOnShown(_ -> paste.setDisable(!Clipboard.getSystemClipboard().hasFiles()));

        return menu;
    }

    @Override
    protected void updateItem(PathItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setText(null);
            setGraphic(null);
            setOnMouseClicked(null);
        } else {
            String text = getString();
            Node image = FileUtils.getIcon(item.getPath());
            if (isEditing()) {
                if (textField != null) {
                    textField.setText(text);
                }

                setText(null);

                var hbox = new RRHBox();
                hbox.getChildren().addAll(image, textField);
                setGraphic(hbox);
                setOnMouseClicked(null);
            } else {
                setText(text);
                setGraphic(image);

                setContextMenu(createContextMenu(this));

                // Double-click to open, not rename
                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !event.isConsumed() && getItem() != null) {
                        Path path = getItem().getPath();
                        if (Files.isDirectory(path)) {
                            TreeItem<PathItem> treeItem = getTreeItem();
                            treeItem.setExpanded(!treeItem.isExpanded());
                        } else {
                            ProjectExplorerPane.openFile(project, getItem());
                        }
                        event.consume();
                    }
                });
            }
        }
    }

    /**
     * Starts the editing mode for the tree cell.
     * Creates a text field for renaming the file or directory.
     */
    @Override
    public void startEdit() {
        if (allowEdit) {
            allowEdit = false;
            super.startEdit();
            if (textField == null) {
                createTextField();
            }

            setText(null);

            var hbox = new RRHBox();
            hbox.getChildren().addAll(FileUtils.getIcon(getItem().getPath()), textField);
            setGraphic(hbox);
            textField.selectAll();

            if (getItem() == null) {
                editingPath = null;
            } else {
                editingPath = getItem().getPath();
            }
        }
    }

    /**
     * Commits the edit by renaming the file or directory.
     * Moves the file to the new path and updates the item.
     *
     * @param newValue the new PathItem with the updated path
     */
    @Override
    public void commitEdit(PathItem newValue) {
        if (editingPath != null) {
            try {
                // TODO: This should really use the IDEStateService to rename the document rather than have manual
                // handling
                ProjectExplorerPane.disableFileChangeListener();

                String oldName = editingPath.getFileName().toString();
                String newName = newValue.getPath().getFileName().toString();

                Files.move(editingPath, newValue.getPath());
                getItem().setPath(newValue.getPath());
                Railroad.EVENT_BUS
                    .publish(new DocumentRenamedEvent(new FileSystemDocument(newValue.getPath()), oldName, newName));
            } catch (IOException exception) {
                cancelEdit();
                messageProperty.setValue("Renaming %s failed".formatted(editingPath.getFileName()));
            } finally {
                Platform.runLater(ProjectExplorerPane::enableFileChangeListener);
            }
        }

        super.commitEdit(newValue);
        setText(getString());
        setGraphic(FileUtils.getIcon(newValue.getPath()));
    }

    /**
     * Cancels the editing mode and restores the original display.
     */
    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getString());
        setGraphic(null);
    }

    private String getString() {
        return getItem() == null ? "" : getItem().toString();
    }

    private void createTextField() {
        textField = new TextField(getString());
        textField.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                var path = Path.of(getItem().getPath().getParent().toAbsolutePath().toString(), textField.getText());
                commitEdit(new PathItem(path));
            } else if (event.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
            }
        });
    }
}
