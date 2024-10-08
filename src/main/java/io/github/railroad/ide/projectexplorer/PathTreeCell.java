package io.github.railroad.ide.projectexplorer;

import io.github.railroad.ide.projectexplorer.dialog.CreateFileDialog;
import io.github.railroad.ide.projectexplorer.dialog.DeleteDialog;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.utility.FileHandler;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathTreeCell extends TreeCell<PathItem> {
    private TextField textField;
    private Path editingPath;
    private final StringProperty messageProperty;
    private final RRBorderPane mainPane;

    public PathTreeCell(StringProperty messageProperty, RRBorderPane mainPane) {
        super();

        this.messageProperty = messageProperty;
        this.mainPane = mainPane;
    }

    @Override
    protected void updateItem(PathItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            String text = getString();
            Node image = FileHandler.getIcon(item.getPath());
            if (isEditing()) {
                if (textField != null) {
                    textField.setText(text);
                }

                setText(null);

                var hbox = new RRHBox();
                hbox.getChildren().addAll(image, textField);
                setGraphic(hbox);
            } else {
                setText(text);
                setGraphic(image);

                setContextMenu(createContextMenu(this, mainPane));

                // TODO: Find a way to do this
//                styleProperty().bind(itemProperty()
//                        .flatMap(PathItem::cutProperty)
//                        .map(cut -> cut != null && cut ? "-fx-text-fill: #f0f0f0;" : ""));
            }
        }
    }

    private static ContextMenu createContextMenu(PathTreeCell cell, RRBorderPane mainPane) {
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

        newFile.setOnAction(event -> CreateFileDialog.open(window, directoryPath, FileCreateType.FILE));
        newFolder.setOnAction(event -> CreateFileDialog.open(window, directoryPath, FileCreateType.FOLDER));
        newClass.setOnAction(event -> CreateFileDialog.open(window, directoryPath, FileCreateType.JAVA_CLASS));
        newJson.setOnAction(event -> CreateFileDialog.open(window, directoryPath, FileCreateType.JSON));
        newTxt.setOnAction(event -> CreateFileDialog.open(window, directoryPath, FileCreateType.TXT));

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

        cut.setOnAction(event -> ProjectExplorerPane.cut((PathTreeItem) cell.getTreeItem(), cell.getTreeView()));
        copy.setOnAction(event -> ProjectExplorerPane.copy(cell.getItem()));
        paste.setOnAction(event -> ProjectExplorerPane.paste(window, cell.getItem()));

        var rename = new MenuItem("Rename");
        var delete = new MenuItem("Delete");

        rename.setOnAction(event -> cell.startEdit());
        delete.setOnAction(event -> DeleteDialog.open(window, currentPath));

        var openIn = new Menu("Open In");
        var openInExplorer = new MenuItem("Explorer");
        if (System.getProperty("os.name").toLowerCase().contains("mac"))
            openInExplorer.setText("Finder");
        else if (System.getProperty("os.name").toLowerCase().contains("linux"))
            openInExplorer.setText("File Manager");
        if (!Desktop.isDesktopSupported())
            openInExplorer.setDisable(true);

        var openInTerminal = new MenuItem("Terminal");

        openInExplorer.setOnAction(event -> ProjectExplorerPane.openInExplorer(currentPath));
        openInTerminal.setOnAction(event -> ProjectExplorerPane.openInTerminal(cell.getItem(), mainPane));

        newMenu.getItems().addAll(newFile, newFolder, newClass, newJson, newTxt);
        openIn.getItems().addAll(openInExplorer, openInTerminal);

        menu.getItems().addAll(newMenu, cut, copy, paste, rename, delete, openIn);

        if (Files.isDirectory(currentPath)) {
            var expandAll = new MenuItem("Expand All");
            expandAll.setOnAction(event -> ProjectExplorerPane.expandAll(cell.getTreeItem()));

            var collapseAll = new MenuItem("Collapse All");
            collapseAll.setOnAction(event -> ProjectExplorerPane.collapseAll(cell.getTreeItem()));

            menu.getItems().addAll(new SeparatorMenuItem(), expandAll, collapseAll);
        }

        menu.setOnShown(event -> paste.setDisable(!Clipboard.getSystemClipboard().hasFiles()));

        return menu;
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (textField == null) {
            createTextField();
        }

        setText(null);

        var hbox = new RRHBox();
        hbox.getChildren().addAll(FileHandler.getIcon(getItem().getPath()), textField);
        setGraphic(hbox);
        textField.selectAll();

        if (getItem() == null) {
            editingPath = null;
        } else {
            editingPath = getItem().getPath();
        }
    }

    @Override
    public void commitEdit(PathItem newValue) {
        if (editingPath != null) {
            try {
                ProjectExplorerPane.disableFileChangeListener();

                Files.move(editingPath, newValue.getPath());
                getItem().setPath(newValue.getPath());
            } catch (IOException exception) {
                cancelEdit();
                messageProperty.setValue("Renaming %s failed".formatted(editingPath.getFileName()));
            } finally {
                Platform.runLater(ProjectExplorerPane::enableFileChangeListener);
            }
        }

        super.commitEdit(newValue);
        setText(getString());
        setGraphic(FileHandler.getIcon(newValue.getPath()));
    }

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
