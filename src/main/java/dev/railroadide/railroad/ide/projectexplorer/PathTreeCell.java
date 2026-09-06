package dev.railroadide.railroad.ide.projectexplorer;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.*;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.events.DocumentRenamedEvent;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.localized.LocalizedMenu;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.OperatingSystem;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An editable project explorer cell with file icons and rename support.
 */
public class PathTreeCell extends TreeCell<PathItem> {
    private final StringProperty messageProperty;
    private TextField textField;
    private Path editingPath;
    /**
     * Creates an editable file cell that publishes operation messages.
     *
     * @param messageProperty property receiving file operation messages
     */
    public PathTreeCell(StringProperty messageProperty) {
        super();

        this.messageProperty = messageProperty;
    }

    private static ContextMenu createContextMenu(PathTreeCell cell) {
        var newMenu = new LocalizedMenu("railroad.project_explorer.menu.new");
        newMenu.getItems().addAll(
            commandItem("railroad.project_explorer.menu.file", Commands.CREATE_PROJECT_EXPLORER_FILE, cell),
            commandItem("railroad.project_explorer.menu.folder", Commands.CREATE_PROJECT_EXPLORER_FOLDER, cell),
            commandItem("railroad.project_explorer.menu.java_class", Commands.CREATE_JAVA_CLASS, cell),
            commandItem("railroad.project_explorer.menu.json", Commands.CREATE_JSON, cell),
            commandItem("railroad.project_explorer.menu.text", Commands.CREATE_TEXT, cell));
        var openIn = new LocalizedMenu("railroad.project_explorer.menu.open_in");
        openIn.getItems().addAll(
            commandItem(OperatingSystem.isMac()
                ? "railroad.project_explorer.menu.finder"
                : OperatingSystem.isLinux()
                    ? "railroad.project_explorer.menu.file_manager"
                    : "railroad.project_explorer.menu.explorer",
                Commands.REVEAL_PROJECT_EXPLORER_ITEM, cell),
            commandItem("railroad.project_explorer.menu.terminal", Commands.OPEN_PROJECT_EXPLORER_ITEM_IN_TERMINAL,
                cell));
        var menu = new ContextMenu(newMenu,
            commandItem("railroad.project_explorer.menu.cut", Commands.CUT_PROJECT_EXPLORER_ITEM, cell),
            commandItem("railroad.project_explorer.menu.copy", Commands.COPY_PROJECT_EXPLORER_ITEM, cell),
            commandItem("railroad.project_explorer.menu.paste", Commands.PASTE_PROJECT_EXPLORER_ITEM, cell),
            commandItem("railroad.project_explorer.menu.rename", Commands.RENAME_PROJECT_EXPLORER_ITEM, cell),
            commandItem("railroad.project_explorer.menu.delete", Commands.DELETE_PROJECT_EXPLORER_ITEM, cell), openIn);
        if (Files.isDirectory(cell.getItem().getPath())) {
            menu.getItems().addAll(new SeparatorMenuItem(),
                commandItem("railroad.project_explorer.menu.expand_all", Commands.EXPAND_EXPLORER, cell),
                commandItem("railroad.project_explorer.menu.collapse_all", Commands.COLLAPSE_EXPLORER, cell));
        }
        return menu;
    }

    private static MenuItem commandItem(String labelKey, Command<ExplorerTarget> command, PathTreeCell cell) {
        var item = new LocalizedMenuItem(labelKey);
        CommandMenuItems.bind(item, command, () -> CommandContext.withArgument(
            Railroad.PROJECT_MANAGER.getOpenProject(), cell,
            new ExplorerTarget(cell.getTreeView(), cell.getTreeItem(),
                cell.getScene() == null ? null : cell.getScene().getWindow())));
        return item;
    }

    @Override
    protected void updateItem(PathItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setContextMenu(null);
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
                    if (event.getButton() != MouseButton.PRIMARY || event.isConsumed() || getItem() == null)
                        return;

                    if (event.getClickCount() == 1 && Files.isRegularFile(getItem().getPath())) {
                        Services.EDITOR_TAB_MANAGER.openPreview(getItem().getPath());
                    } else if (event.getClickCount() == 2) {
                        Path path = getItem().getPath();
                        if (Files.isDirectory(path)) {
                            TreeItem<PathItem> treeItem = getTreeItem();
                            treeItem.setExpanded(!treeItem.isExpanded());
                        } else {
                            CommandDispatcher.execute(Commands.OPEN_PROJECT_EXPLORER_ITEM,
                                CommandContext.withArgument(Railroad.PROJECT_MANAGER.getOpenProject(), this,
                                    new ExplorerTarget(getTreeView(), getTreeItem(), getScene().getWindow())));
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
        if (getTreeView().getProperties().get("railroad:rename-item") == getTreeItem() && getItem() != null) {
            getTreeView().getProperties().remove("railroad:rename-item");
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
                messageProperty
                    .setValue(L18n.localize("railroad.project_explorer.rename_failed", editingPath.getFileName()));
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
