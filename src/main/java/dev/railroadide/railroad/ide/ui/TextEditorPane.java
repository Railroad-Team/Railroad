package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.defaults.DefaultDocument;
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroadpluginapi.events.FileEvent;
import dev.railroadide.railroadpluginapi.events.FileModifiedEvent;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Pair;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.PlainTextChange;
import org.reactfx.Subscription;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TextEditorPane extends CodeArea {
    private static final int[] FONT_SIZES = {6, 8, 10, 12, 14, 16, 18, 20, 24, 26, 28, 30, 36, 40, 48, 56, 60};
    private static final Duration SAVE_DELAY = Duration.ofMillis(400);
    private static final Duration CHANGE_DEBOUNCE = Duration.ofMillis(150);

    private static final ScheduledExecutorService SAVE_EXECUTOR = Executors.newScheduledThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
        namedThreadFactory("railroad-editor-save-"));

    static {
        ShutdownHooks.addHook(() -> {
            SAVE_EXECUTOR.shutdown();
            try {
                if (!SAVE_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    SAVE_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                SAVE_EXECUTOR.shutdownNow();
            }
        });
    }

    protected final Path filePath;

    private final AtomicReference<String> lastSavedText = new AtomicReference<>("");
    private final AtomicReference<String> pendingSnapshot = new AtomicReference<>("");
    private final AtomicLong lastLocalWrite = new AtomicLong(0L);

    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor(
        namedThreadFactory("railroad-editor-watch-"));

    private WatchService watchService;
    private Subscription changeSubscription;
    private ScheduledFuture<?> pendingSaveTask;
    private volatile boolean dirty;

    private int fontSizeIndex = 5;

    public TextEditorPane(Path item) {
        this.filePath = Objects.requireNonNull(item, "item");

        setParagraphGraphicFactory(LineNumberFactory.get(this));
        setMouseOverTextDelay(Duration.ofMillis(500));

        loadInitialContent();
        configureFontControls();
        subscribeToChanges();
        startExternalWatcher();

        moveTo(0);

        ShutdownHooks.addHook(() -> {
            watcherExecutor.shutdownNow();
            if (watchService != null) {
                try {
                    watchService.close();
                } catch (IOException ignored) {
                    // Nothing to do here
                }
            }

            if (changeSubscription != null) {
                changeSubscription.unsubscribe();
            }
        });
    }

    public static ThreadFactory namedThreadFactory(String prefix) {
        var counter = new AtomicInteger();
        return runnable -> {
            var thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private void loadInitialContent() {
        try {
            if (Files.exists(filePath)) {
                String content = Files.readString(filePath);
                replaceText(content);
                lastSavedText.set(content);
            } else {
                replaceText("");
                lastSavedText.set("");
            }

            dirty = false;
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to read file {}", filePath, exception);
            replaceText("");
            lastSavedText.set("");
            dirty = false;
        }
    }

    private void configureFontControls() {
        updateFontSizeClass();
        setOnKeyPressed(this::handleFontResizing);
    }

    private void updateFontSizeClass() {
        getStyleClass().removeIf(styleClass -> styleClass.startsWith("text-editor-font-size-"));
        getStyleClass().add("text-editor-font-size-" + FONT_SIZES[fontSizeIndex]);
    }

    private void subscribeToChanges() {
        changeSubscription = multiPlainChanges()
            .successionEnds(CHANGE_DEBOUNCE)
            .subscribe(changes -> {
                dirty = true;
                String snapshot = getText();
                pendingSnapshot.set(snapshot);

                List<FileModifiedEvent.Change> diff = changes.stream()
                    .map(change -> buildChange(snapshot, change))
                    .toList();
                publishFileModifiedEvent(diff);

                scheduleSave();
            });
    }

    private void scheduleSave() {
        synchronized (this) {
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
            }

            pendingSaveTask = SAVE_EXECUTOR.schedule(() -> {
                String snapshot = pendingSnapshot.get();
                persistSnapshot(snapshot);
            }, SAVE_DELAY.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void persistSnapshot(String snapshot) {
        if (snapshot == null)
            return;

        String lastSaved = lastSavedText.get();
        if (snapshot.equals(lastSaved))
            return;

        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(filePath, snapshot, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            lastSavedText.set(snapshot);
            dirty = false;
            lastLocalWrite.set(System.nanoTime());
            Railroad.EVENT_BUS.publish(new FileEvent(document(), FileEvent.EventType.SAVED));
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to write file {}", filePath, exception);
        }
    }

    private void publishFileModifiedEvent(List<FileModifiedEvent.Change> changes) {
        Railroad.EVENT_BUS.publish(new FileModifiedEvent(document(), changes));
    }

    private DefaultDocument document() {
        return new DefaultDocument(filePath.getFileName().toString(), filePath);
    }

    private void startExternalWatcher() {
        Path parent = filePath.getParent();
        if (parent == null)
            return;

        try {
            watchService = parent.getFileSystem().newWatchService();
            parent.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_CREATE);
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to start watch service for {}", filePath, exception);
            return;
        }

        watcherExecutor.submit(this::watchLoop);
    }

    private void watchLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW)
                        continue;

                    Path changed = (Path) event.context();
                    if (changed != null && changed.equals(filePath.getFileName())) {
                        handleExternalChange(kind);
                    }
                }

                if (!key.reset())
                    break;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException ignored) {
            // watcher closed during shutdown
        }
    }

    private void handleExternalChange(WatchEvent.Kind<?> kind) {
        long lastWriteNanos = lastLocalWrite.get();
        if (System.nanoTime() - lastWriteNanos < TimeUnit.MILLISECONDS.toNanos(250))
            return;

        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            Platform.runLater(() -> {
                replaceText("");
                lastSavedText.set("");
                dirty = false;
            });

            return;
        }

        if (dirty)
            return;

        try {
            if (!Files.exists(filePath)) {
                Platform.runLater(() -> {
                    replaceText("");
                    lastSavedText.set("");
                    dirty = false;
                });

                return;
            }

            String disk = Files.readString(filePath);
            if (disk.equals(lastSavedText.get()))
                return;

            Platform.runLater(() -> {
                int caret = getCaretPosition();
                replaceText(disk);
                moveTo(Math.min(caret, getLength()));
                lastSavedText.set(disk);
                dirty = false;
            });
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to reload file {}", filePath, exception);
        }
    }

    private static FileModifiedEvent.Change buildChange(String text, PlainTextChange change) {
        String inserted = change.getInserted();
        String removed = change.getRemoved();
        int position = change.getPosition();
        int netLength = change.getNetLength();

        Pair<Integer, Integer> start = getLineAndColumn(text, position);
        Pair<Integer, Integer> end = getLineAndColumn(text, position + netLength);

        return new FileModifiedEvent.Change(
            detectChangeType(inserted, removed),
            removed,
            inserted,
            new FileModifiedEvent.Range(start.getKey(), start.getValue(), end.getKey(), end.getValue())
        );
    }

    private static FileModifiedEvent.Change.Type detectChangeType(String inserted, String removed) {
        if (!inserted.isEmpty() && removed.isEmpty())
            return FileModifiedEvent.Change.Type.ADDED;
        else if (inserted.isEmpty() && !removed.isEmpty())
            return FileModifiedEvent.Change.Type.REMOVED;
        else
            return FileModifiedEvent.Change.Type.MODIFIED;
    }

    private static Pair<Integer, Integer> getLineAndColumn(String text, int position) {
        int line = 0;
        int column = 0;
        int limit = Math.min(position, text.length());

        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }

        return new Pair<>(line, column);
    }

    private void handleFontResizing(KeyEvent event) {
        if (!event.isControlDown())
            return;

        KeyCode code = event.getCode();
        if (code != KeyCode.EQUALS && code != KeyCode.MINUS)
            return;

        int newIndex = fontSizeIndex + (code == KeyCode.EQUALS ? 1 : -1);
        if (newIndex < 0 || newIndex >= FONT_SIZES.length)
            return;

        fontSizeIndex = newIndex;
        updateFontSizeClass();
        event.consume();
    }
}
