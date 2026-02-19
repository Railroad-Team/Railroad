package dev.railroadide.railroad.ide.ui.git.commit;

import dev.railroadide.railroad.ide.ui.git.commit.changes.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRBorderPane;
import dev.railroadide.railroad.ui.RRCheckBoxTreeItem;
import dev.railroadide.railroad.ui.RRCheckBoxTreeView;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.util.GitRepository;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;

import java.nio.file.Path;
import java.util.*;

public class GitCommitChangesPane extends RRBorderPane {
    private final RRCheckBoxTreeView<ChangeItem> treeView = new RRCheckBoxTreeView<>();
    private final LocalizedText noChangesText = new LocalizedText("git.commit.changes.empty");

    private List<GitFileChange> lastChanges = Collections.emptyList();
    private Path lastRepoRoot;

    public GitCommitChangesPane(Project project) {
        getStyleClass().add("git-commit-changes-pane-root");

        treeView.setShowRoot(false);
        treeView.getStyleClass().add("git-commit-changes-pane");
        treeView.setCellFactory(view -> new CommitChangeTreeCell());

        project.getGitManager().repoStatusProperty().addListener((observable, oldValue, newValue) -> {
            List<GitFileChange> changes = newValue == null ? Collections.emptyList() : newValue.changes();
            Platform.runLater(() -> setProjectChanges(project, changes));
        });

        if (project.getGitManager().getRepoStatus() != null) {
            setProjectChanges(project, project.getGitManager().getRepoStatus().changes());
        } else {
            setChanges(Collections.emptyList());
        }

        setCenter(treeView);
        treeView.prefWidthProperty().bind(widthProperty());
        treeView.prefHeightProperty().bind(heightProperty());
    }

    private void setProjectChanges(Project project, List<GitFileChange> changes) {
        GitRepository repository = project.getGitManager().getGitRepository();
        Path repoRoot = repository == null ? null : repository.root();
        List<GitFileChange> safeChanges = changes == null ? Collections.emptyList() : List.copyOf(changes);
        if (isSameChanges(repoRoot, safeChanges))
            return;

        if (safeChanges.isEmpty() || repository == null) {
            setChanges(List.of());
            lastChanges = safeChanges;
            lastRepoRoot = repoRoot;
            return;
        }

        var root = new CommitTreeItem(RootItem.INSTANCE);
        var changesRoot = new CommitTreeItem(ChangesRootItem.INSTANCE);
        root.getChildren().add(changesRoot);
        Map<Path, CommitTreeItem> directories = new TreeMap<>(Comparator.comparing(Path::toString));
        Map<Path, List<GitFileChange>> directoryChanges = new HashMap<>();

        for (GitFileChange change : changes) {
            Path relativePath = repository.root().relativize(change.path());
            Path parent = relativePath.getParent();
            if (parent == null) {
                changesRoot.getChildren().add(new CommitTreeItem(new FileItem(project, change)));
                continue;
            }

            CommitTreeItem parentItem = null;
            Path current = Path.of("");
            for (Path part : parent) {
                current = current.resolve(part);
                List<GitFileChange> changesForDir = directoryChanges.computeIfAbsent(current, ignored -> new ArrayList<>());
                changesForDir.add(change);

                CommitTreeItem directoryItem = directories.get(current);
                if (directoryItem == null) {
                    Path directoryPath = repository.root().resolve(current).normalize();
                    directoryItem = new CommitTreeItem(new DirectoryItem(project, directoryPath, changesForDir));
                    directories.put(current, directoryItem);

                    Objects.requireNonNullElse(parentItem, changesRoot).getChildren().add(directoryItem);
                }

                parentItem = directoryItem;
            }

            Objects.requireNonNullElse(parentItem, changesRoot).getChildren()
                .add(new CommitTreeItem(new FileItem(project, change)));
        }

        changesRoot.collapseSingleChildDirectories();
        changesRoot.setExpanded(true);
        treeView.setRoot(root);
        setCenter(treeView);
        lastChanges = safeChanges;
        lastRepoRoot = repository.root();
    }

    private void setChanges(List<ChangeItem> items) {
        if (items == null || items.isEmpty()) {
            setCenter(noChangesText);
        } else {
            var root = new CommitTreeItem(RootItem.INSTANCE);
            var changesRoot = new CommitTreeItem(ChangesRootItem.INSTANCE);
            root.getChildren().add(changesRoot);
            for (ChangeItem item : items) {
                var treeItem = new CommitTreeItem(item);
                changesRoot.getChildren().add(treeItem);
            }

            changesRoot.setExpanded(true);
            treeView.setRoot(root);
            setCenter(treeView);
        }
    }

    private boolean isSameChanges(Path repoRoot, List<GitFileChange> changes) {
        return Objects.equals(lastRepoRoot, repoRoot) && Objects.equals(lastChanges, changes);
    }

    public List<GitFileChange> getSelectedChanges() {
        // TODO: Don't just get the first child, have a better way to access the changes root
        TreeItem<ChangeItem> root = treeView.getRoot().getChildren().getFirst();
        if (!(root instanceof RRCheckBoxTreeItem<ChangeItem> checkRoot))
            return Collections.emptyList();

        List<ChangeItem> selectedItems = checkRoot.getSelectedValues();
        return selectedItems.stream()
            .filter(FileItem.class::isInstance)
            .map(FileItem.class::cast)
            .map(FileItem::change)
            .toList();
    }

    public void expandAll() {
        TreeItem<ChangeItem> root = treeView.getRoot();
        if (root instanceof RRCheckBoxTreeItem<ChangeItem> checkRoot) {
            for (TreeItem<ChangeItem> child : checkRoot.getChildren()) {
                if (child instanceof RRCheckBoxTreeItem<ChangeItem> checkChild) {
                    checkChild.expandAll();
                }
            }
        }
    }

    public void collapseAll() {
        TreeItem<ChangeItem> root = treeView.getRoot();
        if (root instanceof RRCheckBoxTreeItem<ChangeItem> checkRoot) {
            for (TreeItem<ChangeItem> child : checkRoot.getChildren()) {
                if (child instanceof RRCheckBoxTreeItem<ChangeItem> checkChild) {
                    checkChild.collapseAll();
                }
            }
        }
    }
}
