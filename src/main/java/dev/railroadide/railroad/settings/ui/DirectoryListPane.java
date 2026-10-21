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
    /**
     * Creates a directory list pane initialized with the supplied directories.
     *
     * @param initialDirectories directories to display initially
     */
    public DirectoryListPane(Collection<Path> initialDirectories) {
        super(
            initialDirectories,
            "railroad.settings.directories.empty",
            "railroad.settings.directories.add.tooltip",
            "railroad.settings.directories.remove.tooltip");
        getStyleClass().add("directory-list-pane");
    }

    /**
     * Creates an empty directory list pane.
     */
    public DirectoryListPane() {
        this(Collections.emptyList());
    }

    /**
     * Opens a directory chooser for selecting a directory to add.
     *
     * @return the selected directory, or {@code null} when selection is cancelled
     */
    @Override
    protected Path choosePath() {
        var chooser = new DirectoryChooser();
        chooser.setTitle(L18n.localize("railroad.settings.directories.add.title"));
        File selectedDirectory = chooser.showDialog(getScene().getWindow());
        if (selectedDirectory == null)
            return null;

        return selectedDirectory.toPath();
    }

    /**
     * Returns the directories currently displayed by this pane.
     *
     * @return a copy of the displayed directory paths
     */
    public List<Path> getDirectories() {
        return getPaths();
    }

    /**
     * Replaces the directories displayed by this pane.
     *
     * @param directories directories to display; {@code null} is treated as empty
     */
    public void setDirectories(Collection<Path> directories) {
        setPaths(directories);
    }
}
