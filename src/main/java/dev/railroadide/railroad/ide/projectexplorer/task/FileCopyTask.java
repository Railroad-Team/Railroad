package dev.railroadide.railroad.ide.projectexplorer.task;

import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A JavaFX task that copies one filesystem entry and replaces an existing destination.
 */
public class FileCopyTask extends Task<Void> {
    private final Path source;
    private final Path target;

    /**
     * Creates a copy task for the supplied source and destination.
     *
     * @param source source path to copy
     * @param target destination path, replacing an existing entry
     */
    public FileCopyTask(Path source, Path target) {
        this.source = source;
        this.target = target;
    }

    @Override
    protected Void call() throws IOException {
        Files.copy(this.source, this.target, StandardCopyOption.REPLACE_EXISTING);
        return null;
    }
}
