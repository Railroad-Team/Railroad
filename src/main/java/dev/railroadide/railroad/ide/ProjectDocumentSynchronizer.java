package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.sst.document.api.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Keeps project backing documents current independently of whether their editors are open.
 */
public final class ProjectDocumentSynchronizer implements AutoCloseable {
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", ".gradle", ".railroad");
    private final Path root;
    private final DocumentStore store;
    private final DocumentIdentityRegistry identities;
    private final Predicate<DocumentId> openTextDocument;
    private final Consumer<Runnable> dispatch;
    private final ExecutorService reads = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Railroad workspace documents");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<WatchKey, Path> directories = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<Void>> pending = ConcurrentHashMap.newKeySet();
    private final WatchService watcher;
    private volatile boolean closed;
    private final CompletableFuture<Void> initialScan;

    /**
     * Starts asynchronous discovery and recursive file watching for a project session.
     * Open text documents remain under their editor's save lock and watcher.
     *
     * @param root project directory to discover and watch
     * @param store project store receiving backing revisions
     * @param identities shared workspace document identities
     * @param openTextDocument predicate evaluated on the publication thread
     * @param dispatch publication dispatcher, normally the JavaFX application thread
     * @throws IOException if file watching cannot be initialized
     */
    public ProjectDocumentSynchronizer(
        Path root,
        DocumentStore store,
        DocumentIdentityRegistry identities,
        Predicate<DocumentId> openTextDocument,
        Consumer<Runnable> dispatch
    ) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.store = store;
        this.identities = identities;
        this.openTextDocument = openTextDocument;
        this.dispatch = dispatch;
        watcher = root.getFileSystem().newWatchService();
        initialScan = refresh(this.root);
        var thread = new Thread(this::watch, "Railroad workspace document watcher");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Returns completion of the initial discovery and its document publications.
     *
     * @return initial discovery completion; cancelled when the project closes
     */
    public CompletableFuture<Void> initialScan() {
        return initialScan;
    }

    /**
     * Reads and publishes one changed file or directory without waiting for a watcher event.
     * Failed reads preserve existing snapshots and complete exceptionally.
     *
     * @param changed file or directory under the project root
     * @return completion after publication, or cancellation after project closure
     */
    public CompletableFuture<Void> refresh(Path changed) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        pending.add(result);
        result.whenComplete((_, _) -> pending.remove(result));
        submit(changed.toAbsolutePath().normalize(), result);
        return result;
    }

    private void submit(Path path, CompletableFuture<Void> result) {
        if (closed) {
            result.cancel(false);
            return;
        }
        try {
            reads.execute(() -> {
                try {
                    if (Files.isDirectory(path)) {
                        scan(path, result);
                    } else {
                        read(path, result);
                    }
                } catch (IOException | RuntimeException exception) {
                    result.completeExceptionally(exception);
                    if (!closed) {
                        Railroad.LOGGER.warn("Failed to refresh workspace document {}", path, exception);
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            result.cancel(false);
        }
    }

    private void scan(Path directory, CompletableFuture<Void> result) throws IOException {
        List<CompletableFuture<Void>> files = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                if (closed || ignored(path))
                    return FileVisitResult.SKIP_SUBTREE;
                WatchKey key = path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                directories.put(key, path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && !ignored(path)) {
                    files.add(refresh(path));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path path, IOException exception) {
                Railroad.LOGGER.warn("Cannot read workspace path {}", path, exception);
                return FileVisitResult.CONTINUE;
            }
        });
        for (DocumentSnapshot snapshot : store.capture().documents().values()) {
            snapshot.uri().filePath().filter(path -> path.startsWith(directory) && Files.notExists(path))
                .ifPresent(path -> files.add(refresh(path)));
        }
        CompletableFuture.allOf(files.toArray(CompletableFuture[]::new))
            .whenComplete((_, error) -> {
                if (error == null) {
                    result.complete(null);
                } else {
                    result.completeExceptionally(error);
                }
            });
    }

    private boolean ignored(Path path) {
        if (!path.startsWith(root))
            return true;
        for (Path segment : root.relativize(path)) {
            if (IGNORED_DIRECTORIES.contains(segment.toString()))
                return true;
        }
        return false;
    }

    private void read(Path path, CompletableFuture<Void> result) throws IOException {
        if (closed || ignored(path)) {
            result.complete(null);
            return;
        }
        DocumentUri uri = DocumentUri.fromPath(path);
        DocumentIdentity observedIdentity = identities.findIdentity(uri).orElse(null);
        DocumentState expected = observedIdentity == null ? null : store.state(observedIdentity.id()).orElse(null);
        DocumentVersion highest = observedIdentity == null
            ? null
            : store.latestVersion(observedIdentity.id()).orElse(null);
        if (Files.notExists(path)) {
            dispatch.accept(() -> completeDeletion(path, result));
            return;
        }
        byte[] bytes = Files.readAllBytes(path);
        String language = LanguageSupportRegistry.resolveLanguageId(path);
        boolean textBased = LanguageSupportRegistry.get(language).map(LanguageSupport::isTextBased)
            .orElse(!Set.of("image", "binary").contains(language));
        String text = textBased ? StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() : null;
        dispatch.accept(() -> {
            if (closed) {
                result.cancel(false);
                return;
            }
            try {
                if (Files.notExists(path)) {
                    submit(path, result);
                    return;
                }
                DocumentIdentity identity = identities.identify(uri);
                if (openTextDocument.test(identity.id())) {
                    result.complete(null);
                    return;
                }
                if ((observedIdentity != null && !identity.id().equals(observedIdentity.id()))
                    || !Objects.equals(expected, store.state(identity.id()).orElse(null))
                    || !Objects.equals(highest, store.latestVersion(identity.id()).orElse(null))) {
                    submit(path, result);
                    return;
                }
                DocumentSnapshot backing = expected == null ? null : expected.backing();
                if (backing != null && backing.languageId().equals(language)
                    && (text != null && backing instanceof TextDocumentSnapshot previous && previous.text().equals(text)
                        || text == null && backing instanceof BinaryDocumentSnapshot binary
                            && binary.bytes().equals(ByteBuffer.wrap(bytes)))) {
                    result.complete(null);
                    return;
                }
                DocumentVersion version = highest == null ? DocumentVersion.initial() : highest.next();
                DocumentUri location = backing == null ? uri : backing.uri();
                DocumentSnapshot snapshot = text != null
                    ? new TextDocumentSnapshot(identity.id(), location, version, language, text, StandardCharsets.UTF_8)
                    : new BinaryDocumentSnapshot(identity.id(), location, version, language, bytes);
                try {
                    store.publishBacking(snapshot, expected);
                    result.complete(null);
                } catch (IllegalStateException exception) {
                    submit(path, result);
                }
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        });
    }

    private void completeDeletion(Path path, CompletableFuture<Void> result) {
        if (closed) {
            result.cancel(false);
            return;
        }
        if (!Files.notExists(path)) {
            submit(path, result);
            return;
        }
        try {
            DocumentId deletedId = identities.findIdentity(DocumentUri.fromPath(path))
                .map(DocumentIdentity::id).orElse(null);
            for (DocumentSnapshot current : store.capture().documents().values()) {
                DocumentState state = store.state(current.id()).orElse(null);
                if (state == null || state.backing() == null
                    || !current.id().equals(deletedId)
                        && !state.backing().uri().filePath().map(location -> location.startsWith(path)).orElse(false))
                    continue;
                if (openTextDocument.test(current.id()))
                    continue;
                store.removeBacking(current.id(), state.backing().version());
            }
            result.complete(null);
        } catch (IllegalStateException exception) {
            submit(path, result);
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
    }

    private void watch() {
        try {
            while (!closed) {
                WatchKey key = watcher.take();
                Path directory = directories.get(key);
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        refresh(root);
                    } else if (directory != null && event.context() instanceof Path child) {
                        refresh(directory.resolve(child));
                    }
                }
                if (!key.reset()) {
                    directories.remove(key);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException _) {
            // Project shutdown closes the blocking watcher.
        }
    }

    @Override
    public void close() {
        closed = true;
        reads.shutdownNow();
        pending.forEach(result -> result.cancel(false));
        try {
            watcher.close();
        } catch (IOException exception) {
            Railroad.LOGGER.warn("Failed to close workspace document watcher", exception);
        }
    }
}
