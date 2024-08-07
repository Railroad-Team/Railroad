package io.github.railroad.ide.projectexplorer;

import io.github.railroad.Railroad;
import io.github.railroad.ide.projectexplorer.dialog.CopyModalDialog;
import io.github.railroad.ide.projectexplorer.dialog.CreateFileDialog;
import io.github.railroad.ide.projectexplorer.task.FileCopyTask;
import io.github.railroad.ide.projectexplorer.task.SearchTask;
import io.github.railroad.ide.projectexplorer.task.WatchTask;
import io.github.railroad.localization.ui.LocalizedTextField;
import io.github.railroad.project.Project;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.ShutdownHooks;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Insets;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectExplorerPane extends RRVBox implements WatchTask.FileChangeListener {
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private final StringProperty messageProperty = new SimpleStringProperty();
    private final TreeView<PathItem> treeView = new TreeView<>();

    private final TextField searchField;
    private final ObservableList<String> searchListItems = FXCollections.observableArrayList();
    private final StringProperty searchProperty = new SimpleStringProperty();
    private final List<String> searchList = new ArrayList<>();

    private static boolean fileChangeListenerEnabled = true;

    public static void disableFileChangeListener() {
        fileChangeListenerEnabled = false;
    }

    public static void enableFileChangeListener() {
        fileChangeListenerEnabled = true;
    }

    public ProjectExplorerPane(Project project) {
        Path rootPath = Path.of(project.getPathString());
        setPadding(new Insets(10));
        setSpacing(10);

        this.treeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        this.treeView.setRoot(new PathTreeItem(new PathItem(rootPath)));
        this.treeView.setEditable(true);
        this.treeView.setCellFactory(param -> {
            var cell = new PathTreeCell(messageProperty);
            handleDragDrop(cell);
            return cell;
        });
        this.treeView.getRoot().setExpanded(true);
        this.treeView.prefHeightProperty().bind(heightProperty());
        sortTreeItems(this.treeView.getRoot());

        this.searchField = new LocalizedTextField("railroad.ide.project_explorer.search_field");

        handleSearchEvents(rootPath);

        var watchTask = new WatchTask(rootPath, this);
        this.executorService.submit(watchTask);

        getChildren().addAll(searchField, this.treeView);

        ShutdownHooks.addHook(this.executorService::shutdown);
    }

    public static void createFile(Window window, Path path, FileCreateType type) {
        CreateFileDialog.open(window, path, type);
    }

    @Override
    public void onFileChange(Path path, WatchEvent.Kind<?> kind) {
        if (!fileChangeListenerEnabled)
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
            if(!searchValue.isBlank()) {
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
                var sourceCell = (PathTreeCell) event.getGestureSource();
                Path sourceParentPath = sourceCell.getTreeItem().getValue().getPath().getParent();
                if (sourceParentPath.compareTo(targetPath) != 0) {
                    cell.setStyle("-fx-background-color: -color-accent-4");
                }
            }

            event.consume();
        });

        cell.setOnDragExited(event -> {
            cell.setStyle(null);
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
                        CopyModalDialog.open(getScene().getWindow(), replaceProperty);
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
            if(!isPathAlreadyAdded(parentItem, path)) {
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
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    addPathToTree(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
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
        if (parentItem != null && !parentItem.getChildren().isEmpty()) {
            parentItem.getChildren().sort(new PathTreeItemComparator());
            for (TreeItem<PathItem> child : parentItem.getChildren()) {
                sortTreeItems(child);
            }
        }
    }

    private void addPathToTree(Path path) {
        TreeItem<PathItem> parentItem = findTreeItem(path.getParent());
        if (parentItem != null && !isPathAlreadyAdded(parentItem, path)) {
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
        for (TreeItem<PathItem> child : currentItem.getChildren()) {
            TreeItem<PathItem> result = findTreeItemRecursive(child, path);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private void expandAllFolders(TreeItem<PathItem> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            for (TreeItem<PathItem> child : item.getChildren()) {
                expandAllFolders(child);
            }
        }
    }

    private boolean isPathAlreadyAdded(TreeItem<PathItem> parentItem, Path path) {
        for (TreeItem<PathItem> child : parentItem.getChildren()) {
            if (child.getValue().getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }
}
