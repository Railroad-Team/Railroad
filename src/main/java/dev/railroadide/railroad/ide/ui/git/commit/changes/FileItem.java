package dev.railroadide.railroad.ide.ui.git.commit.changes;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.ide.ui.git.diff.GitDiffPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tab;
import javafx.scene.input.MouseEvent;
import org.jspecify.annotations.NonNull;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Consumer;

public record FileItem(Project project, GitFileChange change) implements ChangeItem {
    @Override
    public Node getIcon() {
        // TODO: Replace with some icon manager lookup
        var fontIcon = new FontIcon(FontAwesomeSolid.FILE);
        fontIcon.getStyleClass().add("git-file-icon");
        fontIcon.setIconSize(16);
        return fontIcon;
    }

    @Override
    public String getTitle() {
        return change.path().getFileName().toString();
    }

    @Override
    public String getSubtitle() {
        return "";
    }

    @Override
    public ContextMenu getContextMenu(Project project) {
        return null; // TODO: Implement context menu
    }

    @Override
    public Consumer<Boolean> getSelectionHandler() {
        return isSelected -> {

        };
    }

    @Override
    public Consumer<MouseEvent> getDoubleClickHandler() {
        return event -> {
            if (event.getTarget() instanceof Node node) {
                Scene scene = node.getScene();
                openDiffForFile(scene, this);
            }
        };
    }

    @Override
    public String getStyleClass() {
        var joiner = new StringJoiner(" ");
        joiner.add("git-file-item");

        String suffix = "";
        if (change.isAdded()) {
            suffix = "-added";
        } else if (change.isDeleted()) {
            suffix = "-deleted";
        } else if (change.isRenamed()) {
            suffix = "-renamed";
        } else if (change.isCopied()) {
            suffix = "-copied";
        } else if (change.isConflict()) {
            suffix = "-unmerged";
        } else if (change.isUntracked()) {
            suffix = "-untracked";
        } else if (change.isModified()) {
            suffix = "-modified";
        }

        if (!suffix.isEmpty()) {
            joiner.add("git-file-item" + suffix);
        }

        return joiner.toString();
    }

    @Override
    public @NonNull String toString() {
        return ChangeItem.formatTitle(getTitle(), getSubtitle());
    }

    private void openDiffForFile(Scene scene, FileItem fileItem) {
        if (scene == null || scene.getRoot() == null)
            return;

        Parent root = scene.getRoot();
        Optional<DiffTabLocation> existing = findExistingDiffTab(root);
        DetachableTabPane tabPane = existing.map(DiffTabLocation::tabPane)
            .or(() -> IDESetup.findBestPaneForFiles(root))
            .orElse(null);
        if (tabPane == null)
            return;

        Tab diffTab = existing.map(DiffTabLocation::tab).orElseGet(() -> {
            GitDiffPane diffPane = new GitDiffPane(fileItem.project());
            Tab created = tabPane.addTab("Git Diff", diffPane);
            created.textProperty().bind(diffPane.titleProperty());
            return created;
        });

        tabPane.getSelectionModel().select(diffTab);
        GitDiffPane diffPane = (GitDiffPane) diffTab.getContent();
        if (!diffTab.textProperty().isBound()) {
            diffTab.textProperty().bind(diffPane.titleProperty());
        }
        diffPane.setFilePath(fileItem.change().path());
    }

    private Optional<DiffTabLocation> findExistingDiffTab(Parent parent) {
        for (DetachableTabPane pane : collectTabPanes(parent)) {
            Optional<Tab> diffTab = pane.getTabs().stream()
                .filter(tab -> tab.getContent() instanceof GitDiffPane)
                .findFirst();
            if (diffTab.isPresent())
                return Optional.of(new DiffTabLocation(pane, diffTab.get()));
        }

        return Optional.empty();
    }

    private List<DetachableTabPane> collectTabPanes(Parent parent) {
        List<DetachableTabPane> panes = new ArrayList<>();
        if (parent instanceof DetachableTabPane tabPane) {
            panes.add(tabPane);
        }

        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent childParent) {
                panes.addAll(collectTabPanes(childParent));
            }
        }

        return panes;
    }

    private record DiffTabLocation(DetachableTabPane tabPane, Tab tab) {
    }
}
