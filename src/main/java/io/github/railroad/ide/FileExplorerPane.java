package io.github.railroad.ide;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import io.github.railroad.IDESetup;
import io.github.railroad.Railroad;
import io.github.railroad.ide.syntax_tests.ASTJavaEditorPane;
import io.github.railroad.ide.syntax_tests.RegexJavaEditorPane;
import io.github.railroad.ide.syntax_tests.TreeSitterJavaEditorPane;
import io.github.railroad.project.Project;
import io.github.railroad.utility.FileHandler;
import io.github.railroad.utility.ShutdownHooks;
import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class FileExplorerPane extends TreeView<Path> {
    public FileExplorerPane(Project project, Parent rootPane) {
        var projectRoot = new FileExplorerTreeItem(null, project);
        projectRoot.setExpanded(true);
        projectRoot.sort();

        setRoot(projectRoot);
        setCellFactory(param -> new FileExplorerCell(rootPane));

        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();

            Files.walkFileTree(Path.of(project.getPathString()), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                    return FileVisitResult.CONTINUE;
                }
            });

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(() -> {
                while (!Thread.interrupted()) {
                    try {
                        WatchKey key = watcher.take();
                        Path dir = (Path) key.watchable(); // This breaks when a directory is renamed since the key.watchable() is the old path
                        if (dir == null)
                            continue;

                        for (WatchEvent<?> event : key.pollEvents()) {
                            var kind = event.kind();
                            if (kind == StandardWatchEventKinds.OVERFLOW)
                                continue;

                            Path path = ((Path) event.context());
                            Path fullPath = dir.resolve(path);
                            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                                var parent = projectRoot.findParent(fullPath);
                                parent.getChildren().add(new FileExplorerTreeItem(projectRoot, fullPath));
                                ((FileExplorerTreeItem) parent).sort();

                                if (Files.isDirectory(fullPath)) {
                                    fullPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                                }
                            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                var parent = projectRoot.findParent(fullPath);
                                parent.getChildren().removeIf(treeItem -> treeItem.getValue().equals(fullPath));
                                ((FileExplorerTreeItem) parent).sort();
                            }
                        }

                        key.reset();
                    } catch (InterruptedException exception) {
                        Railroad.LOGGER.error("File explorer watcher interrupted.", exception);
                        break;
                    } catch (IOException exception) {
                        Railroad.LOGGER.error("Failed to watch file explorer.", exception);
                        break;
                    }
                }
            });

            ShutdownHooks.addHook(() -> {
                try {
                    executorService.shutdown();
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        Railroad.LOGGER.error("Failed to close file explorer watcher executor.");
                        executorService.shutdownNow();
                    }

                    watcher.close();
                } catch (IOException exception) {
                    Railroad.LOGGER.error("Failed to close file explorer watcher.", exception);
                } catch (InterruptedException exception) {
                    Railroad.LOGGER.error("File explorer watcher interrupted.", exception);
                }
            });
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to create file explorer watcher.", exception);
        }
    }

    public static class FileExplorerTreeItem extends TreeItem<Path> {
        private final TreeItem<Path> root;

        public FileExplorerTreeItem(TreeItem<Path> root, Path path) {
            super(path);
            this.root = root == null ? this : root;

            expandedProperty().addListener((observableValue, oldValue, newValue) -> {
                if (newValue) {
                    try (Stream<Path> paths = Files.walk(getValue(), 2)) {
                        paths.forEach(path1 -> {
                            if (path1.equals(getValue()))
                                return;

                            TreeItem<Path> parent = findParent(path1);
                            if (parent.getChildren().stream().noneMatch(treeItem -> treeItem.getValue().equals(path1))) {
                                parent.getChildren().add(new FileExplorerTreeItem(root, path1));
                                ((FileExplorerTreeItem) parent).sort();
                            }
                        });
                    } catch (IOException exception) {
                        Railroad.LOGGER.error("Failed to walk file tree.", exception);
                    }
                }
            });
        }

        public FileExplorerTreeItem(TreeItem<Path> root, Project project) {
            this(root, Path.of(project.getPathString()));
        }

        public void sort() {
            List<TreeItem<Path>> children = FXCollections.observableArrayList(getChildren());
            children.sort(new TreeItemComparator());
            getChildren().setAll(children);
        }

        private @NotNull TreeItem<Path> findParent(Path path) {
            TreeItem<Path> parent = findParent(path, root);
            return parent == null ? root : parent;
        }

        private @Nullable TreeItem<Path> findParent(Path path, TreeItem<Path> parent) {
            if (parent.getValue().equals(path.getParent())) {
                return parent;
            }

            for (var child : parent.getChildren()) {
                if (child.getValue().equals(path.getParent())) {
                    return child;
                }

                if (!child.getChildren().isEmpty()) {
                    var result = findParent(path, child);
                    if (result != null) {
                        return result;
                    }
                }
            }

            return null;
        }
    }

    public static class FileExplorerCell extends TreeCell<Path> {
        public FileExplorerCell(Parent rootPane) {

            setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.getClickCount() == 2) {
                    Path item = getItem();
                    if (item != null) {
                        if (Files.isDirectory(item)) {
                            var treeItem = getTreeItem();
                            treeItem.setExpanded(!treeItem.isExpanded());
                        } else {
                            var bestPane = IDESetup.findBestPaneForFiles(rootPane);
                            bestPane.ifPresent(detachableTabPane -> {
                                DetachableTabPane tabPane = bestPane.get();
                                var textEditorPane = new CodeEditorPane(item);
                                tabPane.addTab(item.getFileName().toString(), new VirtualizedScrollPane<>(textEditorPane));
                            });
                        }
                    }
                }
            });
        }

        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getFileName().toString());
                setGraphic(FileHandler.getIcon(item));
            }
        }
    }

    public static class TreeItemComparator implements Comparator<TreeItem<Path>> {
        @Override
        public int compare(TreeItem<Path> o1, TreeItem<Path> o2) {
            if (Files.isDirectory(o1.getValue()) && Files.isRegularFile(o2.getValue())) {
                return -1;
            } else if (Files.isRegularFile(o1.getValue()) && Files.isDirectory(o2.getValue())) {
                return 1;
            } else {
                return o1.getValue().compareTo(o2.getValue());
            }
        }
    }
}
