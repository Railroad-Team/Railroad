package dev.railroadide.railroad.project.data;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides thread-safe access to the per-project data directory ({@code PROJECT/.railroad}).
 * Callers can store any number of files (JSON, binary, etc.) and use the provided helpers to
 * work with bytes, text, or JSON-serialized DTOs.
 * Path resolution creates missing files and parent directories, including during reads and existence checks.
 * Absolute paths, normalized parent traversal, and symbolic links resolving outside the data directory are rejected.
 */
public final class ProjectDataStore {
    private final Project project;

    private final CopyOnWriteArrayList<FileChangeListener> fileChangeListeners = new CopyOnWriteArrayList<>();
    private final Object watcherLock = new Object();
    private volatile WatchService watchService;
    private volatile ExecutorService watchExecutor;
    private volatile Path watchRoot;

    /**
     * Creates a data store bound to a project; the data directory is created on first access.
     *
     * @param project the project whose data is managed
     * @throws NullPointerException if the project is null
     */
    public ProjectDataStore(Project project) {
        this.project = Objects.requireNonNull(project, "project");
    }

    /**
     * Ensures the per-project data directory exists and returns it.
     *
     * @return the project root resolved with {@code .railroad}
     * @throws IllegalStateException if the project path is unset or the directory cannot be created
     */
    public Path dataDirectory() {
        Path projectPath = project.getPath();
        if (projectPath == null)
            throw new IllegalStateException("Project path is not set yet");

        Path dir = projectPath.resolve(".railroad");
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create project data directory at " + dir, exception);
        }

        return dir;
    }

    /**
     * Resolves a path within the per-project data directory.
     *
     * @param first the initial relative path segment
     * @param more additional path segments
     * @return the resolved path, creating an empty file and parent directories when absent
     * @throws IllegalArgumentException if the path is absolute or escapes the data directory
     * @throws IllegalStateException if creating or resolving the path fails
     */
    public Path resolve(String first, String... more) {
        return resolveInternal(Path.of(first, more));
    }

    /**
     * Registers a listener that will be notified whenever a file under {@code .railroad} changes.
     *
     * @param listener the listener to invoke on the background watcher thread
     * @throws NullPointerException if the listener is null
     * @throws IllegalStateException if the data directory or watcher cannot be created
     */
    public void addFileChangeListener(FileChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        fileChangeListeners.add(listener);
        ensureWatcherStarted();
    }

    /**
     * Removes a previously registered listener. When none remain the watcher is stopped.
     *
     * @param listener the listener to remove, or null to do nothing
     */
    public void removeFileChangeListener(FileChangeListener listener) {
        if (listener == null)
            return;

        fileChangeListeners.remove(listener);
        if (fileChangeListeners.isEmpty()) {
            stopWatcher(false);
        }
    }

    /**
     * Stores raw bytes at the given relative path (creating parent directories if needed).
     * Passing {@code null} bytes deletes the file.
     *
     * @param relativePath the file path relative to the data directory
     * @param bytes the replacement contents, or null to delete the file
     * @throws IllegalArgumentException if the path is absolute or escapes the data directory
     * @throws IllegalStateException if resolving, writing, or deleting the file fails
     */
    public synchronized void writeBytes(String relativePath, byte[] bytes) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path target = resolveInternal(Path.of(relativePath));

        if (bytes == null) {
            delete(relativePath);
            return;
        }

        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write project data file " + target, exception);
        }
    }

    /**
     * Writes UTF-8 text to the given relative path.
     *
     * @param relativePath the file path relative to the data directory
     * @param content the replacement text, or null to delete the file
     * @throws IllegalArgumentException if the path is absolute or escapes the data directory
     * @throws IllegalStateException if resolving, writing, or deleting the file fails
     */
    public synchronized void writeString(String relativePath, CharSequence content) {
        writeBytes(relativePath, content == null ? null : content.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Serializes a DTO as JSON and stores it at the given relative path (e.g. {@code configs/plugin.json}).
     *
     * @param <T> the value type
     * @param relativePath the file path relative to the data directory
     * @param value the value to serialize, or null to delete the file
     * @throws IllegalArgumentException if the path is absolute or escapes the data directory
     * @throws IllegalStateException if resolving, writing, or deleting the file fails
     */
    public synchronized <T> void writeJson(String relativePath, T value) {
        writeString(relativePath, value == null ? null : Railroad.GSON.toJson(value));
    }

    /**
     * Reads all bytes from the given relative path after resolving it, creating an empty file if absent.
     *
     * @param relativePath the file path relative to the data directory
     * @return the file contents, or empty if the file disappears before the existence check
     * @throws IllegalArgumentException if the path is absolute or escapes the data directory
     * @throws IllegalStateException if resolving or reading the file fails
     */
    public synchronized Optional<byte[]> readBytes(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path target = resolveInternal(Path.of(relativePath));

        if (Files.notExists(target))
            return Optional.empty();

        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read project data file " + target, exception);
        }
    }

    /**
     * Reads a UTF-8 encoded file as text.
     *
     * @param relativePath the file path relative to the data directory
     * @return the decoded contents, including an empty string for a newly created file, or empty if it disappears
     * @throws IllegalArgumentException if the path is absolute or escapes the data directory
     * @throws IllegalStateException if resolving or reading the file fails
     */
    public synchronized Optional<String> readString(String relativePath) {
        return readBytes(relativePath).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Deserializes JSON from the given relative path into {@code type}.
     *
     * @param <T> the deserialized value type
     * @param relativePath the JSON file path relative to the data directory
     * @param type the class to deserialize
     * @return the parsed value, or empty for a missing, blank, newly created, or JSON-null file
     * @throws IllegalArgumentException if the path is absolute or escapes the data directory
     * @throws IllegalStateException if resolving or reading the file fails
     * @throws com.google.gson.JsonParseException if the contents cannot be deserialized
     */
    public synchronized <T> Optional<T> readJson(String relativePath, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return readString(relativePath).filter(content -> !content.isBlank())
            .map(content -> Railroad.GSON.fromJson(content, type));
    }

    /**
     * Deletes the file at the given relative path, if it exists.
     *
     * @param relativePath the file path relative to the data directory
     * @throws IllegalArgumentException if the path is absolute or escapes the data directory
     * @throws IllegalStateException if resolving or deleting the file fails
     */
    public synchronized void delete(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path target = resolveInternal(Path.of(relativePath));

        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete project data file " + target, exception);
        }
    }

    /**
     * Checks existence after resolving the path, which creates an empty file if absent.
     *
     * @param relativePath the path relative to the data directory
     * @return whether the resolved path exists at the time of the check
     * @throws IllegalArgumentException if the path is absolute or escapes the data directory
     * @throws IllegalStateException if resolving the path fails
     */
    public synchronized boolean exists(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path target = resolveInternal(Path.of(relativePath));
        return Files.exists(target);
    }

    /**
     * Lists all files under the project data directory, returned as paths relative to it.
     *
     * @return the regular files found recursively, relative to the data directory
     * @throws IllegalStateException if the data directory cannot be created or enumerated
     */
    public synchronized List<Path> listFiles() {
        Path dir = dataDirectory();
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                .map(dir::relativize)
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to enumerate project data files under " + dir, exception);
        }
    }

    private Path resolveInternal(Path relative) {
        Objects.requireNonNull(relative, "relativePath");
        Path normalized = relative.normalize();
        if (normalized.isAbsolute())
            throw new IllegalArgumentException("Expected a relative path but got " + relative);

        for (Path part : normalized) {
            if ("..".equals(part.toString()))
                throw new IllegalArgumentException("Path escapes project data directory: " + relative);
        }

        Path path = dataDirectory();
        Path resolved = path.resolve(normalized);
        try {
            if (Files.notExists(resolved)) {
                Files.createDirectories(resolved.getParent());
                Files.createFile(resolved);
            }

            Path dataDirReal = path.toRealPath();
            Path resolvedReal = resolved.toRealPath();
            if (!resolvedReal.startsWith(dataDirReal))
                throw new IllegalArgumentException("Resolved path escapes project data directory: " + relative);

            return resolved;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to resolve real path for " + resolved, exception);
        }
    }

    private void ensureWatcherStarted() {
        synchronized (watcherLock) {
            if (watchExecutor != null || fileChangeListeners.isEmpty())
                return;

            try {
                Path root = dataDirectory();
                watchRoot = root;
                watchService = root.getFileSystem().newWatchService();
                registerDirectoryRecursive(root);

                watchExecutor = Executors.newSingleThreadExecutor(runnable -> {
                    var thread = new Thread(runnable, "ProjectDataStoreWatcher-" + project.getId());
                    thread.setDaemon(true);
                    return thread;
                });
                watchExecutor.submit(this::watchLoop);
            } catch (IOException exception) {
                stopWatcher(true);
                throw new IllegalStateException("Failed to start file watcher for project data", exception);
            }
        }
    }

    private void registerDirectoryRecursive(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isDirectory).forEach(this::registerDirectorySafely);
        }
    }

    private void registerDirectorySafely(Path dir) {
        WatchService service = watchService;
        if (service == null)
            return;

        try {
            dir.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException exception) {
            Railroad.LOGGER.warn("Failed to register directory {} for project data watcher", dir, exception);
        }
    }

    private void watchLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException exception) {
                    break;
                }

                Path dir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW)
                        continue;

                    @SuppressWarnings("unchecked")
                    Path name = ((WatchEvent<Path>) event).context();
                    Path child = dir.resolve(name);

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        try {
                            if (Files.isDirectory(child)) {
                                registerDirectoryRecursive(child);
                            }
                        } catch (IOException exception) {
                            Railroad.LOGGER.warn("Failed to register new directory {} for watcher", child, exception);
                        }
                    }

                    notifyFileChangeListeners(child, kind);
                }

                if (!key.reset())
                    break;
            }
        } finally {
            stopWatcher(true);
            if (!fileChangeListeners.isEmpty()) {
                ensureWatcherStarted();
            }
        }
    }

    private void notifyFileChangeListeners(Path absolutePath, WatchEvent.Kind<?> kind) {
        Path root = watchRoot;
        if (root == null || fileChangeListeners.isEmpty())
            return;

        Path relative;
        try {
            relative = root.relativize(absolutePath);
        } catch (IllegalArgumentException exception) {
            Railroad.LOGGER.warn("Watcher produced path outside of project data directory: {}", absolutePath);
            return;
        }

        for (FileChangeListener listener : fileChangeListeners) {
            try {
                listener.onFileChanged(relative, kind);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Project data file change listener threw an exception", exception);
            }
        }
    }

    private void stopWatcher(boolean force) {
        synchronized (watcherLock) {
            if (watchExecutor == null)
                return;

            if (!force && !fileChangeListeners.isEmpty())
                return;

            ExecutorService executor = watchExecutor;
            watchExecutor = null;

            WatchService service = watchService;
            watchService = null;
            watchRoot = null;

            if (service != null || executor != null) {
                try {
                    if (service != null) {
                        service.close();
                    }
                } catch (IOException exception) {
                    Railroad.LOGGER.warn("Failed to close project data watch service", exception);
                } finally {
                    if (executor != null) {
                        executor.shutdownNow();
                    }
                }
            }
        }
    }

    /** Receives file and directory changes from the background data directory watcher. */
    @FunctionalInterface
    public interface FileChangeListener {
        /**
         * Handles a creation, modification, or deletion event in the project data directory.
         *
         * @param relativePath the changed path relative to the data directory
         * @param kind the file system event kind
         */
        void onFileChanged(Path relativePath, WatchEvent.Kind<?> kind);
    }
}
