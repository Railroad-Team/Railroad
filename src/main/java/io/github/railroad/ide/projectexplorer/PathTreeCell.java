package io.github.railroad.ide.projectexplorer;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.utility.FileHandler;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathTreeCell extends TreeCell<PathItem> {
    private TextField textField;
    private Path editingPath;
    private final StringProperty messageProperty;

    public PathTreeCell(StringProperty messageProperty) {
        super();

        this.messageProperty = messageProperty;
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

                setContextMenu(createContextMenu(this));
            }
        }
    }

    private static ContextMenu createContextMenu(PathTreeCell cell) {
        Path path = Files.isDirectory(cell.getItem().getPath()) ? cell.getItem().getPath() : cell.getItem().getPath().getParent();
        Window window = cell.getScene().getWindow();

        var menu = new ContextMenu();

        var newMenu = new Menu("New");
        var newFile = new MenuItem("File");
        var newFolder = new MenuItem("Folder");
        var newClass = new MenuItem("Java Class");
        var newJson = new MenuItem("JSON File");
        var newTxt = new MenuItem("Text File");

        newFile.setOnAction(event -> ProjectExplorerPane.createFile(window, path, FileCreateType.FILE));
        newFolder.setOnAction(event -> ProjectExplorerPane.createFile(window, path, FileCreateType.FOLDER));
        newClass.setOnAction(event -> ProjectExplorerPane.createFile(window, path, FileCreateType.JAVA_CLASS));
        newJson.setOnAction(event -> ProjectExplorerPane.createFile(window, path, FileCreateType.JSON));
        newTxt.setOnAction(event -> ProjectExplorerPane.createFile(window, path, FileCreateType.TXT));

        var cut = new MenuItem("Cut");
        var copy = new MenuItem("Copy");
        var paste = new MenuItem("Paste");

//        cut.setOnAction(event -> ProjectExplorerPane.cut(cell.getItem()));
//        copy.setOnAction(event -> ProjectExplorerPane.copy(cell.getItem()));
//        paste.setOnAction(event -> ProjectExplorerPane.paste(cell.getItem()));

        var rename = new MenuItem("Rename");
        var delete = new MenuItem("Delete");

        rename.setOnAction(event -> cell.startEdit());
//        delete.setOnAction(event -> ProjectExplorerPane.delete(cell.getItem()));

        var openIn = new Menu("Open In");
        var openInExplorer = new MenuItem("Explorer");
        var openInTerminal = new MenuItem("Terminal");

//        openInExplorer.setOnAction(event -> ProjectExplorerPane.openInExplorer(cell.getItem()));
//        openInTerminal.setOnAction(event -> ProjectExplorerPane.openInTerminal(cell.getItem()));

        newMenu.getItems().addAll(newFile, newFolder, newClass, newJson, newTxt);
        openIn.getItems().addAll(openInExplorer, openInTerminal);

        menu.getItems().addAll(newMenu, cut, copy, paste, rename, delete, openIn);

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
