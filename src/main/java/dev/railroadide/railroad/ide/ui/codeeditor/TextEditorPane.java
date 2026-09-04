package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.ui.editor.EditorSaveState;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.plugin.spi.events.DocumentEvent;
import dev.railroadide.railroad.plugin.spi.events.DocumentModifiedEvent;
import dev.railroadide.railroad.settings.IndentMode;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.IndexRange;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Font;
import javafx.util.Pair;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import java.util.function.BiConsumer;

@Slf4j
public class TextEditorPane extends CodeArea implements AutoCloseable {
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

    @Getter
    protected volatile Path filePath;
    @Getter
    protected final String languageId;

    private final AtomicReference<String> lastSavedText = new AtomicReference<>("");
    private final AtomicReference<String> pendingSnapshot = new AtomicReference<>("");
    private final AtomicLong pendingSnapshotVersion = new AtomicLong();

    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor(
        namedThreadFactory("railroad-editor-watch-"));

    private WatchService watchService;
    private Subscription changeSubscription;
    private Subscription dirtySubscription;
    private ScheduledFuture<?> pendingSaveTask;
    private final ShutdownHooks.Registration shutdownRegistration;
    private final BiConsumer<Integer, Integer> tabWidthListener = (_, _) -> applyEditorStyles();
    private final BiConsumer<String, String> fontFamilyListener = (_, _) -> applyEditorStyles();
    private final ReadOnlyObjectWrapper<EditorSaveState> saveState = new ReadOnlyObjectWrapper<>(this, "saveState",
        EditorSaveState.CLEAN);
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper(this, "dirty");
    private final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper(this, "saving");
    private final ReadOnlyBooleanWrapper saved = new ReadOnlyBooleanWrapper(this, "saved");
    private final ReadOnlyBooleanWrapper saveFailed = new ReadOnlyBooleanWrapper(this, "saveFailed");
    private final AtomicLong editVersion = new AtomicLong();
    private final AtomicLong savedVersion = new AtomicLong();
    private volatile boolean backingFileMissing;
    private volatile boolean discardChangesOnClose;
    private boolean closed;
    private final Object saveLock = new Object();
    // Guarded by saveLock. Saving is suspended until the FX thread resolves this snapshot.
    private String pendingExternalText;
    private String displayedExternalText;
    private ExternalChangeDialog externalChangeDialog;

    private int fontSizeIndex = 5;

    public TextEditorPane(Path item, String languageId) {
        this.filePath = Objects.requireNonNull(item, "item");
        this.languageId = Objects.requireNonNull(languageId, "languageId");
        dirty.bind(saveState.isNotEqualTo(EditorSaveState.CLEAN));
        saving.bind(saveState.isEqualTo(EditorSaveState.SAVING));
        saved.bind(saveState.isEqualTo(EditorSaveState.CLEAN));
        saveFailed.bind(saveState.isEqualTo(EditorSaveState.ERROR));

        setParagraphGraphicFactory(LineNumberFactory.get(this));
        setMouseOverTextDelay(Duration.ofMillis(500));

        loadInitialContent();
        configureFontControls();
        configureTabBehaviour();
        subscribeToChanges();
        startExternalWatcher();

        Platform.runLater(() -> {
            moveTo(0);
            scrollToPixel(0, 0);
        });

        shutdownRegistration = ShutdownHooks.registerHook(this::close);
    }

    @Override
    public void close() {
        synchronized (saveLock) {
            if (closed)
                return;

            closed = true;

            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }

            if (!discardChangesOnClose) {
                // Capture the editor directly as pendingSnapshot may still be behind the
                // multiPlainChanges debounce.
                persistSnapshot(getText(), editVersion.get());
            }
        }

        JavaFXUtils.runOnApplicationThread(this::closeExternalChangeDialog);
        shutdownRegistration.close();
        Settings.TAB_WIDTH.removeListener(tabWidthListener);
        Settings.EDITOR_FONT_FAMILY.removeListener(fontFamilyListener);

        watcherExecutor.shutdownNow();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException _) {
                // Nothing to do here
            }
        }

        if (changeSubscription != null) {
            changeSubscription.unsubscribe();
            changeSubscription = null;
        }
        if (dirtySubscription != null) {
            dirtySubscription.unsubscribe();
            dirtySubscription = null;
        }
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

            setSaveState(EditorSaveState.CLEAN);
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to read file {}", filePath, exception);
            replaceText("");
            lastSavedText.set("");
            setSaveState(EditorSaveState.CLEAN);
        }
    }

    private void configureFontControls() {
        updateFontSizeClass();
        addEventHandler(KeyEvent.KEY_PRESSED, this::handleFontResizing);
    }

    private void updateFontSizeClass() {
        getStyleClass().removeIf(styleClass -> styleClass.startsWith("text-editor-font-size-"));
        getStyleClass().add("text-editor-font-size-" + FONT_SIZES[fontSizeIndex]);
        applyEditorStyles();
    }

    private void configureTabBehaviour() {
        applyEditorStyles();
        Platform.runLater(this::applyEditorStyles);
        Settings.TAB_WIDTH.addListener(tabWidthListener);
        Settings.EDITOR_FONT_FAMILY.addListener(fontFamilyListener);

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Skip if any modifier keys are pressed to allow for shortcuts like Ctrl+Tab, Alt+Tab, etc.
            if (event.isControlDown() || event.isAltDown() || event.isMetaDown())
                return;

            if (event.getCode() == KeyCode.TAB) {
                event.consume();

                IndentMode indentMode = Settings.INDENT_MODE.getOrDefaultValue();
                int indentWidth = Math.max(1, Settings.INDENT_WIDTH.getOrDefaultValue());

                if (event.isShiftDown()) {
                    if (getSelection().getLength() == 0) {
                        unindentCurrentLine(indentWidth);
                    } else {
                        unindentSelected(indentWidth);
                    }
                } else {
                    if (getSelection().getLength() == 0) {
                        insertIndentAtCaret(indentMode, indentWidth);
                    } else {
                        indentSelected(indentMode, indentWidth);
                    }
                }
            }
        });
    }

    private void applyEditorStyles() {
        int tabWidth = Math.max(1, Settings.TAB_WIDTH.getOrDefaultValue());
        String fontFamily = Settings.EDITOR_FONT_FAMILY.getOrDefaultValue();
        Font font = Font.font(fontFamily, FONT_SIZES[fontSizeIndex]);
        double visualWidth = JavaFXUtils.measureTextWidth(" ".repeat(tabWidth), font);
        setStyle("-fx-font-family: \"" + fontFamily.replace("\"", "\\\"") + "\"; -fx-tab-size: " + visualWidth + "px;");
    }

    private void insertIndentAtCaret(IndentMode indentMode, int indentWidth) {
        indentWidth = Math.max(1, indentWidth);
        String indentString = indentMode == IndentMode.TABS ? "\t" : " ".repeat(indentWidth);
        int caret = getCaretPosition();
        insertText(caret, indentString);
        moveTo(caret + indentString.length());
    }

    private void unindentCurrentLine(int indentWidth) {
        int caret = getCaretPosition();
        String fullText = getText();
        int lineStart = fullText.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        int lineEnd = fullText.indexOf('\n', caret);
        if (lineEnd == -1) {
            lineEnd = fullText.length();
        }

        String line = fullText.substring(lineStart, lineEnd);
        String modified = unindentLine(line, indentWidth);
        if (modified.equals(line))
            return;

        replaceText(lineStart, lineEnd, modified);
        moveTo(Math.max(lineStart, caret - (line.length() - modified.length())));
    }

    private void unindentSelected(int indentWidth) {
        IndexRange selection = getSelection();
        int selectionStart = selection.getStart();
        int selectionEnd = selection.getEnd();
        String fullText = getText();

        int blockStart = fullText.lastIndexOf('\n', Math.max(0, selectionStart - 1)) + 1;
        int blockEnd = fullText.indexOf('\n', selectionEnd);
        if (blockEnd == -1) {
            blockEnd = fullText.length();
        }

        String block = fullText.substring(blockStart, blockEnd);
        String[] lines = block.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = unindentLine(lines[i], indentWidth);
        }

        String modified = String.join("\n", lines);

        replaceText(blockStart, blockEnd, modified);
        moveTo(blockStart);
        selectRange(blockStart, blockStart + modified.length());
    }

    private String unindentLine(String line, int indentWidth) {
        if (line.startsWith("\t"))
            return line.substring(1);

        int spacesToRemove = 0;
        while (spacesToRemove < line.length()
            && spacesToRemove < indentWidth
            && line.charAt(spacesToRemove) == ' ') {
            spacesToRemove++;
        }

        return line.substring(spacesToRemove);
    }

    private void indentSelected(IndentMode indentMode, int indentWidth) {
        IndexRange selection = getSelection();
        int selectionStart = selection.getStart();
        int selectionEnd = selection.getEnd();
        String fullText = getText();

        int blockStart = fullText.lastIndexOf('\n', Math.max(0, selectionStart - 1)) + 1;
        int blockEnd = fullText.indexOf('\n', selectionEnd);
        if (blockEnd == -1) {
            blockEnd = fullText.length();
        }

        String block = fullText.substring(blockStart, blockEnd);
        String indentString = indentMode == IndentMode.TABS ? "\t" : " ".repeat(indentWidth);
        String modified = indentString + block.replace("\n", "\n" + indentString);

        replaceText(blockStart, blockEnd, modified);
        moveTo(blockStart);
        selectRange(blockStart, blockStart + modified.length());
    }

    private void subscribeToChanges() {
        dirtySubscription = plainTextChanges().subscribe(_ -> {
            discardChangesOnClose = false;
            editVersion.incrementAndGet();
            setSaveState(EditorSaveState.DIRTY);
        });
        changeSubscription = multiPlainChanges()
            .successionEnds(CHANGE_DEBOUNCE)
            .subscribe(changes -> {
                String snapshot = getText();
                pendingSnapshot.set(snapshot);
                pendingSnapshotVersion.set(editVersion.get());

                List<DocumentModifiedEvent.Change> diff = changes.stream()
                    .map(change -> buildChange(snapshot, change))
                    .toList();
                publishFileModifiedEvent(diff);

                scheduleSave();
            });
    }

    private void scheduleSave() {
        synchronized (saveLock) {
            if (closed)
                return;

            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
            }

            pendingSaveTask = SAVE_EXECUTOR.schedule(() -> {
                synchronized (saveLock) {
                    if (closed)
                        return;

                    persistSnapshot(pendingSnapshot.get(), pendingSnapshotVersion.get());
                }
            }, SAVE_DELAY.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private boolean persistSnapshot(String snapshot, long snapshotVersion) {
        if (snapshot == null)
            return true;

        if (pendingExternalText != null)
            return false;

        if (backingFileMissing) {
            updateAfterSave(snapshotVersion, EditorSaveState.ERROR);
            Railroad.LOGGER.warn("Not saving {} because its backing file was deleted", filePath);
            return false;
        }

        // The watcher can arrive after autosave, so also check disk before writing.
        try {
            if (Files.exists(filePath)) {
                String disk = Files.readString(filePath);
                if (!disk.equals(lastSavedText.get())) {
                    queueExternalChange(disk);
                    return false;
                }
            } else {
                backingFileMissing = true;
                updateAfterSave(snapshotVersion, EditorSaveState.ERROR);
                return false;
            }
        } catch (IOException exception) {
            updateAfterSave(snapshotVersion, EditorSaveState.ERROR);
            Railroad.LOGGER.error("Failed to check file before saving {}", filePath, exception);
            return false;
        }

        String lastSaved = lastSavedText.get();
        if (snapshot.equals(lastSaved)) {
            savedVersion.set(snapshotVersion);
            updateAfterSave(snapshotVersion, EditorSaveState.CLEAN);
            return true;
        }

        updateForVersion(snapshotVersion, EditorSaveState.SAVING);
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(filePath, snapshot, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            lastSavedText.set(snapshot);
            savedVersion.set(snapshotVersion);
            updateAfterSave(snapshotVersion, EditorSaveState.CLEAN);
            Railroad.EVENT_BUS.publish(new DocumentEvent(document(), DocumentEvent.EventType.SAVED));
            return true;
        } catch (IOException | RuntimeException exception) {
            updateAfterSave(snapshotVersion, EditorSaveState.ERROR);
            Railroad.LOGGER.error("Failed to write file {}", filePath, exception);
            return false;
        }
    }

    /** Immediately writes the current editor contents, bypassing the autosave delay. */
    public boolean saveNow() {
        synchronized (saveLock) {
            if (closed)
                return saveState() == EditorSaveState.CLEAN;

            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }

            String snapshot = getText();
            long snapshotVersion = editVersion.get();
            pendingSnapshot.set(snapshot);
            pendingSnapshotVersion.set(snapshotVersion);
            return persistSnapshot(snapshot, snapshotVersion);
        }
    }

    /** Writes the current contents to a new path and makes it the backing file. */
    public boolean saveAs(Path newPath) {
        Path normalizedPath = Objects.requireNonNull(newPath, "New path cannot be null")
            .toAbsolutePath()
            .normalize();
        synchronized (saveLock) {
            if (closed)
                return false;

            if (normalizedPath.equals(filePath.toAbsolutePath().normalize()))
                return saveNow();

            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }

            String snapshot = getText();
            long snapshotVersion = editVersion.get();
            updateForVersion(snapshotVersion, EditorSaveState.SAVING);
            try {
                Path parent = normalizedPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                    normalizedPath,
                    snapshot,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

                filePath = normalizedPath;
                pendingExternalText = null;
                closeExternalChangeDialog();
                backingFileMissing = false;
                discardChangesOnClose = false;
                lastSavedText.set(snapshot);
                pendingSnapshot.set(snapshot);
                pendingSnapshotVersion.set(snapshotVersion);
                savedVersion.set(snapshotVersion);
                updateAfterSave(snapshotVersion, EditorSaveState.CLEAN);
                restartExternalWatcher();
                Railroad.EVENT_BUS.publish(new DocumentEvent(document(), DocumentEvent.EventType.SAVED));
                return true;
            } catch (IOException | RuntimeException exception) {
                updateAfterSave(snapshotVersion, EditorSaveState.ERROR);
                Railroad.LOGGER.error("Failed to write file {}", normalizedPath, exception);
                return false;
            }
        }
    }

    /** Prevents disposal from retrying a save after the user explicitly chose Discard. */
    public void discardChangesOnClose() {
        synchronized (saveLock) {
            discardChangesOnClose = true;
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }
        }
    }

    private void updateForVersion(long version, EditorSaveState state) {
        JavaFXUtils.runOnApplicationThread(() -> {
            if (editVersion.get() == version) {
                setSaveState(state);
            }
        });
    }

    private void updateAfterSave(long version, EditorSaveState currentVersionState) {
        JavaFXUtils.runOnApplicationThread(() -> setSaveState(editVersion.get() == version
            ? currentVersionState
            : EditorSaveState.DIRTY));
    }

    private void setSaveState(EditorSaveState state) {
        saveState.set(state);
    }

    public EditorSaveState saveState() {
        return saveState.get();
    }

    public ReadOnlyObjectProperty<EditorSaveState> saveStateProperty() {
        return saveState.getReadOnlyProperty();
    }

    public boolean dirty() {
        return dirty.get();
    }

    public ReadOnlyBooleanProperty dirtyProperty() {
        return dirty.getReadOnlyProperty();
    }

    public boolean saving() {
        return saving.get();
    }

    public ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    public boolean saved() {
        return saved.get();
    }

    public ReadOnlyBooleanProperty savedProperty() {
        return saved.getReadOnlyProperty();
    }

    public boolean saveFailed() {
        return saveFailed.get();
    }

    public ReadOnlyBooleanProperty saveFailedProperty() {
        return saveFailed.getReadOnlyProperty();
    }

    private void publishFileModifiedEvent(List<DocumentModifiedEvent.Change> changes) {
        if (backingFileMissing)
            return;

        Railroad.EVENT_BUS.publish(new DocumentModifiedEvent(document(), changes));
    }

    private Document document() {
        return new FileSystemDocument(filePath.getFileName().toString(), filePath, languageId);
    }

    /**
     * Rebinds this editor after its backing file has been moved or renamed. The editor
     * keeps its current in-memory content and begins saving and watching the new path.
     */
    public void rebind(Path newPath) {
        Path normalizedPath = Objects.requireNonNull(newPath, "New path cannot be null")
            .toAbsolutePath()
            .normalize();
        if (!Files.isRegularFile(normalizedPath))
            throw new IllegalArgumentException("Invalid editor path: " + normalizedPath);

        synchronized (saveLock) {
            if (closed)
                return;

            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }

            filePath = normalizedPath;
            pendingExternalText = null;
            closeExternalChangeDialog();
            backingFileMissing = false;
            String currentText = getText();
            boolean contentDirty;
            try {
                contentDirty = !currentText.equals(Files.readString(normalizedPath));
            } catch (IOException exception) {
                contentDirty = true;
                Railroad.LOGGER.warn("Failed to verify renamed editor file {}", normalizedPath, exception);
            }
            if (contentDirty && editVersion.get() == savedVersion.get()) {
                editVersion.incrementAndGet();
            }
            if (!contentDirty) {
                lastSavedText.set(currentText);
                savedVersion.set(editVersion.get());
            }
            setSaveState(contentDirty ? EditorSaveState.DIRTY : EditorSaveState.CLEAN);
            pendingSnapshot.set(currentText);
            pendingSnapshotVersion.set(editVersion.get());
            restartExternalWatcher();
            if (contentDirty) {
                scheduleSave();
            }
        }
    }

    /** Marks a deleted backing file without discarding the editor's in-memory text. */
    public void markBackingFileDeleted() {
        synchronized (saveLock) {
            if (closed)
                return;

            backingFileMissing = true;
            pendingExternalText = null;
            closeExternalChangeDialog();
            if (editVersion.get() == savedVersion.get()) {
                editVersion.incrementAndGet();
            }
            setSaveState(EditorSaveState.ERROR);
            pendingSnapshot.set(getText());
            pendingSnapshotVersion.set(editVersion.get());
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }
        }
    }

    public boolean isBackingFileMissing() {
        return backingFileMissing;
    }

    private void startExternalWatcher() {
        Path watchedPath = filePath;
        Path parent = watchedPath.getParent();
        if (parent == null)
            return;

        try {
            WatchService newWatchService = parent.getFileSystem().newWatchService();
            parent.register(newWatchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_CREATE);
            watchService = newWatchService;
            watcherExecutor.submit(() -> watchLoop(newWatchService, watchedPath));
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to start watch service for {}", watchedPath, exception);
            return;
        }
    }

    private void restartExternalWatcher() {
        WatchService previousWatchService = watchService;
        watchService = null;
        if (previousWatchService != null) {
            try {
                previousWatchService.close();
            } catch (IOException exception) {
                Railroad.LOGGER.warn("Failed to stop watcher for {}", filePath, exception);
            }
        }
        startExternalWatcher();
    }

    private void watchLoop(WatchService activeWatchService, Path watchedPath) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = activeWatchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW)
                        continue;

                    Path changed = (Path) event.context();
                    if (changed != null && changed.equals(watchedPath.getFileName())) {
                        handleExternalChange(watchedPath);
                    }
                }

                if (!key.reset())
                    break;
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException _) {
            // watcher closed during shutdown
        }
    }

    private void handleExternalChange(Path watchedPath) {
        synchronized (saveLock) {
            if (closed || !filePath.equals(watchedPath))
                return;

            try {
                // Inspect the current file, including delete/create pairs from atomic saves.
                if (!Files.exists(watchedPath)) {
                    backingFileMissing = true;
                    Platform.runLater(() -> {
                        if (filePath.equals(watchedPath) && !Files.exists(watchedPath)) {
                            markBackingFileDeleted();
                        }
                    });
                    return;
                }

                String disk = Files.readString(watchedPath);
                boolean wasMissing = backingFileMissing;
                backingFileMissing = false;
                if (wasMissing || pendingExternalText != null || !disk.equals(lastSavedText.get())) {
                    queueExternalChange(disk);
                }
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to reload file {}", watchedPath, exception);
            }
        }
    }

    private void queueExternalChange(String disk) {
        if (disk.equals(pendingExternalText))
            return;

        boolean alreadyQueued = pendingExternalText != null;
        pendingExternalText = disk;
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel(false);
            pendingSaveTask = null;
        }
        if (!alreadyQueued || displayedExternalText != null) {
            Path changedPath = filePath;
            Platform.runLater(() -> processExternalChange(changedPath));
        }
    }

    private void processExternalChange(Path changedPath) {
        synchronized (saveLock) {
            if (closed || !filePath.equals(changedPath) || pendingExternalText == null)
                return;

            String disk = pendingExternalText;
            if (disk.equals(getText()) || (!hasUnsavedChanges() && displayedExternalText == null)) {
                reloadExternalText(disk);
            } else {
                setSaveState(EditorSaveState.DIRTY);
                displayedExternalText = disk;
                showExternalChangeDialog(disk);
            }
        }
    }

    protected void showExternalChangeDialog(String disk) {
        if (externalChangeDialog == null) {
            externalChangeDialog = new ExternalChangeDialog(this, () -> resolveExternalChange(true),
                () -> resolveExternalChange(false));
        }
        externalChangeDialog.update(getText(), disk);
    }

    public boolean hasPendingExternalChange() {
        synchronized (saveLock) {
            return pendingExternalText != null;
        }
    }

    protected void resolveExternalChange(boolean reload) {
        synchronized (saveLock) {
            if (closed || pendingExternalText == null)
                return;

            try {
                String disk = Files.readString(filePath);
                // A choice only applies to the disk version the user has been shown.
                if (!disk.equals(displayedExternalText)) {
                    pendingExternalText = disk;
                    displayedExternalText = disk;
                    showExternalChangeDialog(disk);
                    return;
                }
                if (reload) {
                    reloadExternalText(disk);
                } else {
                    lastSavedText.set(disk);
                    pendingExternalText = null;
                    closeExternalChangeDialog();
                    saveNow();
                }
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to resolve external change for {}", filePath, exception);
                if (Files.notExists(filePath)) {
                    markBackingFileDeleted();
                } else if (externalChangeDialog != null) {
                    externalChangeDialog.showReadError();
                }
            }
        }
    }

    private void reloadExternalText(String disk) {
        int caret = getCaretPosition();
        if (!getText().equals(disk)) {
            replaceText(disk);
            moveTo(Math.min(caret, getLength()));
        }
        lastSavedText.set(disk);
        pendingSnapshot.set(disk);
        pendingSnapshotVersion.set(editVersion.get());
        pendingExternalText = null;
        backingFileMissing = false;
        markCurrentVersionSaved();
        closeExternalChangeDialog();
    }

    private void closeExternalChangeDialog() {
        displayedExternalText = null;
        if (externalChangeDialog != null) {
            externalChangeDialog.close();
            externalChangeDialog = null;
        }
    }

    private boolean hasUnsavedChanges() {
        return editVersion.get() != savedVersion.get();
    }

    private void markCurrentVersionSaved() {
        savedVersion.set(editVersion.get());
        setSaveState(EditorSaveState.CLEAN);
    }

    private static DocumentModifiedEvent.Change buildChange(String text, PlainTextChange change) {
        String inserted = change.getInserted();
        String removed = change.getRemoved();
        int position = change.getPosition();
        int netLength = change.getNetLength();

        Pair<Integer, Integer> start = getLineAndColumn(text, position);
        Pair<Integer, Integer> end = getLineAndColumn(text, position + netLength);

        return new DocumentModifiedEvent.Change(
            detectChangeType(inserted, removed),
            removed,
            inserted,
            new DocumentModifiedEvent.Range(start.getKey(), start.getValue(), end.getKey(), end.getValue()));
    }

    private static DocumentModifiedEvent.Change.Type detectChangeType(String inserted, String removed) {
        if (!inserted.isEmpty() && removed.isEmpty())
            return DocumentModifiedEvent.Change.Type.ADDED;
        else if (inserted.isEmpty() && !removed.isEmpty())
            return DocumentModifiedEvent.Change.Type.REMOVED;
        else
            return DocumentModifiedEvent.Change.Type.MODIFIED;
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
