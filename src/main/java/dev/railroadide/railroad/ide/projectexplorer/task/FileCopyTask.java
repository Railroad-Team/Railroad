package dev.railroadide.railroad.ide.projectexplorer.task;

import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileCopyTask extends Task<Void> {
    private final Path source;
    private final Path target;

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
