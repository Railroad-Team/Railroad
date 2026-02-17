package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;
import org.jspecify.annotations.NonNull;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public record DirectoryItem(Project project, Path path, List<GitFileChange> changes,
                            String displayTitle) implements ChangeItem {
    public DirectoryItem(Project project, Path path, List<GitFileChange> changes) {
        this(project, path, changes, path.getFileName().toString());
    }

    @Override
    public Node getIcon() {
        // TODO: Replace with some icon manager lookup
        var fontIcon = new FontIcon(FontAwesomeSolid.FOLDER);
        fontIcon.getStyleClass().add("git-directory-icon");
        fontIcon.setIconSize(16);
        return fontIcon;
    }

    @Override
    public String getTitle() {
        return displayTitle;
    }

    @Override
    public String getSubtitle() {
        return L18n.localize("git.commit.changes.directory.subtitle", String.valueOf(changes.size()));
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

        };
    }

    @Override
    public String getStyleClass() {
        return "git-directory-item";
    }

    @Override
    public @NonNull String toString() {
        return ChangeItem.formatTitle(getTitle(), getSubtitle());
    }
}
