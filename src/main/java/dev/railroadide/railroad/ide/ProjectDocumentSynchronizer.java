package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.utility.ProjectPathIdentityUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import dev.railroadide.railroad.ide.sst.document.api.ContentKind;

/**
 * Keeps project backing documents current independently of whether their editors are open.
 */
public final class ProjectDocumentSynchronizer implements AutoCloseable {
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", ".gradle", ".railroad");
    private static final Set<String> OUTPUT_DIRECTORIES = Set.of("build", "out", "target");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of("generated", "generated-sources",
        "generated-test-sources");
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
        BackgroundActivities.Handle scanning = BackgroundActivities.shared().start("railroad.activity.scanning_files");
        initialScan = refresh(this.root, scanning);
        initialScan.whenComplete((_, _) -> scanning.close());
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
        return refresh(changed, ActivityProgress.NONE);
    }

    private CompletableFuture<Void> refresh(Path changed, ActivityProgress progress) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        pending.add(result);
        result.whenComplete((_, _) -> pending.remove(result));
        submit(changed.toAbsolutePath().normalize(), result, progress);
        return result;
    }

    private void submit(Path path, CompletableFuture<Void> result, ActivityProgress progress) {
        if (closed) {
            result.cancel(false);
            return;
        }

        try {
            reads.execute(() -> {
                try {
                    if (Files.isDirectory(path)) {
                        scan(path, result, progress);
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

    private void scan(Path directory, CompletableFuture<Void> result, ActivityProgress progress) throws IOException {
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

        if (progress != ActivityProgress.NONE) {
            Runnable processed = progress.count("railroad.activity.files", ActivityProgress.Unit.COUNT, files.size());
            files.replaceAll(file -> file.whenComplete((_, _) -> processed.run()));
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
        return ignored(path, root);
    }

    /**
     * Reports whether this live synchronizer covers a file's physical location.
     *
     * @param path document location, including aliases to the project directory
     * @return whether project discovery and watching cover the path
     */
    public boolean covers(Path path) {
        return !closed && !ignored(ProjectPathIdentityUtils.normalize(path), ProjectPathIdentityUtils.normalize(root));
    }

    /**
     * Reports whether project discovery manages a location, independently of whether a synchronizer is running.
     *
     * @param root project directory
     * @param path location to classify
     * @return whether the path is inside the root and outside directories Railroad does not manage
     */
    public static boolean isWorkspacePath(Path root, Path path) {
        return !ignored(ProjectPathIdentityUtils.normalize(path), ProjectPathIdentityUtils.normalize(root));
    }

    /**
     * Builds the backing snapshot that discovery would publish for a file's bytes.
     *
     * @param id logical document identity
     * @param uri document location
     * @param version backing revision
     * @param path file location used to resolve the language; it need not exist
     * @param bytes file content, which classifies files that no language recognizes
     * @return text snapshot decoded as UTF-8 for text languages, otherwise a binary snapshot
     * @throws CharacterCodingException if a text language's content is not valid UTF-8
     */
    public static DocumentSnapshot backingSnapshot(
        DocumentId id,
        DocumentUri uri,
        DocumentVersion version,
        Path path,
        byte[] bytes
    ) throws CharacterCodingException {
        String language = LanguageSupportRegistry.resolveLanguageId(path, bytes);
        return snapshot(id, uri, version, language, decodeText(language, bytes), bytes);
    }

    private static String decodeText(String language, byte[] bytes) throws CharacterCodingException {
        boolean textBased = LanguageSupportRegistry.get(language)
            .map(support -> support.contentKind() == ContentKind.TEXT)
            .orElse(!Set.of("image", "binary").contains(language));
        return textBased ? StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() : null;
    }

    private static DocumentSnapshot snapshot(
        DocumentId id,
        DocumentUri uri,
        DocumentVersion version,
        String language,
        String text,
        byte[] bytes
    ) {
        return text != null
            ? new TextDocumentSnapshot(id, uri, version, language, text, StandardCharsets.UTF_8)
            : new BinaryDocumentSnapshot(id, uri, version, language, bytes);
    }

    /**
     * Build output directories are skipped unless they sit inside a source tree, except for their generated
     * source subtrees, which build models report as source roots.
     */
    private static boolean ignored(Path path, Path root) {
        if (!path.startsWith(root))
            return true;

        boolean insideSources = false;
        Path relative = root.relativize(path);
        for (int index = 0; index < relative.getNameCount(); index++) {
            String segment = relative.getName(index).toString();
            if (IGNORED_DIRECTORIES.contains(segment))
                return true;

            if (segment.equals("src")) {
                insideSources = true;
            } else if (!insideSources && OUTPUT_DIRECTORIES.contains(segment))
                return index + 1 < relative.getNameCount()
                    && !GENERATED_DIRECTORIES.contains(relative.getName(index + 1).toString());

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
        String language = LanguageSupportRegistry.resolveLanguageId(path, bytes);
        String text = decodeText(language, bytes);
        dispatch.accept(() -> {
            if (closed) {
                result.cancel(false);
                return;
            }

            try {
                if (Files.notExists(path)) {
                    submit(path, result, ActivityProgress.NONE);
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
                    submit(path, result, ActivityProgress.NONE);
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
                DocumentSnapshot snapshot = snapshot(identity.id(), location, version, language, text, bytes);
                try {
                    store.publishBacking(snapshot, expected);
                    result.complete(null);
                } catch (IllegalStateException exception) {
                    submit(path, result, ActivityProgress.NONE);
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
            submit(path, result, ActivityProgress.NONE);
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

                Path location = state.backing().uri().filePath().orElse(path);
                Optional<Path> surviving = identities.forgetDeletedAlias(current.id(),
                    current.id().equals(deletedId) ? path : location);
                if (surviving.isPresent()) {
                    if (Files.notExists(location)) {
                        store.relocate(current.id(), DocumentUri.fromPath(surviving.get()));
                    }

                    continue;
                }

                store.removeBacking(current.id(), state.backing().version());
            }

            result.complete(null);
        } catch (IllegalStateException exception) {
            submit(path, result, ActivityProgress.NONE);
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
