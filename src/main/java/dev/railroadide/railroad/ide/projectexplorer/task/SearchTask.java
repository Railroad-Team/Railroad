package dev.railroadide.railroad.ide.projectexplorer.task;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import lombok.Getter;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class SearchTask extends Task<Void> {
    private final Path path;
    private final String searchQuery;
    private final StringProperty resultProperty = new SimpleStringProperty();
    @Getter
    private final List<Path> matchedPaths = new ArrayList<>();

    public SearchTask(Path path, String searchQuery) {
        this.path = path;
        this.searchQuery = searchQuery;
    }

    @Override
    protected Void call() throws Exception {
        updateProgress(0, 0);
        updateMessage("Searching...");

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + this.searchQuery + "*");
        Files.walkFileTree(this.path, new SimpleFileVisitor<>() {
            private int count = 0;

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if(matcher.matches(file.getFileName())) {
                    this.count++;
                    matchedPaths.add(file);
                    SearchTask.this.resultProperty.setValue(file.toAbsolutePath().toString());
                    updateMessage("%d files found!".formatted(this.count));
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return null;
    }

    public StringProperty resultProperty() {
        return resultProperty;
    }
}
