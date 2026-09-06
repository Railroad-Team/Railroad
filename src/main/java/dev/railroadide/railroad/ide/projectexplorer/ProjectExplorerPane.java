package dev.railroadide.railroad.ide.projectexplorer;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.Commands;
import dev.railroadide.railroad.command.ExplorerTarget;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexCoordinator;
import dev.railroadide.railroad.ide.projectexplorer.dialog.CopyModalDialog;
import dev.railroadide.railroad.ide.projectexplorer.dialog.CreateFileDialog;
import dev.railroadide.railroad.ide.projectexplorer.dialog.DeleteDialog;
import dev.railroadide.railroad.ide.projectexplorer.task.FileCopyTask;
import dev.railroadide.railroad.ide.projectexplorer.task.SearchTask;
import dev.railroadide.railroad.ide.projectexplorer.task.WatchTask;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.ShutdownHooks;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Project filesystem browser with search, file operations, watching, and editor navigation.
 */
public class ProjectExplorerPane extends RRVBox implements WatchTask.FileChangeListener, AutoCloseable {
    private static boolean fileChangeListenerEnabled = true;
    private final Project project;
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private final ProjectLanguageIndexCoordinator projectLanguageIndexCoordinator;
    private final StringProperty messageProperty = new SimpleStringProperty();
    private final TreeView<PathItem> treeView = new TreeView<>();
    private final TextField searchField;
    private final ObservableList<String> searchListItems = FXCollections.observableArrayList();
    private final StringProperty searchProperty = new SimpleStringProperty();
    private final List<String> searchList = new ArrayList<>();
    private final ShutdownHooks.Registration shutdownRegistration;
    private boolean closed;

    /**
     * Builds the project tree and starts filesystem watching and index warming.
     *
     * @param project project displayed by the explorer
     */
    public ProjectExplorerPane(Project project) {
        this.project = project;
        this.projectLanguageIndexCoordinator = new ProjectLanguageIndexCoordinator(project);
        Path rootPath = project.getPath();
        getStyleClass().add("rr-project-explorer");

        this.searchField = new RRTextField("railroad.ide.project_explorer.search_field");
        this.searchField.getStyleClass().add("rr-search-field");

        var header = createModernHeader(project);

        this.treeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        this.treeView.setRoot(new PathTreeItem(new PathItem(rootPath)));
        this.treeView.setEditable(true);
        this.treeView.getStyleClass().add("rr-tree-view");
        this.treeView.setCellFactory(_ -> {
            var cell = new PathTreeCell(messageProperty);
            handleDragDrop(cell);
            return cell;
        });
        this.treeView.getSelectionModel().selectedItemProperty().addListener((_, _, selectedItem) -> {
            if (selectedItem == null)
                return;

            Path selectedPath = selectedItem.getValue().getPath();
            if (Files.isRegularFile(selectedPath)) {
                Services.EDITOR_TAB_MANAGER.openPreview(selectedPath);
            }
        });
        this.treeView.getRoot().setExpanded(true);
        this.treeView.prefHeightProperty().bind(heightProperty().subtract(60));
        sortTreeItems(this.treeView.getRoot());

        handleSearchEvents(rootPath);

        warmProjectLanguageIndexes();

        var watchTask = new WatchTask(rootPath, this);
        this.executorService.submit(watchTask);

        getChildren().addAll(header, this.treeView);

        KeybindHandler.registerCapture(KeybindContexts.of("railroad:project_explorer"), this.treeView);

        shutdownRegistration = ShutdownHooks.registerHook(this.executorService::shutdownNow);
        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.PROJECT_EXPLORER, this);
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        shutdownRegistration.close();
        executorService.shutdownNow();
    }

    /**
     * Opens the selected file in the editor, or advances the selection for a directory.
     */
    public void openSelectedItem() {
        selectedTreeItem().ifPresent(selectedItem -> {
            PathItem item = selectedItem.getValue();
            if (Files.isDirectory(item.getPath())) {
                this.treeView.getSelectionModel().selectNext();
            } else {
                Services.EDITOR_TAB_MANAGER.open(item.getPath());
            }
        });
    }

    /**
     * Prompts to delete the selected filesystem entry.
     */
    public void deleteSelectedItem() {
        selectedTreeItem().ifPresent(selectedItem -> DeleteDialog.open(selectedItem.getValue().getPath()));
    }

    /**
     * Marks the selected entry for a clipboard move.
     */
    public void cutSelectedItem() {
        selectedTreeItem().ifPresent(selectedItem -> cut((PathTreeItem) selectedItem, this.treeView));
    }

    /**
     * Copies the selected filesystem entry to the clipboard.
     */
    public void copySelectedItem() {
        selectedTreeItem().ifPresent(selectedItem -> copy(selectedItem.getValue()));
    }

    /**
     * Pastes clipboard files into the selected entry.
     */
    public void pasteIntoSelectedItem() {
        selectedTreeItem().ifPresent(selectedItem -> paste(selectedItem.getValue()));
    }

    /**
     * Opens the creation dialog using the selected entry as its target path.
     *
     * @param type kind of file or directory to create
     */
    public void createFileInSelectedItem(FileCreateType type) {
        selectedTreeItem().ifPresent(
            selectedItem -> CreateFileDialog.open(getScene().getWindow(), selectedItem.getValue().getPath(), type));
    }

    /**
     * Starts inline editing of the selected entry's name.
     */
    public void renameSelectedItem() {
        selectedTreeItem().ifPresent(selectedItem -> commandTarget().rename());
    }

    /**
     * Reveals the selected entry in the system file explorer.
     */
    public void openSelectedItemInExplorer() {
        selectedTreeItem().ifPresent(selectedItem -> FileUtils.openInExplorer(selectedItem.getValue().getPath()));
    }

    /**
     * Opens a terminal for the selected filesystem entry.
     */
    public void openSelectedItemInTerminal() {
        selectedTreeItem().ifPresent(selectedItem -> FileUtils.openInTerminal(selectedItem.getValue().getPath()));
    }

    /**
     * Captures the current explorer selection for command dispatch.
     *
     * @return explicit selected-entry target
     */
    public ExplorerTarget commandTarget() {
        return new ExplorerTarget(treeView, treeView.getSelectionModel().getSelectedItem(),
            getScene() == null ? null : getScene().getWindow());
    }

    private ExplorerTarget rootCommandTarget() {
        return new ExplorerTarget(treeView, treeView.getRoot(), getScene().getWindow());
    }

    private Optional<TreeItem<PathItem>> selectedTreeItem() {
        return Optional.ofNullable(this.treeView.getSelectionModel().getSelectedItem());
    }

    private void warmProjectLanguageIndexes() {
        executorService.submit(projectLanguageIndexCoordinator::warmIndexes);
    }

    /**
     * Suspends filesystem change handling across project explorer panes.
     */
    public static void disableFileChangeListener() {
        fileChangeListenerEnabled = false;
    }

    /**
     * Enables filesystem change handling across project explorer panes.
     */
    public static void enableFileChangeListener() {
        fileChangeListenerEnabled = true;
    }

    /**
     * Places the entry on the clipboard for moving and updates cut markers in the tree.
     *
     * @param pathItem filesystem tree item
     * @param treeView tree containing the item and any previous cut selection
     */
    public static void cut(PathTreeItem pathItem, TreeView<PathItem> treeView) {
        pathItem.getValue().setCut(true);

        // get the clipboard content
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles() && clipboard.hasString() && clipboard.getString().equals("cut")) {
            for (File file : clipboard.getFiles()) {
                Path path = file.toPath();

                // we need to find the cells that match the path and set them to not cut
                TreeItem<PathItem> rootItem = treeView.getRoot();
                TreeItem<PathItem> item = ((ProjectExplorerPane) treeView.getParent()).findOrCreateTreeItem(rootItem,
                    path);
                if (item == null)
                    continue;

                item.getValue().setCut(false);
            }
        }

        var content = new ClipboardContent();
        content.putFiles(List.of(pathItem.getValue().getPath().toFile()));
        content.putString("cut");
        clipboard.setContent(content);
    }

    /**
     * Places the entry on the system clipboard for copying.
     *
     * @param item filesystem item to operate on
     */
    public static void copy(PathItem item) {
        var clipboard = Clipboard.getSystemClipboard();
        var content = new ClipboardContent();
        content.putFiles(List.of(item.getPath().toFile()));
        clipboard.setContent(content);
    }

    /**
     * Copies clipboard files into the target entry, prompting on conflicts and removing cut sources.
     *
     * @param item filesystem item to operate on
     */
    public static void paste(PathItem item) {
        var clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles()) {
            var files = clipboard.getFiles();
            boolean isCut = clipboard.hasString() && clipboard.getString().equals("cut");
            for (File file : files) {
                var targetPath = Path.of(item.getPath().toAbsolutePath().toString(), file.getName());
                if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                    var replaceProperty = new SimpleBooleanProperty();
                    CopyModalDialog.open(replaceProperty);
                    replaceProperty.addListener((_, _, newValue) -> {
                        if (newValue) {
                            new FileCopyTask(file.toPath(), targetPath).run();
                        }
                    });
                } else {
                    new FileCopyTask(file.toPath(), targetPath).run();
                }

                if (!isCut)
                    continue;

                Path path = file.toPath();
                if (Files.isDirectory(path)) {
                    FileUtils.deleteFolder(path);
                } else {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        Railroad.LOGGER.error("Error while deleting file", exception);
                    }
                }
            }
        }
    }

    /**
     * Recursively expands the subtree, loading children as needed.
     *
     * @param treeItem root of the subtree to update
     */
    public static void expandAll(TreeItem<PathItem> treeItem) {
        treeItem.setExpanded(true);
        for (TreeItem<PathItem> child : treeItem.getChildren()) {
            expandAll(child);
        }
    }

    /**
     * Recursively collapses the subtree.
     *
     * @param treeItem root of the subtree to update
     */
    public static void collapseAll(TreeItem<PathItem> treeItem) {
        treeItem.setExpanded(false);
        for (TreeItem<PathItem> child : treeItem.getChildren()) {
            collapseAll(child);
        }
    }

    private Node createModernHeader(Project project) {
        var header = new HBox();
        header.getStyleClass().add("project-explorer-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // Project icon and name
        var projectInfo = new HBox();
        projectInfo.getStyleClass().add("project-explorer-project-info");
        projectInfo.setAlignment(Pos.CENTER_LEFT);
        var projectIcon = new FontIcon(FontAwesomeSolid.FOLDER_OPEN);
        projectIcon.getStyleClass().add("project-icon");
        projectIcon.setIconSize(16);
        var projectName = new Label(project.getAlias());
        projectName.getStyleClass().add("project-name");
        projectName.setMinWidth(Label.USE_PREF_SIZE); // Prevent truncation
        projectInfo.getChildren().addAll(projectIcon, projectName);

        // Search field
        this.searchField.setPromptText("Search files...");
        this.searchField.getStyleClass().add("project-explorer-search-field");
        HBox.setHgrow(this.searchField, Priority.ALWAYS);

        // Action buttons
        var actionButtons = new HBox();
        actionButtons.getStyleClass().add("project-explorer-action-buttons");
        actionButtons.setAlignment(Pos.CENTER_RIGHT);

        var refreshButton = new RRButton("", FontAwesomeSolid.SYNC_ALT);
        refreshButton.setVariant(ButtonVariant.GHOST);
        refreshButton.setButtonSize(ButtonSize.SMALL);
        refreshButton.getStyleClass().add("project-explorer-button");
        refreshButton.setTooltip(new LocalizedTooltip("railroad.generic.refresh"));
        CommandButtons.bind(refreshButton, Commands.REFRESH_EXPLORER,
            () -> CommandContext.withArgument(project, this, this));

        var collapseAllButton = new RRButton("", FontAwesomeSolid.COMPRESS_ALT);
        collapseAllButton.setVariant(ButtonVariant.GHOST);
        collapseAllButton.setButtonSize(ButtonSize.SMALL);
        collapseAllButton.getStyleClass().add("project-explorer-button");
        collapseAllButton.setTooltip(new LocalizedTooltip("railroad.generic.collapse_all"));
        CommandButtons.bind(collapseAllButton, Commands.COLLAPSE_EXPLORER,
            () -> CommandContext.withArgument(project, this, rootCommandTarget()));

        var expandAllButton = new RRButton("", FontAwesomeSolid.EXPAND_ALT);
        expandAllButton.setVariant(ButtonVariant.GHOST);
        expandAllButton.setButtonSize(ButtonSize.SMALL);
        expandAllButton.getStyleClass().add("project-explorer-button");
        expandAllButton.setTooltip(new LocalizedTooltip("railroad.generic.expand_all"));
        CommandButtons.bind(expandAllButton, Commands.EXPAND_EXPLORER,
            () -> CommandContext.withArgument(project, this, rootCommandTarget()));

        actionButtons.getChildren().addAll(refreshButton, collapseAllButton, expandAllButton);

        // Layout: projectInfo | searchField | actionButtons
        header.getChildren().addAll(projectInfo, this.searchField, actionButtons);
        HBox.setHgrow(actionButtons, Priority.NEVER);
        HBox.setHgrow(projectInfo, Priority.NEVER);
        // The search field will take up the remaining space, but not shrink projectInfo

        return header;
    }

    /**
     * Refreshes the project tree using the existing filesystem scan.
     */
    public void refreshProjectExplorer() {
        Path rootPath = Path.of(this.treeView.getRoot().getValue().getPath().toString());
        this.treeView.setRoot(new PathTreeItem(new PathItem(rootPath)));
        this.treeView.getRoot().setExpanded(true);
        sortTreeItems(this.treeView.getRoot());
    }

    @Override
    public void onFileChange(Path path, WatchEvent.Kind<?> kind) {
        if (!fileChangeListenerEnabled)
            return;

        projectLanguageIndexCoordinator.handleFileChange(path, kind);
        if (kind != StandardWatchEventKinds.ENTRY_CREATE && kind != StandardWatchEventKinds.ENTRY_DELETE)
            return;

        Platform.runLater(() -> {
            // Refresh the tree view based on the kind of event
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                addPathToTree(path);
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                removePathFromTree(path);
            } /*
               * else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
               *
               * }
               */

            String searchValue = searchField.getText();
            if (!searchValue.isBlank()) {
                var searchTask = new SearchTask(treeView.getRoot().getValue().getPath(), searchValue);
                searchTask.setOnSucceeded(event -> updateTreeViewWithSearchResults(searchTask.getMatchedPaths()));
                executorService.submit(searchTask);
            }
        });
    }

    private void handleDragDrop(PathTreeCell cell) {
        cell.setOnDragDetected(event -> {
            TreeItem<PathItem> item = cell.getTreeItem();
            if (item != null && item.isLeaf()) {
                Dragboard dragboard = cell.startDragAndDrop(TransferMode.COPY);
                var content = new ClipboardContent();
                List<File> files = List.of(item.getValue().getPath().toFile());
                content.putFiles(files);
                dragboard.setContent(content);
                event.consume();
            }
        });

        cell.setOnDragOver(event -> {
            TreeItem<PathItem> item = cell.getTreeItem();
            if ((item != null && !item.isLeaf()) && event.getGestureSource() != cell
                && event.getDragboard().hasFiles()) {
                Path targetPath = cell.getTreeItem().getValue().getPath();
                var sourceCell = (PathTreeCell) event.getGestureSource();
                Path sourceParentPath = sourceCell.getTreeItem().getValue().getPath().getParent();
                if (sourceParentPath.compareTo(targetPath) != 0) {
                    event.acceptTransferModes(TransferMode.COPY);
                }
            }

            event.consume();
        });

        cell.setOnDragEntered(event -> {
            TreeItem<PathItem> item = cell.getTreeItem();
            if ((item != null && !item.isLeaf()) && event.getGestureSource() != cell
                && event.getDragboard().hasFiles()) {
                Path targetPath = cell.getTreeItem().getValue().getPath();
                var sourceCell = (PathTreeCell) event.getGestureSource(); // TODO: This breaks if from external source
                Path sourceParentPath = sourceCell.getTreeItem().getValue().getPath().getParent();
                if (sourceParentPath.compareTo(targetPath) != 0) {
                    cell.getStyleClass().add("project-explorer-drag-target");
                }
            }

            event.consume();
        });

        cell.setOnDragExited(event -> {
            cell.getStyleClass().remove("project-explorer-drag-target");
            event.consume();
        });

        cell.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;
            if (dragboard.hasFiles()) {
                Path sourcePath = dragboard.getFiles().getFirst().toPath();
                var targetPath = Path.of(
                    cell.getTreeItem().getValue().getPath().toAbsolutePath().toString(),
                    sourcePath.getFileName().toString());

                if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                    Platform.runLater(() -> {
                        var replaceProperty = new SimpleBooleanProperty();
                        CopyModalDialog.open(replaceProperty);
                        replaceProperty.addListener((_, _, newValue) -> {
                            if (newValue) {
                                this.executorService.submit(new FileCopyTask(sourcePath, targetPath));
                            }
                        });
                    });
                } else {
                    var task = new FileCopyTask(sourcePath, targetPath);
                    this.executorService.submit(task);

                    task.setOnSucceeded(_ -> Platform.runLater(() -> {
                        var item = new PathTreeItem(new PathItem(targetPath));
                        cell.getTreeItem().getChildren().add(item);
                    }));
                }

                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleSearchEvents(Path rootPath) {
        this.searchField.textProperty().addListener((_, _, newValue) -> {
            this.searchListItems.clear();

            if (newValue.isBlank()) {
                resetTreeView(rootPath);
                return;
            }

            var searchTask = new SearchTask(rootPath, newValue);
            this.searchList.clear();
            this.searchProperty.bind(searchTask.resultProperty());
            searchTask.setOnSucceeded((WorkerStateEvent _) -> {
                this.searchListItems.addAll(this.searchList);
                updateTreeViewWithSearchResults(searchTask.getMatchedPaths());
            });

            this.executorService.submit(searchTask);
        });

        this.searchProperty.addListener((_, _, newValue) -> {
            if (newValue != null) {
                this.searchList.add(newValue);
            }
        });
    }

    private void updateTreeViewWithSearchResults(List<Path> matchedPaths) {
        TreeItem<PathItem> rootItem = treeView.getRoot();
        rootItem.getChildren().clear();

        for (Path path : matchedPaths) {
            TreeItem<PathItem> parentItem = findOrCreateTreeItem(rootItem, path.getParent());
            if (isMissingPath(parentItem, path)) {
                TreeItem<PathItem> newItem = new PathTreeItem(new PathItem(path));
                parentItem.getChildren().add(newItem);
            }
        }

        filterTreeItems(rootItem, matchedPaths);
        sortTreeItems(rootItem);
        expandAllFolders(rootItem);
    }

    private TreeItem<PathItem> findOrCreateTreeItem(TreeItem<PathItem> rootItem, Path path) {
        if (path == null || path.equals(rootItem.getValue().getPath()))
            return rootItem;

        // Recursively create parent items
        TreeItem<PathItem> parentItem = findOrCreateTreeItem(rootItem, path.getParent());

        // Check if the current item already exists
        TreeItem<PathItem> currentItem = findTreeItemRecursive(parentItem, path);
        if (currentItem == null) {
            currentItem = new PathTreeItem(new PathItem(path));
            parentItem.getChildren().add(currentItem);
        }

        return currentItem;
    }

    private void filterTreeItems(TreeItem<PathItem> parentItem, List<Path> matchedPaths) {
        if (parentItem != null && !parentItem.getChildren().isEmpty()) {
            parentItem.getChildren().removeIf(child -> !isPathMatched(child.getValue().getPath(), matchedPaths));
            for (TreeItem<PathItem> child : parentItem.getChildren()) {
                filterTreeItems(child, matchedPaths);
            }
        }
    }

    private boolean isPathMatched(Path path, List<Path> matchedPaths) {
        for (Path matchedPath : matchedPaths) {
            if (matchedPath.startsWith(path))
                return true;
        }

        return false;
    }

    private void resetTreeView(Path rootPath) {
        TreeItem<PathItem> rootItem = treeView.getRoot();
        rootItem.getChildren().clear();

        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                    addPathToTree(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult preVisitDirectory(
                    @NotNull Path dir,
                    @NotNull BasicFileAttributes attrs
                ) {
                    addPathToTree(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error while walking file tree", exception);
        }

        sortTreeItems(rootItem);
    }

    private void sortTreeItems(TreeItem<PathItem> parentItem) {
        if (parentItem == null)
            return;

        ObservableList<TreeItem<PathItem>> children = getLoadedChildren(parentItem);
        if (!children.isEmpty()) {
            children.sort(new PathTreeItemComparator());
            for (TreeItem<PathItem> child : children) {
                sortTreeItems(child);
            }
        }
    }

    private void addPathToTree(Path path) {
        TreeItem<PathItem> parentItem = findTreeItem(path.getParent());
        if (parentItem != null && isMissingPath(parentItem, path)) {
            var newItem = new PathItem(path);
            TreeItem<PathItem> newTreeItem = new PathTreeItem(newItem);
            parentItem.getChildren().add(newTreeItem);
            sortTreeItems(parentItem);
        }
    }

    private void removePathFromTree(Path path) {
        TreeItem<PathItem> itemToRemove = findTreeItem(path);
        if (itemToRemove != null && itemToRemove.getParent() != null) {
            TreeItem<PathItem> parentItem = itemToRemove.getParent();
            parentItem.getChildren().remove(itemToRemove);
            sortTreeItems(parentItem);
        }
    }

    private TreeItem<PathItem> findTreeItem(Path path) {
        return findTreeItemRecursive(treeView.getRoot(), path);
    }

    private TreeItem<PathItem> findTreeItemRecursive(TreeItem<PathItem> currentItem, Path path) {
        if (currentItem.getValue().getPath().equals(path))
            return currentItem;

        for (TreeItem<PathItem> child : getLoadedChildren(currentItem)) {
            TreeItem<PathItem> result = findTreeItemRecursive(child, path);
            if (result != null)
                return result;
        }
        return null;
    }

    private static ObservableList<TreeItem<PathItem>> getLoadedChildren(TreeItem<PathItem> item) {
        if (item instanceof PathTreeItem pathTreeItem && !pathTreeItem.areChildrenLoaded())
            return FXCollections.emptyObservableList();

        return item.getChildren();
    }

    private void expandAllFolders(TreeItem<PathItem> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            for (TreeItem<PathItem> child : item.getChildren()) {
                expandAllFolders(child);
            }
        }
    }

    private boolean isMissingPath(TreeItem<PathItem> parentItem, Path path) {
        for (TreeItem<PathItem> child : parentItem.getChildren()) {
            if (child.getValue().getPath().equals(path))
                return false;
        }

        return true;
    }

    /**
     * Resolves a directory from the current explorer selection.
     *
     * @return selected directory, the selected file's parent, or the project root if nothing is selected
     */
    public Path getSelectedDirectory() {
        TreeItem<PathItem> selected = treeView.getSelectionModel().getSelectedItem();

        if (selected == null)
            return project.getPath();

        Path path = selected.getValue().getPath();
        return Files.isDirectory(path) ? path : path.getParent();
    }

    /**
     * Expands ancestors and selects the requested path within the project tree.
     *
     * @param path filesystem path to operate on
     */
    public void revealPath(Path path) {
        TreeItem<PathItem> item = findTreeItemForReveal(path);
        if (item != null) {
            TreeItem<PathItem> parent = item.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }

            treeView.getSelectionModel().select(item);
            treeView.scrollTo(treeView.getRow(item));
        }
    }

    private TreeItem<PathItem> findTreeItemForReveal(Path path) {
        if (path == null || treeView.getRoot() == null)
            return null;

        TreeItem<PathItem> currentItem = treeView.getRoot();
        Path rootPath = currentItem.getValue().getPath().toAbsolutePath().normalize();
        Path targetPath = path.toAbsolutePath().normalize();
        if (!targetPath.startsWith(rootPath))
            return null;

        Path currentPath = rootPath;
        for (Path segment : rootPath.relativize(targetPath)) {
            currentPath = currentPath.resolve(segment);
            Path expectedPath = currentPath;
            currentItem = currentItem.getChildren().stream()
                .filter(child -> child.getValue().getPath().toAbsolutePath().normalize().equals(expectedPath))
                .findFirst()
                .orElse(null);
            if (currentItem == null)
                return null;
        }

        return currentItem;
    }
}
