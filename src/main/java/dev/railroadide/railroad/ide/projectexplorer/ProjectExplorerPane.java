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
import dev.railroadide.railroad.settings.Settings;
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
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.fontawesome6.FontAwesomeRegular;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import javafx.beans.binding.Bindings;

/**
 * Project filesystem browser with search, file operations, watching, and editor navigation.
 */
public class ProjectExplorerPane extends RRVBox implements WatchTask.FileChangeListener, AutoCloseable {
    private static boolean fileChangeListenerEnabled = true;
    private final Project project;
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private final Future<ProjectLanguageIndexCoordinator> projectLanguageIndexCoordinator;
    private final StringProperty messageProperty = new SimpleStringProperty();
    private final TreeView<PathItem> treeView = new TreeView<>();
    private final TextField searchField;
    private final ShutdownHooks.Registration shutdownRegistration;
    private boolean closed;
    private final BiConsumer<Boolean, Boolean> compactPackagesListener = (_, _) -> Platform.runLater(() -> {
        if (!closed) {
            refreshProjectExplorer();
        }
    });

    /**
     * Builds the project tree and starts filesystem watching and index warming.
     *
     * @param project project displayed by the explorer
     */
    public ProjectExplorerPane(Project project) {
        this.project = project;
        this.projectLanguageIndexCoordinator = executorService.submit(() -> {
            try {
                var coordinator = new ProjectLanguageIndexCoordinator(project);
                coordinator.warmIndexes();
                return coordinator;
            } catch (RuntimeException exception) {
                Railroad.LOGGER.error("Failed to initialize project indexes for {}", project.getPath(), exception);
                throw exception;
            }
        });
        Path rootPath = project.getPath();
        getStyleClass().add("rr-project-explorer");

        this.searchField = new RRTextField("railroad.ide.project_explorer.search_field");
        this.searchField.getStyleClass().add("rr-search-field");

        var header = createModernHeader(project);

        this.treeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        this.treeView.setRoot(new PathTreeItem(new PathItem(rootPath), Settings.COMPACT_MIDDLE_PACKAGES.getValue()));
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
        this.treeView.setMinHeight(0);
        VBox.setVgrow(this.treeView, Priority.ALWAYS);
        sortTreeItems(this.treeView.getRoot());

        handleSearchEvents(rootPath);

        var watchTask = new WatchTask(rootPath, this);
        this.executorService.submit(watchTask);

        getChildren().addAll(header, this.treeView);

        KeybindHandler.registerCapture(KeybindContexts.of("railroad:project_explorer"), this.treeView);

        shutdownRegistration = ShutdownHooks.registerHook(this.executorService::shutdownNow);
        Services.UI_MANAGER.assignWhileAttached(UIIds.IDE.PROJECT_EXPLORER, this);
        Settings.COMPACT_MIDDLE_PACKAGES.addListener(compactPackagesListener);
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        Settings.COMPACT_MIDDLE_PACKAGES.removeListener(compactPackagesListener);
        shutdownRegistration.close();
        projectLanguageIndexCoordinator.cancel(true);
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
                TreeItem<PathItem> item = PathTreeItem.find(rootItem, path);
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
        var header = new VBox();
        header.getStyleClass().add("project-explorer-header");
        var titleRow = new HBox();
        titleRow.getStyleClass().add("project-explorer-title-row");
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // Project icon and name
        var projectInfo = new HBox();
        projectInfo.getStyleClass().add("project-explorer-project-info");
        projectInfo.setAlignment(Pos.CENTER_LEFT);
        var projectIcon = new FontIcon(FontAwesomeRegular.FOLDER);
        projectIcon.getStyleClass().add("project-icon");
        var projectName = new Label(project.getAlias());
        projectName.getStyleClass().add("project-name");
        projectName.setMinWidth(0);
        projectName.setTooltip(new Tooltip(project.getAlias()));
        projectInfo.setMinWidth(0);
        HBox.setHgrow(projectInfo, Priority.ALWAYS);
        projectInfo.getChildren().addAll(projectIcon, projectName);

        // Search field
        this.searchField.getStyleClass().add("project-explorer-search-field");
        this.searchField.setMaxWidth(Double.MAX_VALUE);

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

        // Keep search usable even when the dock is narrow or the project name is long.
        titleRow.getChildren().addAll(projectInfo, actionButtons);
        header.getChildren().addAll(titleRow, this.searchField);
        HBox.setHgrow(actionButtons, Priority.NEVER);
        actionButtons.setMinWidth(HBox.USE_PREF_SIZE);

        var searchButton = new RRButton("", FontAwesomeSolid.SEARCH);
        searchButton.setVariant(ButtonVariant.GHOST);
        searchButton.setButtonSize(ButtonSize.SMALL);
        searchButton.getStyleClass().add("project-explorer-button");
        var searchTooltip = new LocalizedTooltip("railroad.ide.project_explorer.search_field");
        searchButton.setTooltip(searchTooltip);
        searchButton.accessibleTextProperty().bind(searchTooltip.textProperty());

        var popupSearch = new RRTextField("railroad.ide.project_explorer.search_field");
        popupSearch.getStyleClass().add("project-explorer-search-field");
        popupSearch.textProperty().bindBidirectional(searchField.textProperty());
        popupSearch.prefWidthProperty().bind(searchField.fontProperty().map(font -> font.getSize() * 20));
        popupSearch.setMinWidth(TextField.USE_PREF_SIZE);
        var searchMenu = new ContextMenu(new CustomMenuItem(popupSearch, false));
        searchButton.setOnAction(_ -> {
            searchMenu.show(searchButton, Side.BOTTOM, 0, 0);
            popupSearch.requestFocus();
        });
        searchMenu.setOnHidden(_ -> searchButton.requestFocus());
        sceneProperty().addListener((_, _, scene) -> {
            if (scene == null) {
                searchMenu.hide();
            }
        });
        var narrow = Bindings.createBooleanBinding(
            () -> header.getWidth() - header.getInsets().getLeft()
                - header.getInsets().getRight() < searchField.getFont().getSize() * 14,
            header.widthProperty(), header.insetsProperty(), searchField.fontProperty());
        searchField.visibleProperty().bind(narrow.not());
        searchField.managedProperty().bind(searchField.visibleProperty());
        searchButton.visibleProperty().bind(narrow);
        searchButton.managedProperty().bind(searchButton.visibleProperty());
        narrow.addListener((_, _, isNarrow) -> {
            if (!isNarrow) {
                searchMenu.hide();
            }
        });
        actionButtons.getChildren().addFirst(searchButton);

        return header;
    }

    /**
     * Refreshes the project tree using the existing filesystem scan.
     */
    public void refreshProjectExplorer() {
        if (!searchField.getText().isBlank()) {
            search(searchField.getText());
            return;
        }
        List<Path> expanded = new ArrayList<>();
        collectExpandedPaths(treeView.getRoot(), expanded);
        Path selected = selectedTreeItem().map(item -> item.getValue().getPath()).orElse(null);
        treeView
            .setRoot(new PathTreeItem(new PathItem(project.getPath()), Settings.COMPACT_MIDDLE_PACKAGES.getValue()));
        treeView.getRoot().setExpanded(true);
        for (Path path : expanded) {
            TreeItem<PathItem> item = PathTreeItem.find(treeView.getRoot(), path);
            while (item != null) {
                item.setExpanded(true);
                item = item.getParent();
            }
        }
        if (selected != null) {
            revealPath(selected);
        }
    }

    private static void collectExpandedPaths(TreeItem<PathItem> item, List<Path> expanded) {
        if (!item.isExpanded())
            return;
        expanded.add(item.getValue().getPath());
        for (TreeItem<PathItem> child : getLoadedChildren(item)) {
            collectExpandedPaths(child, expanded);
        }
    }

    @Override
    public void onFileChange(Path path, WatchEvent.Kind<?> kind) {
        if (!fileChangeListenerEnabled)
            return;

        try {
            // This callback runs on the watcher thread. Wait here so changes observed
            // during initialization are applied after the initial index is ready.
            projectLanguageIndexCoordinator.get().handleFileChange(path, kind);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        } catch (CancellationException exception) {
            return;
        } catch (ExecutionException exception) {
            // Initialization already logged the failure; keep the explorer usable.
        }
        if (kind != StandardWatchEventKinds.ENTRY_CREATE && kind != StandardWatchEventKinds.ENTRY_DELETE)
            return;

        // A new sibling can split a compact package; deletion can join it again.
        Platform.runLater(() -> {
            if (closed)
                return;
            if (!searchField.getText().isBlank()) {
                search(searchField.getText());
                return;
            }
            List<Path> expanded = new ArrayList<>();
            collectExpandedPaths(treeView.getRoot(), expanded);
            TreeItem<PathItem> selected = treeView.getSelectionModel().getSelectedItem();
            ((PathTreeItem) treeView.getRoot()).refresh(path);
            for (Path expandedPath : expanded) {
                TreeItem<PathItem> item = PathTreeItem.find(treeView.getRoot(), expandedPath);
                while (item != null) {
                    item.setExpanded(true);
                    item = item.getParent();
                }
            }
            if (selected != null) {
                treeView.getSelectionModel()
                    .select(PathTreeItem.find(treeView.getRoot(), selected.getValue().getPath()));
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
                        refreshProjectExplorer();
                    }));
                }

                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleSearchEvents(Path rootPath) {
        searchField.textProperty().addListener((_, _, query) -> {
            if (query.isBlank()) {
                searchGeneration++;
                treeView.setRoot(new PathTreeItem(new PathItem(rootPath), Settings.COMPACT_MIDDLE_PACKAGES.getValue()));
                treeView.getRoot().setExpanded(true);
            } else {
                search(query);
            }
        });
    }

    private long searchGeneration;

    private void search(String query) {
        long generation = ++searchGeneration;
        var task = new SearchTask(project.getPath(), query);
        task.setOnSucceeded(_ -> {
            if (!closed && generation == searchGeneration) {
                updateTreeViewWithSearchResults(task.getMatchedPaths());
            }
        });
        executorService.submit(task);
    }

    private void updateTreeViewWithSearchResults(List<Path> matchedPaths) {
        var root = PathTreeItem.filtered(project.getPath());
        for (Path path : matchedPaths) {
            findOrCreateTreeItem(root, path);
        }
        PathTreeItem.compactFilteredPackages(root, Settings.COMPACT_MIDDLE_PACKAGES.getValue());
        sortTreeItems(root);
        treeView.setRoot(root);
        expandAllFolders(root);
    }

    private TreeItem<PathItem> findOrCreateTreeItem(TreeItem<PathItem> rootItem, Path path) {
        if (path == null || path.equals(rootItem.getValue().getPath()))
            return rootItem;
        TreeItem<PathItem> parent = findOrCreateTreeItem(rootItem, path.getParent());
        for (TreeItem<PathItem> child : parent.getChildren()) {
            if (child.getValue().getPath().equals(path))
                return child;
        }
        var item = PathTreeItem.filtered(path);
        parent.getChildren().add(item);
        return item;
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
        return PathTreeItem.find(treeView.getRoot(), path);
    }
}
