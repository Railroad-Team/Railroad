package dev.railroadide.railroad.ide.projectexplorer.task;

import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

public class WatchTask extends Task<Void> {
    private final Path path;
    private final StringBuilder message = new StringBuilder();
    private final Map<WatchKey, Path> keys = new HashMap<>();
    private final FileChangeListener fileChangeListener;

    public WatchTask(Path path, FileChangeListener fileChangeListener) {
        this.path = path;
        this.fileChangeListener = fileChangeListener;
    }

    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    @Override
    protected Void call() throws IOException {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            registerAll(path, watcher);

            while (true) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException exception) {
                    break;
                }

                Path dir = keys.get(key);
                if (dir == null)
                    continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == OVERFLOW)
                        continue;

                    WatchEvent<Path> ev = cast(event);
                    Path name = ev.context();
                    Path child = dir.resolve(name);

                    message.append(child.toAbsolutePath());
                    message.append(getKindToMessage(event.kind()));
                    message.append(System.lineSeparator());
                    updateMessage(message.toString()); // to bind to the TextArea

                    fileChangeListener.onFileChange(child, event.kind());

                    if (event.kind() == ENTRY_CREATE) {
                        try {
                            if (Files.isDirectory(child)) {
                                registerAll(child, watcher);
                            }
                        } catch (IOException exception) {
                            updateMessage("Failed to register new directory: " + child + " due to " + exception.getMessage());
                        }
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);
                    if (keys.isEmpty())
                        break;
                }
            }
        }

        return null;
    }

    @Override
    protected void cancelled() {
        updateMessage("Watch task was cancelled");
    }

    private String getKindToMessage(WatchEvent.Kind<?> kind) {
        if (kind == ENTRY_CREATE) {
            return " is created";
        } else if (kind == ENTRY_DELETE) {
            return " is deleted";
        }
        return " is updated";
    }

    private void registerAll(final Path start, WatchService watcher) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir, watcher);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir, WatchService watcher) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    @FunctionalInterface
    public interface FileChangeListener {
        void onFileChange(Path path, WatchEvent.Kind<?> kind);
    }
}