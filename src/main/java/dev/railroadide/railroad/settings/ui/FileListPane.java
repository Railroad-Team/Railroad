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
    public FileListPane(Collection<Path> initialFiles) {
        super(
            initialFiles,
            "railroad.settings.files.empty",
            "railroad.settings.files.add.tooltip",
            "railroad.settings.files.remove.tooltip");
        getStyleClass().add("file-list-pane");
    }

    public FileListPane() {
        this(Collections.emptyList());
    }

    @Override
    protected Path choosePath() {
        var chooser = new FileChooser();
        chooser.setTitle(L18n.localize("railroad.settings.files.add.title"));
        File selectedFile = chooser.showOpenDialog(getScene().getWindow());
        if (selectedFile == null)
            return null;

        return selectedFile.toPath();
    }

    public List<Path> getFiles() {
        return getPaths();
    }

    public void setFiles(Collection<Path> files) {
        setPaths(files);
    }
}
