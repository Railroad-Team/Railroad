package dev.railroadide.railroad.ide.projectexplorer;

import com.kodedu.terminalfx.Terminal;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.impl.ImageLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.PlainTextLanguageSupport;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexCoordinator;
import dev.railroadide.railroad.ide.projectexplorer.dialog.CopyModalDialog;
import dev.railroadide.railroad.ide.projectexplorer.dialog.CreateFileDialog;
import dev.railroadide.railroad.ide.projectexplorer.dialog.DeleteDialog;
import dev.railroadide.railroad.ide.projectexplorer.task.FileCopyTask;
import dev.railroadide.railroad.ide.projectexplorer.task.SearchTask;
import dev.railroadide.railroad.ide.projectexplorer.task.WatchTask;
import dev.railroadide.railroad.ide.ui.IDEWelcomePane;
import dev.railroadide.railroad.ide.ui.ImageViewerPane;
import dev.railroadide.railroad.ide.ui.MarkdownPreviewPane;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.ide.ui.setup.TerminalFactory;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.DocumentEvent;
import dev.railroadide.railroad.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.ui.RRBorderPane;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
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
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectExplorerPane extends RRVBox implements WatchTask.FileChangeListener {
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

    public ProjectExplorerPane(Project project, RRBorderPane mainPane) {
        this.project = project;
        this.projectLanguageIndexCoordinator = new ProjectLanguageIndexCoordinator(project.getPath());
        Path rootPath = project.getPath();
        getStyleClass().add("rr-project-explorer");

        this.searchField = new RRTextField("railroad.ide.project_explorer.search_field");
        this.searchField.getStyleClass().add("rr-search-field");

        var header = createModernHeader(project);

        this.treeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        this.treeView.setRoot(new PathTreeItem(new PathItem(rootPath)));
        this.treeView.setEditable(true);
        this.treeView.getStyleClass().add("rr-tree-view");
        this.treeView.setCellFactory(param -> {
            var cell = new PathTreeCell(project, messageProperty, mainPane);
            handleDragDrop(cell);
            return cell;
        });
        this.treeView.getRoot().setExpanded(true);
        this.treeView.prefHeightProperty().bind(heightProperty().subtract(60));
        this.treeView.setOnKeyReleased(event -> {
            TreeItem<PathItem> selectedItem = this.treeView.getSelectionModel().getSelectedItem();
            if (selectedItem == null)
                return;

            PathItem item = selectedItem.getValue();
            if (event.getCode() == KeyCode.ENTER) {
                event.consume();

                if (Files.isDirectory(item.getPath())) {
                    this.treeView.getSelectionModel().selectNext();
                } else {
                    ProjectExplorerPane.openFile(project, item, mainPane);
                }

                return;
            }

            if (event.getCode() == KeyCode.DELETE) {
                event.consume();

                DeleteDialog.open(item.getPath());
                return;
            }

            if (event.getCode() == KeyCode.C && event.isControlDown()) {
                event.consume();

                ProjectExplorerPane.copy(item);
                return;
            }

            if (event.getCode() == KeyCode.X && event.isControlDown()) {
                event.consume();

                ProjectExplorerPane.cut((PathTreeItem) selectedItem, this.treeView);
                return;
            }

            if (event.getCode() == KeyCode.N && event.isControlDown()) {
                event.consume();

                CreateFileDialog.open(getScene().getWindow(), item.getPath(), event.isShiftDown() ? FileCreateType.FOLDER : FileCreateType.FILE);
                return;
            }

            if (event.getCode() == KeyCode.R && event.isControlDown()) {
                event.consume();

                ((PathTreeCell) selectedItem.getGraphic()).startEdit();
                return;
            }

            if (event.getCode() == KeyCode.O && event.isControlDown()) {
                event.consume();

                ProjectExplorerPane.openInExplorer(item.getPath());
                return;
            }

            if (event.getCode() == KeyCode.T && event.isControlDown()) {
                event.consume();

                ProjectExplorerPane.openInTerminal(item, mainPane);
                return;
            }
        });
        sortTreeItems(this.treeView.getRoot());

        handleSearchEvents(rootPath);

        warmProjectLanguageIndexes();

        var watchTask = new WatchTask(rootPath, this);
        this.executorService.submit(watchTask);

        getChildren().addAll(header, this.treeView);

        KeybindHandler.registerCapture(KeybindContexts.of("railroad:project_explorer"), this.treeView);

        ShutdownHooks.addHook(this.executorService::shutdownNow);
    }

    private void warmProjectLanguageIndexes() {
        executorService.submit(projectLanguageIndexCoordinator::warmIndexes);
    }

    public static void disableFileChangeListener() {
        fileChangeListenerEnabled = false;
    }

    public static void enableFileChangeListener() {
        fileChangeListenerEnabled = true;
    }

    public static void cut(PathTreeItem pathItem, TreeView<PathItem> treeView) {
        pathItem.getValue().setCut(true);

        // get the clipboard content
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles() && clipboard.hasString() && clipboard.getString().equals("cut")) {
            for (File file : clipboard.getFiles()) {
                Path path = file.toPath();

                // we need to find the cells that match the path and set them to not cut
                TreeItem<PathItem> rootItem = treeView.getRoot();
                TreeItem<PathItem> item = ((ProjectExplorerPane) treeView.getParent()).findOrCreateTreeItem(rootItem, path);
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

    public static void copy(PathItem item) {
        var clipboard = Clipboard.getSystemClipboard();
        var content = new ClipboardContent();
        content.putFiles(List.of(item.getPath().toFile()));
        clipboard.setContent(content);
    }

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
                    replaceProperty.addListener((observable, oldValue, newValue) -> {
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

    public static void openInExplorer(Path path) {
        FileUtils.openInExplorer(path);
    }

    public static void openInTerminal(PathItem item, RRBorderPane mainPane) {
        Path path = item.getPath();

        Optional<DetachableTabPane> pane = IDESetup.findBestPaneForTerminal(mainPane);
        pane.ifPresent(detachableTabPane -> {
            Terminal terminal = TerminalFactory.create(Files.isDirectory(path) ? path : path.getParent());
            if (!Files.isDirectory(path)) {
                terminal.onTerminalFxReady(() -> terminal.command(path.getFileName().toString()));
            }

            Tab terminalTab = detachableTabPane.addTab("Terminal (" +
                detachableTabPane.getTabs()
                    .stream()
                    .filter(tab -> tab.getContent() instanceof Terminal)
                    .count()
                + ")", terminal);

            detachableTabPane.getSelectionModel().select(terminalTab);
        });
    }

    public static void openFile(Project project, PathItem item, RRBorderPane mainPane) {
        Path path = item.getPath();
        if (Files.isDirectory(path))
            return;
        Path normalizedPath = path.toAbsolutePath().normalize();

        LanguageSupport support = LanguageSupportRegistry.find(path)
            .orElseGet(() -> FileUtils.isBinaryFile(path)
                ? (FileUtils.isImageFile(path) ? ImageLanguageSupport.INSTANCE : null)
                : PlainTextLanguageSupport.INSTANCE);
        if (support == null) {
            FileUtils.openInDefaultApplication(path);
            Railroad.EVENT_BUS.publish(new DocumentEvent(
                new FileSystemDocument(path.getFileName().toString(), path, LanguageSupportRegistry.resolveLanguageId(path)),
                DocumentEvent.EventType.OPENED
            ));
            return;
        }

        Optional<OpenTabLocation> existing = findOpenTab(mainPane, normalizedPath);
        if (existing.isPresent()) {
            OpenTabLocation location = existing.get();
            location.tabPane().getSelectionModel().select(location.tab());
            if (location.tab().getContent() instanceof TextEditorPane textEditorPane) {
                Services.DOCUMENT_EDITOR_STATE.setActiveEditor(textEditorPane, support.languageId());
            } else {
                Services.DOCUMENT_EDITOR_STATE.setActiveEditor(null, null);
            }

            return;
        }

        Optional<DetachableTabPane> pane = IDESetup.findBestPaneForFiles(mainPane);
        pane.ifPresent(detachableTabPane -> {
            String fileName = path.getFileName().toString();

            // Check if there's a welcome tab to replace
            Tab welcomeTab = detachableTabPane.getTabs().stream()
                .filter(tab -> tab.getContent() instanceof IDEWelcomePane)
                .findFirst()
                .orElse(null);

            EditorOpenView editorOpenView = support.open(project, path);
            if (editorOpenView == null) {
                FileUtils.openInDefaultApplication(path);
                Railroad.EVENT_BUS.publish(new DocumentEvent(new FileSystemDocument(path.getFileName().toString(), path, support.languageId()), DocumentEvent.EventType.OPENED));
                return;
            }

            TextEditorPane activeEditorPane = editorOpenView.activeEditor();
            Services.DOCUMENT_EDITOR_STATE.setActiveEditor(activeEditorPane, support.languageId());

            Node content = editorOpenView.content();
            Tab tab;
            if (welcomeTab != null) {
                welcomeTab.setContent(content);
                welcomeTab.setText(fileName);
                tab = welcomeTab;
            } else {
                tab = detachableTabPane.addTab(fileName, content);
            }

            detachableTabPane.getSelectionModel().select(tab);

            var document = new FileSystemDocument(fileName, path, support.languageId());
            Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.OPENED));
            Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.ACTIVATED));

            tab.setOnClosed(event -> {
                Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.CLOSED));
                if (tab.isSelected()) {
                    Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.DEACTIVATED));
                }
            });

            tab.setOnSelectionChanged(event -> {
                if (tab.isSelected()) {
                    Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.ACTIVATED));
                    Services.DOCUMENT_EDITOR_STATE.setActiveEditor(activeEditorPane, support.languageId());
                } else {
                    Railroad.EVENT_BUS.publish(new DocumentEvent(document, DocumentEvent.EventType.DEACTIVATED));
                    Services.DOCUMENT_EDITOR_STATE.setActiveEditor(null, null);
                }
            });
        });
    }

    private static Optional<OpenTabLocation> findOpenTab(RRBorderPane mainPane, Path path) {
        return collectTabPanes(mainPane).stream()
            .flatMap(tabPane -> tabPane.getTabs().stream()
                .filter(tab -> tabMatchesPath(tab, path))
                .map(tab -> new OpenTabLocation(tabPane, tab)))
            .findFirst();
    }

    private static boolean tabMatchesPath(Tab tab, Path path) {
        Node content = tab.getContent();
        if (content instanceof TextEditorPane textEditorPane)
            return textEditorPane.getFilePath().toAbsolutePath().normalize().equals(path);

        if (content instanceof MarkdownPreviewPane markdownPreviewPane)
            return markdownPreviewPane.getMarkdownFile().toAbsolutePath().normalize().equals(path);

        if (content instanceof ImageViewerPane imageViewerPane) {
            Path imagePath = imageViewerPane.getImagePath();
            return imagePath != null && imagePath.toAbsolutePath().normalize().equals(path);
        }

        return false;
    }

    private static List<DetachableTabPane> collectTabPanes(Parent parent) {
        List<DetachableTabPane> panes = new ArrayList<>();
        collectTabPanes(parent, panes);
        return panes;
    }

    private static void collectTabPanes(Parent parent, List<DetachableTabPane> panes) {
        if (parent instanceof DetachableTabPane tabPane) {
            panes.add(tabPane);
        }

        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent childParent) {
                collectTabPanes(childParent, panes);
            }
        }
    }

    public static void expandAll(TreeItem<PathItem> treeItem) {
        treeItem.setExpanded(true);
        for (TreeItem<PathItem> child : treeItem.getChildren()) {
            expandAll(child);
        }
    }

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
        refreshButton.setOnAction(e -> refreshProjectExplorer());

        var collapseAllButton = new RRButton("", FontAwesomeSolid.COMPRESS_ALT);
        collapseAllButton.setVariant(ButtonVariant.GHOST);
        collapseAllButton.setButtonSize(ButtonSize.SMALL);
        collapseAllButton.getStyleClass().add("project-explorer-button");
        collapseAllButton.setTooltip(new LocalizedTooltip("railroad.generic.collapse_all"));
        collapseAllButton.setOnAction(e -> ProjectExplorerPane.collapseAll(this.treeView.getRoot()));

        var expandAllButton = new RRButton("", FontAwesomeSolid.EXPAND_ALT);
        expandAllButton.setVariant(ButtonVariant.GHOST);
        expandAllButton.setButtonSize(ButtonSize.SMALL);
        expandAllButton.getStyleClass().add("project-explorer-button");
        expandAllButton.setTooltip(new LocalizedTooltip("railroad.generic.expand_all"));
        expandAllButton.setOnAction(e -> ProjectExplorerPane.expandAll(this.treeView.getRoot()));

        actionButtons.getChildren().addAll(refreshButton, collapseAllButton, expandAllButton);

        // Layout: projectInfo | searchField | actionButtons
        header.getChildren().addAll(projectInfo, this.searchField, actionButtons);
        HBox.setHgrow(actionButtons, Priority.NEVER);
        HBox.setHgrow(projectInfo, Priority.NEVER);
        // The search field will take up the remaining space, but not shrink projectInfo

        return header;
    }

    private void refreshProjectExplorer() {
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
            }/* else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {

            }*/

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
                @SuppressWarnings("ArraysAsListWithZeroOrOneArgument") // Using List.of would produce an unmodifiable list
                List<File> files = Arrays.asList(item.getValue().getPath().toFile());
                content.putFiles(files);
                dragboard.setContent(content);
                event.consume();
            }
        });

        cell.setOnDragOver(event -> {
            TreeItem<PathItem> item = cell.getTreeItem();
            if ((item != null && !item.isLeaf()) && event.getGestureSource() != cell && event.getDragboard().hasFiles()) {
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
            if ((item != null && !item.isLeaf()) && event.getGestureSource() != cell && event.getDragboard().hasFiles()) {
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
                    sourcePath.getFileName().toString()
                );

                if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                    Platform.runLater(() -> {
                        var replaceProperty = new SimpleBooleanProperty();
                        CopyModalDialog.open(replaceProperty);
                        replaceProperty.addListener((observable, oldValue, newValue) -> {
                            if (newValue) {
                                this.executorService.submit(new FileCopyTask(sourcePath, targetPath));
                            }
                        });
                    });
                } else {
                    var task = new FileCopyTask(sourcePath, targetPath);
                    this.executorService.submit(task);

                    task.setOnSucceeded(value -> Platform.runLater(() -> {
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
        this.searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            this.searchListItems.clear();

            if (newValue.isBlank()) {
                resetTreeView(rootPath);
                return;
            }

            var searchTask = new SearchTask(rootPath, newValue);
            this.searchList.clear();
            this.searchProperty.bind(searchTask.resultProperty());
            searchTask.setOnSucceeded((WorkerStateEvent stateEvent) -> {
                this.searchListItems.addAll(this.searchList);
                updateTreeViewWithSearchResults(searchTask.getMatchedPaths());
            });

            this.executorService.submit(searchTask);
        });

        this.searchProperty.addListener((observable, oldValue, newValue) -> {
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
            if (matchedPath.startsWith(path)) {
                return true;
            }
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
                public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
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
            PathItem newItem = new PathItem(path);
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
        if (currentItem.getValue().getPath().equals(path)) {
            return currentItem;
        }

        for (TreeItem<PathItem> child : getLoadedChildren(currentItem)) {
            TreeItem<PathItem> result = findTreeItemRecursive(child, path);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static ObservableList<TreeItem<PathItem>> getLoadedChildren(TreeItem<PathItem> item) {
        if (item instanceof PathTreeItem pathTreeItem && !pathTreeItem.areChildrenLoaded()) {
            return FXCollections.emptyObservableList();
        }

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
            if (child.getValue().getPath().equals(path)) {
                return false;
            }
        }

        return true;
    }

    private record OpenTabLocation(DetachableTabPane tabPane, Tab tab) {
    }
}
