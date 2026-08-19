package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.railroad.ide.ui.IDEContentRouter;
import dev.railroadide.railroad.ide.ui.WorkspaceContentTargets;
import dev.railroadide.railroad.ide.ui.git.diff.GitDiffPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tab;
import javafx.scene.input.MouseEvent;
import org.jspecify.annotations.NonNull;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

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
            if (event.getTarget() instanceof Node) {
                openDiffForFile(this);
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

    private void openDiffForFile(FileItem fileItem) {
        IDEContentRouter.routeActive(WorkspaceContentTargets.GIT_EDITOR, tabPane -> {
            String tabId = fileItem.change().path().toAbsolutePath().normalize().toString();
            if (tabPane.getTabs().stream().anyMatch(tab -> tabId.equals(tab.getId()))) {
                tabPane.getTabs().stream()
                    .filter(tab -> tabId.equals(tab.getId()))
                    .findFirst()
                    .ifPresent(tab -> tabPane.getSelectionModel().select(tab));
                return;
            }

            var diffPane = new GitDiffPane(fileItem.project(), fileItem.change.path());
            var tab = new Tab("Diff " + fileItem.change().path().getFileName().toString(), diffPane);
            tab.setId(tabId);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
        });
    }
}
