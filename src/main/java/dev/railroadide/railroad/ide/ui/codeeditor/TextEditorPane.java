package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.document.api.*;
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
import org.fxmisc.richtext.model.PlainTextChange;
import org.reactfx.Subscription;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Edits a file with observable save state, autosaving, and external-file change handling.
 */
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

    @Getter
    private final EditorGutter gutter;

    private final AtomicReference<String> lastSavedText = new AtomicReference<>("");
    private final AtomicReference<TextDocumentSnapshot> pendingSnapshot = new AtomicReference<>();

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
    protected final DocumentId documentId;
    private final boolean initialContentLoaded;
    private volatile boolean backingReadFailed;
    private volatile DocumentStore documentStore;
    private final AtomicLong savedVersion = new AtomicLong();
    private volatile boolean backingFileMissing;
    private volatile boolean discardChangesOnClose;
    private boolean applyingExternalReload;
    private boolean closed;
    private final Object saveLock = new Object();
    private String pendingExternalText;
    private String displayedExternalText;
    private ExternalChangeDialog externalChangeDialog;

    private int fontSizeIndex = 5;

    /**
     * Reveals and highlights a suspended debugger location on the JavaFX thread.
     *
     * @param line one-based source line; out-of-range lines are ignored
     */
    public void showExecutionLine(int line) {
        clearExecutionLine();
        if (closed || line < 1 || line > getParagraphs().size())
            return;

        int paragraph = line - 1;
        // Unfold enclosing regions so the execution location is actually visible.
        for (int index = 0; index <= paragraph; index++) {
            if (isFolded(index)) {
                unfoldParagraphs(Math.max(0, index - 1));
            }
        }

        List<String> styles = new ArrayList<>(getParagraph(paragraph).getParagraphStyle());
        styles.add("debug-execution-line");
        setParagraphStyle(paragraph, styles);
        moveTo(paragraph, 0);
        requestFollowCaret();
        requestFocus();
    }

    /**
     * Clears debugger highlighting without changing the caret or other paragraph styles.
     */
    public void clearExecutionLine() {
        for (int index = 0; index < getParagraphs().size(); index++) {
            var styles = new ArrayList<>(getParagraph(index).getParagraphStyle());
            if (styles.remove("debug-execution-line")) {
                setParagraphStyle(index, styles);
            }
        }
    }

    /**
     * Creates a file editor, loads its initial contents, and configures saving and file watching.
     *
     * @param documentId authoritative logical identity supplied by the editor owner
     * @param item file to open in the editor
     * @param languageId identifier of the document language
     * @throws NullPointerException if any argument is {@code null}
     */
    public TextEditorPane(DocumentId documentId, Path item, String languageId) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.filePath = Objects.requireNonNull(item, "item");
        this.languageId = Objects.requireNonNull(languageId, "languageId");
        dirty.bind(saveState.isNotEqualTo(EditorSaveState.CLEAN));
        saving.bind(saveState.isEqualTo(EditorSaveState.SAVING));
        saved.bind(saveState.isEqualTo(EditorSaveState.CLEAN));
        saveFailed.bind(saveState.isEqualTo(EditorSaveState.ERROR));

        gutter = new EditorGutter(this);
        setMouseOverTextDelay(Duration.ofMillis(500));

        initialContentLoaded = loadInitialContent();
        backingReadFailed = !initialContentLoaded;
        if (backingReadFailed) {
            setSaveState(EditorSaveState.ERROR);
        }
        pendingSnapshot.set(captureSnapshot());
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

    /**
     * Captures the current editor content and location for document publication and language features.
     * Call on the JavaFX thread; the returned snapshot remains readable after subsequent edits.
     * Every capture retains the document identity supplied at construction.
     *
     * @return immutable text revision with this editor's stable identity and current edit version
     */
    public final TextDocumentSnapshot captureSnapshot() {
        DocumentUri uri = DocumentUri.fromPath(filePath);
        return new TextDocumentSnapshot(documentId, uri, new DocumentVersion(editVersion.get()), languageId,
            DocumentOrigin.original(uri), getText(), StandardCharsets.UTF_8);
    }

    /**
     * Reports whether construction successfully read the initial text from the file.
     * Missing or unreadable files use empty fallback text, which must not be published
     * as backing content. Later edits and reloads do not change this initial-load result.
     *
     * @return true if the initial file read succeeded, including an empty file
     */
    public final boolean isInitialContentLoaded() {
        return initialContentLoaded;
    }

    /**
     * Restores the revision assigned to the editor's initially loaded, saved content.
     * Call on the JavaFX thread during initialisation, before attaching the document store.
     *
     * @param version saved backing revision assigned to the loaded content
     * @throws NullPointerException if the version is {@code null}
     */
    public final void restoreSavedVersion(DocumentVersion version) {
        Objects.requireNonNull(version, "version");
        editVersion.set(version.value());
        savedVersion.set(version.value());
        pendingSnapshot.set(captureSnapshot());
    }

    /**
     * Recovers a retained draft during initialization without treating restoration as a user edit.
     * Call on the JavaFX thread before attaching the store. Stale drafts retain a pending conflict.
     *
     * @param snapshot retained editor overlay
     * @param backingVersion current backing revision, or null when backing is absent
     * @param stale whether the draft needs resolution against that baseline
     * @throws NullPointerException if the snapshot is null
     * @throws IllegalArgumentException if the overlay belongs to another document
     */
    public final void restoreOverlay(TextDocumentSnapshot snapshot, DocumentVersion backingVersion, boolean stale) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!documentId.equals(snapshot.id()))
            throw new IllegalArgumentException("Overlay belongs to another document");
        applyingExternalReload = true;
        try {
            replaceText(snapshot.text());
        } finally {
            applyingExternalReload = false;
        }
        editVersion.set(snapshot.version().value());
        savedVersion.set(backingVersion == null ? -1 : backingVersion.value());
        pendingSnapshot.set(snapshot);
        setSaveState(backingReadFailed
            ? EditorSaveState.ERROR
            : snapshot.version().equals(backingVersion) && snapshot.text().equals(lastSavedText.get())
                ? EditorSaveState.CLEAN
                : EditorSaveState.DIRTY);
        if (stale && !backingReadFailed) {
            pendingExternalText = lastSavedText.get();
            Path restoredPath = filePath;
            Platform.runLater(() -> processExternalChange(restoredPath));
        }
    }

    /**
     * Associates this editor with the store that already contains its initial backing
     * snapshot. Subsequent text changes publish immutable overlays to this store.
     * Call on the JavaFX thread after the initial backing has been published or reused.
     *
     * @param documentStore project store that owns this document's backing snapshot
     * @throws NullPointerException if the store is {@code null}
     */
    public final void setDocumentStore(DocumentStore documentStore) {
        this.documentStore = Objects.requireNonNull(documentStore, "documentStore");
        documentStoreAttached();
    }

    /** Notifies subclasses after the initialized document has been attached to its store. */
    protected void documentStoreAttached() {
    }

    /** @return the associated project store, or {@code null} before initialization */
    protected final DocumentStore getDocumentStore() {
        return documentStore;
    }

    /**
     * Reports whether this editor has completed its close transition.
     *
     * @return whether this editor completed closure
     */
    public final boolean isEditorClosed() {
        return closed;
    }

    private boolean retainDraftOnClose;

    /**
     * Releases a forcibly removed editor without retrying persistence or discarding its
     * published overlay. Reopening the document can recover that draft from the store.
     */
    public final void closeRetainingDraft() {
        synchronized (saveLock) {
            retainDraftOnClose = true;
            close();
        }
    }

    @Override
    public void close() {
        synchronized (saveLock) {
            if (closed)
                return;

            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }

            if (!discardChangesOnClose && !retainDraftOnClose) {
                // Every edit updates the immutable pending snapshot before the autosave debounce.
                if (!persistSnapshot(pendingSnapshot.get()))
                    return;
            }
            if (!retainDraftOnClose) {
                DocumentStore store = documentStore;
                DocumentState state = store == null ? null : store.state(documentId).orElse(null);
                if (state != null && state.overlay() != null) {
                    store.removeOverlay(documentId, state.overlay().version());
                }
            }
            closed = true;
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

    /**
     * Creates a factory for daemon threads with a numbered name prefix.
     *
     * @param prefix prefix for incrementally numbered daemon thread names
     * @return factory that appends an increasing counter to the prefix
     */
    public static ThreadFactory namedThreadFactory(String prefix) {
        var counter = new AtomicInteger();
        return runnable -> {
            var thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private boolean loadInitialContent() {
        boolean loaded = false;
        try {
            if (Files.exists(filePath)) {
                String content = Files.readString(filePath);
                replaceText(content);
                lastSavedText.set(content);
                loaded = true;
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
        return loaded;
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
            if (documentStore != null) {
                documentStore.latestVersion(documentId)
                    .ifPresent(version -> editVersion.accumulateAndGet(version.value(), Math::max));
            }
            editVersion.incrementAndGet();
            TextDocumentSnapshot snapshot = captureSnapshot();
            pendingSnapshot.set(snapshot);
            if (!applyingExternalReload) {
                if (documentStore != null) {
                    documentStore.publishOverlay(snapshot);
                }
                setSaveState(EditorSaveState.DIRTY);
            }
        });
        changeSubscription = multiPlainChanges()
            .successionEnds(CHANGE_DEBOUNCE)
            .subscribe(changes -> {
                TextDocumentSnapshot snapshot = pendingSnapshot.get();

                List<DocumentModifiedEvent.Change> diff = changes.stream()
                    .map(change -> buildChange(snapshot.text(), change))
                    .toList();
                publishFileModifiedEvent(diff);

                if (snapshot.version().value() != savedVersion.get()) {
                    scheduleSave(snapshot);
                }
            });
    }

    private void scheduleSave(TextDocumentSnapshot snapshot) {
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

                    persistSnapshot(snapshot);
                }
            }, SAVE_DELAY.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private boolean persistSnapshot(TextDocumentSnapshot savedSnapshot) {
        if (backingReadFailed) {
            updateAfterSave(editVersion.get(), EditorSaveState.ERROR);
            return false;
        }
        if (savedSnapshot == null)
            return true;

        String snapshot = savedSnapshot.text();
        long snapshotVersion = savedSnapshot.version().value();
        // A cancelled task can already be waiting for saveLock behind a newer manual save.
        if (snapshotVersion < savedVersion.get())
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
            acknowledgeSavedSnapshot(savedSnapshot);
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
            acknowledgeSavedSnapshot(savedSnapshot);
            updateAfterSave(snapshotVersion, EditorSaveState.CLEAN);
            Railroad.EVENT_BUS.publish(new DocumentEvent(document(), DocumentEvent.EventType.SAVED));
            return true;
        } catch (IOException | RuntimeException exception) {
            updateAfterSave(snapshotVersion, EditorSaveState.ERROR);
            Railroad.LOGGER.error("Failed to write file {}", filePath, exception);
            return false;
        }
    }

    private void acknowledgeSavedSnapshot(TextDocumentSnapshot savedSnapshot) {
        DocumentStore store = documentStore;
        if (store == null)
            return;

        for (int attempt = 0; attempt < 3; attempt++) {
            DocumentState state = store.state(savedSnapshot.id()).orElse(null);
            if (state == null || state.backing() == null || state.isStale()
                || !(state.overlay() instanceof TextDocumentSnapshot overlay)
                || !savedSnapshot.version().isAfter(state.backing().version()))
                return;

            try {
                if (overlay.version().equals(savedSnapshot.version())) {
                    if (!overlay.text().equals(savedSnapshot.text()))
                        return;

                    store.commitOverlay(savedSnapshot.id(), state.backing().version(), overlay.version());
                } else if (overlay.version().isAfter(savedSnapshot.version())) {
                    store.acknowledgeSavedRevision(savedSnapshot.id(), state.backing().version(), overlay.version(),
                        savedSnapshot);
                }
                return;
            } catch (IllegalStateException exception) {
                Railroad.LOGGER.debug("Retrying save acknowledgement for {} because the document state changed",
                    savedSnapshot.id(), exception);
            }
        }
    }

    /**
     * Immediately writes the current editor contents, bypassing the autosave delay.
     *
     * @return true when persistence succeeds, or when an already closed editor is clean
     */
    public boolean saveNow() {
        synchronized (saveLock) {
            if (closed)
                return saveState() == EditorSaveState.CLEAN;

            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }

            TextDocumentSnapshot snapshot = captureSnapshot();
            pendingSnapshot.set(snapshot);
            return persistSnapshot(snapshot);
        }
    }

    /**
     * Writes the current contents to a new path and makes it the backing file.
     * Attached editors reject a different path; their owner must finalize identity and store state.
     *
     * @param newPath new backing-file path
     * @return true when the save succeeds and the backing path is updated
     */
    public boolean saveAs(Path newPath) {
        return saveAs(newPath, () -> {
        });
    }

    /**
     * Writes the current contents to a new path, then lets the owner rebind its identity
     * before changing the editor's location or announcing the save. A failing callback
     * leaves the editor's previous location and saved baseline intact. The written
     * destination is retained; filesystem writes are not rolled back.
     * Call on the JavaFX thread. When the path is unchanged, this performs an ordinary
     * save without calling the rebinding callback.
     * For attached editors, changing paths requires the snapshot finalizer overload instead.
     *
     * @param newPath destination file
     * @param rebindIdentity owner callback run after the write and before the editor switches paths
     * @return true when writing, rebinding, and updating the editor succeed
     * @throws NullPointerException if either argument is {@code null}
     */
    public boolean saveAs(Path newPath, Runnable rebindIdentity) {
        Objects.requireNonNull(rebindIdentity, "rebindIdentity");
        Objects.requireNonNull(newPath, "newPath");
        if (documentStore != null
            && !filePath.toAbsolutePath().normalize().equals(newPath.toAbsolutePath().normalize())) {
            Railroad.LOGGER.warn("Use the document owner's Save As transition for attached editor {}", filePath);
            return false;
        }
        return saveAs(newPath, saved -> rebindIdentity.run());
    }

    /**
     * Writes a destination and lets its owner finalize identity and store state using
     * the exact persisted snapshot before this editor adopts its new saved location.
     * A failing finalizer retains the original location and saved baseline.
     * Once finalization succeeds, failures in watcher, UI, or event updates are logged
     * separately and do not change the successful save result.
     *
     * @param newPath destination file
     * @param finalizeSave owner transition, including any rollback of identity on failure
     * @return whether persistence and finalization succeeded
     */
    public boolean saveAs(Path newPath, Consumer<TextDocumentSnapshot> finalizeSave) {
        Objects.requireNonNull(finalizeSave, "finalizeSave");
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
            long previousEditVersion = editVersion.get();
            TextDocumentSnapshot previousPending = pendingSnapshot.get();
            DocumentStore store = documentStore;
            DocumentState state = store == null ? null : store.state(documentId).orElse(null);
            if (state == null && store != null) {
                store.latestVersion(documentId).ifPresent(version -> {
                    if (version.value() >= editVersion.get()) {
                        editVersion.set(version.next().value());
                    }
                });
            }
            if (state != null && (state.isStale() || state.backing() == null
                || state.overlay() == null && state.backing() instanceof TextDocumentSnapshot backing
                    && (!backing.text().equals(snapshot) || editVersion.get() != backing.version().value()))) {
                long latestVersion = latestDocumentVersion();
                if (state.backing() != null) {
                    latestVersion = Math.max(latestVersion, state.backing().version().value());
                }
                if (state.overlay() != null) {
                    latestVersion = Math.max(latestVersion, state.overlay().version().value());
                }
                editVersion.set(new DocumentVersion(latestVersion).next().value());
                pendingSnapshot.set(captureSnapshot());
            }
            long snapshotVersion = editVersion.get();
            DocumentUri uri = DocumentUri.fromPath(normalizedPath);
            var savedSnapshot = new TextDocumentSnapshot(documentId, uri,
                new DocumentVersion(snapshotVersion), languageId, DocumentOrigin.original(uri), snapshot,
                StandardCharsets.UTF_8);
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

                finalizeSave.accept(savedSnapshot);
            } catch (IOException | RuntimeException exception) {
                if (editVersion.get() == snapshotVersion) {
                    editVersion.set(previousEditVersion);
                    pendingSnapshot.set(previousPending);
                }
                setSaveState(EditorSaveState.ERROR);
                Railroad.LOGGER.error("Failed to write file {}", normalizedPath, exception);
                return false;
            }

            filePath = normalizedPath;
            backingReadFailed = false;
            pendingExternalText = null;
            backingFileMissing = false;
            discardChangesOnClose = false;
            lastSavedText.set(snapshot);
            pendingSnapshot.set(savedSnapshot);
            savedVersion.set(snapshotVersion);
            afterSaveAs("update save state", () -> updateAfterSave(snapshotVersion, EditorSaveState.CLEAN));
            afterSaveAs("refresh the gutter", () -> JavaFXUtils.runOnApplicationThread(gutter::refresh));
            afterSaveAs("close the external-change dialog", this::closeExternalChangeDialog);
            afterSaveAs("restart the file watcher", this::restartExternalWatcher);
            afterSaveAs("publish the save event",
                () -> Railroad.EVENT_BUS.publish(new DocumentEvent(document(), DocumentEvent.EventType.SAVED)));
            return true;
        }
    }

    private void afterSaveAs(String action, Runnable update) {
        try {
            update.run();
        } catch (RuntimeException exception) {
            Railroad.LOGGER.warn("Saved {}, but could not {}", filePath, action, exception);
        }
    }

    /**
     * Prevents disposal from retrying a save after the user explicitly chose Discard.
     */
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

    /**
     * Returns the current save state.
     *
     * @return current editor save state
     */
    public EditorSaveState saveState() {
        return saveState.get();
    }

    /**
     * Exposes changes to the editor save state.
     *
     * @return read-only observable save state
     */
    public ReadOnlyObjectProperty<EditorSaveState> saveStateProperty() {
        return saveState.getReadOnlyProperty();
    }

    /**
     * Reports whether the editor is in any state other than clean.
     *
     * @return true when the save state is not CLEAN
     */
    public boolean dirty() {
        return dirty.get();
    }

    /**
     * Exposes changes to whether the editor is not clean.
     *
     * @return read-only observable dirty flag
     */
    public ReadOnlyBooleanProperty dirtyProperty() {
        return dirty.getReadOnlyProperty();
    }

    /**
     * Reports whether a save is in progress.
     *
     * @return true when the save state is SAVING
     */
    public boolean saving() {
        return saving.get();
    }

    /**
     * Exposes changes to whether a save is in progress.
     *
     * @return read-only observable saving flag
     */
    public ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    /**
     * Reports whether the editor is clean.
     *
     * @return true when the save state is CLEAN
     */
    public boolean saved() {
        return saved.get();
    }

    /**
     * Exposes changes to whether the editor is clean.
     *
     * @return read-only observable clean-state flag
     */
    public ReadOnlyBooleanProperty savedProperty() {
        return saved.getReadOnlyProperty();
    }

    /**
     * Reports whether the latest save failed.
     *
     * @return true when the save state is ERROR
     */
    public boolean saveFailed() {
        return saveFailed.get();
    }

    /**
     * Exposes changes to whether the latest save failed.
     *
     * @return read-only observable save-failure flag
     */
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
     *
     * @param newPath new backing-file path
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
            if (documentStore != null) {
                documentStore.relocate(documentId, DocumentUri.fromPath(normalizedPath));
            }
            JavaFXUtils.runOnApplicationThread(gutter::refresh);
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
            TextDocumentSnapshot snapshot = captureSnapshot();
            pendingSnapshot.set(snapshot);
            restartExternalWatcher();
            if (contentDirty) {
                scheduleSave(snapshot);
            }
        }
    }

    /**
     * Marks a deleted backing file without discarding the editor's in-memory text.
     */
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
            DocumentStore store = documentStore;
            DocumentState state = store != null ? store.state(documentId).orElse(null) : null;
            if (state != null && state.overlay() instanceof TextDocumentSnapshot overlay
                && overlay.text().equals(getText())) {
                editVersion.accumulateAndGet(overlay.version().value(), Math::max);
            }
            if (state != null && state.backing() != null && state.overlay() == null) {
                editVersion.accumulateAndGet(state.backing().version().next().value(), Math::max);
            }
            setSaveState(EditorSaveState.ERROR);
            TextDocumentSnapshot snapshot = captureSnapshot();
            pendingSnapshot.set(snapshot);
            if (state != null && state.backing() != null) {
                if (state.overlay() == null) {
                    store.publishOverlay(snapshot);
                }
                store.removeBacking(documentId, state.backing().version());
            }
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }
        }
    }

    /**
     * Reports whether file watching or saving has detected a missing backing file.
     *
     * @return true when the backing file is marked missing
     */
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
                if (disk.equals(getText()) && documentStore != null
                    && documentStore.state(documentId).map(state -> state.overlay() != null).orElse(false)) {
                    publishExternalBacking(disk);
                }
                reloadExternalText(disk);
            } else {
                publishExternalBacking(disk);
                setSaveState(EditorSaveState.DIRTY);
                displayedExternalText = disk;
                showExternalChangeDialog(disk);
            }
        }
    }

    private long latestDocumentVersion() {
        DocumentStore store = documentStore;
        return Math.max(editVersion.get(), store == null
            ? 0
            : store.latestVersion(documentId).map(DocumentVersion::value).orElse(0L));
    }

    private void publishExternalBacking(String disk) {
        DocumentStore store = documentStore;
        if (store == null)
            return;

        DocumentState state = store.state(documentId).orElse(null);
        if (state != null && state.backing() instanceof TextDocumentSnapshot backing && disk.equals(backing.text()))
            return;

        long latestVersion = latestDocumentVersion();
        if (state != null) {
            if (state.backing() != null) {
                latestVersion = Math.max(latestVersion, state.backing().version().value());
            }
            if (state.overlay() != null) {
                latestVersion = Math.max(latestVersion, state.overlay().version().value());
            }
        }
        DocumentVersion externalVersion = new DocumentVersion(latestVersion).next();
        DocumentUri uri = DocumentUri.fromPath(filePath);
        var externalSnapshot = new TextDocumentSnapshot(documentId, uri, externalVersion, languageId,
            DocumentOrigin.original(uri), disk, StandardCharsets.UTF_8);
        store.publishBacking(externalSnapshot);
        editVersion.accumulateAndGet(externalVersion.value(), Math::max);
    }

    protected void showExternalChangeDialog(String disk) {
        if (externalChangeDialog == null) {
            externalChangeDialog = new ExternalChangeDialog(this, () -> resolveExternalChange(true),
                () -> resolveExternalChange(false));
        }
        externalChangeDialog.update(getText(), disk);
    }

    /**
     * Checks whether a disk change awaits resolution against the local editor contents.
     *
     * @return true when external text is pending
     */
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
                backingReadFailed = false;
                if (!disk.equals(displayedExternalText)) {
                    pendingExternalText = disk;
                    publishExternalBacking(disk);
                    displayedExternalText = disk;
                    showExternalChangeDialog(disk);
                    return;
                }
                if (reload) {
                    DocumentStore store = documentStore;
                    DocumentState state = store != null ? store.state(documentId).orElse(null) : null;
                    TextDocumentSnapshot restoredBacking = null;
                    if (state != null && state.backing() instanceof TextDocumentSnapshot backing
                        && state.overlay() != null) {
                        long latestVersion = Math.max(latestDocumentVersion(),
                            Math.max(backing.version().value(), state.overlay().version().value()));
                        DocumentVersion restoredVersion = new DocumentVersion(latestVersion).next();
                        restoredBacking = new TextDocumentSnapshot(backing.id(), backing.uri(), restoredVersion,
                            backing.languageId(), backing.origin(), disk, backing.encoding());
                        store.discardOverlay(documentId, backing.version(), state.overlay().version(), restoredBacking);
                    }
                    reloadExternalText(disk, restoredBacking);
                } else {
                    DocumentStore store = documentStore;
                    DocumentState state = store != null ? store.state(documentId).orElse(null) : null;
                    if (state != null && state.backing() != null && state.overlay() != null) {
                        long latestVersion = Math.max(latestDocumentVersion(),
                            Math.max(state.backing().version().value(), state.overlay().version().value()));
                        DocumentVersion reconciledVersion = new DocumentVersion(latestVersion).next();
                        TextDocumentSnapshot localSnapshot = captureSnapshot();
                        var reconciledOverlay = new TextDocumentSnapshot(localSnapshot.id(), localSnapshot.uri(),
                            reconciledVersion, localSnapshot.languageId(), localSnapshot.origin(), localSnapshot.text(),
                            localSnapshot.encoding());
                        store.reconcileOverlay(documentId, state.backing().version(), state.overlay().version(),
                            reconciledOverlay);
                        editVersion.set(reconciledVersion.value());
                        pendingSnapshot.set(reconciledOverlay);
                    }
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
        reloadExternalText(disk, null);
    }

    private void reloadExternalText(String disk, TextDocumentSnapshot restoredBacking) {
        backingReadFailed = false;
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel(false);
            pendingSaveTask = null;
        }
        DocumentStore store = documentStore;
        DocumentState state = store != null ? store.state(documentId).orElse(null) : null;
        if (restoredBacking == null && state != null && state.overlay() != null
            && state.backing() instanceof TextDocumentSnapshot backing && backing.text().equals(disk)
            && getText().equals(disk)) {
            long latestVersion = Math.max(latestDocumentVersion(),
                Math.max(backing.version().value(), state.overlay().version().value()));
            restoredBacking = new TextDocumentSnapshot(backing.id(), backing.uri(),
                new DocumentVersion(latestVersion).next(),
                backing.languageId(), backing.origin(), disk, backing.encoding());
            store.discardOverlay(documentId, backing.version(), state.overlay().version(), restoredBacking);
        }
        boolean cleanReload = restoredBacking == null && !hasUnsavedChanges()
            && (state == null || state.overlay() == null);
        if (cleanReload && state != null && state.backing() instanceof TextDocumentSnapshot backing
            && backing.text().equals(disk) && backing.uri().equals(DocumentUri.fromPath(filePath))) {
            restoredBacking = backing;
            cleanReload = false;
        }
        int caret = getCaretPosition();
        if (!getText().equals(disk)) {
            applyingExternalReload = true;
            try {
                replaceText(disk);
            } finally {
                applyingExternalReload = false;
            }
            if (cleanReload && state != null && state.backing() != null) {
                editVersion.accumulateAndGet(state.backing().version().next().value(), Math::max);
            }
            moveTo(Math.min(caret, getLength()));
        }
        if (restoredBacking != null) {
            editVersion.set(restoredBacking.version().value());
        }
        TextDocumentSnapshot snapshot = restoredBacking != null ? restoredBacking : captureSnapshot();
        if (cleanReload && store != null) {
            store.publishBacking(snapshot);
        }
        lastSavedText.set(disk);
        pendingSnapshot.set(snapshot);
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
