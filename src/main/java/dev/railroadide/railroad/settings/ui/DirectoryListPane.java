package dev.railroadide.railroad.settings.ui;

import dev.railroadide.railroad.localization.L18n;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Directory-specific implementation of {@link AbstractPathListPane}.
 */
public class DirectoryListPane extends AbstractPathListPane {
    public DirectoryListPane(Collection<Path> initialDirectories) {
        super(
            initialDirectories,
            "railroad.settings.directories.empty",
            "railroad.settings.directories.add.tooltip",
            "railroad.settings.directories.remove.tooltip");
        getStyleClass().add("directory-list-pane");
    }

    public DirectoryListPane() {
        this(Collections.emptyList());
    }

    @Override
    protected Path choosePath() {
        var chooser = new DirectoryChooser();
        chooser.setTitle(L18n.localize("railroad.settings.directories.add.title"));
        File selectedDirectory = chooser.showDialog(getScene().getWindow());
        if (selectedDirectory == null)
            return null;

        return selectedDirectory.toPath();
    }

    public List<Path> getDirectories() {
        return getPaths();
    }

    public void setDirectories(Collection<Path> directories) {
        setPaths(directories);
    }
}
