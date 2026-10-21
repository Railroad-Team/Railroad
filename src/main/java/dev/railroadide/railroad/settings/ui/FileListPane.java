package dev.railroadide.railroad.settings.ui;

import dev.railroadide.railroad.localization.L18n;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * File-specific implementation of {@link AbstractPathListPane}.
 */
public class FileListPane extends AbstractPathListPane {
    /**
     * Creates a file list pane initialized with the supplied files.
     *
     * @param initialFiles files to display initially
     */
    public FileListPane(Collection<Path> initialFiles) {
        super(
            initialFiles,
            "railroad.settings.files.empty",
            "railroad.settings.files.add.tooltip",
            "railroad.settings.files.remove.tooltip");
        getStyleClass().add("file-list-pane");
    }

    /**
     * Creates an empty file list pane.
     */
    public FileListPane() {
        this(Collections.emptyList());
    }

    /**
     * Opens a file chooser for selecting a file to add.
     *
     * @return the selected file, or {@code null} when selection is cancelled
     */
    @Override
    protected Path choosePath() {
        var chooser = new FileChooser();
        chooser.setTitle(L18n.localize("railroad.settings.files.add.title"));
        File selectedFile = chooser.showOpenDialog(getScene().getWindow());
        if (selectedFile == null)
            return null;

        return selectedFile.toPath();
    }

    /**
     * Returns the files currently displayed by this pane.
     *
     * @return a copy of the displayed file paths
     */
    public List<Path> getFiles() {
        return getPaths();
    }

    /**
     * Replaces the files displayed by this pane.
     *
     * @param files files to display; {@code null} is treated as empty
     */
    public void setFiles(Collection<Path> files) {
        setPaths(files);
    }
}
