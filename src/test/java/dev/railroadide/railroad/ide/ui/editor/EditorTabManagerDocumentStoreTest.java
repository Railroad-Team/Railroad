package dev.railroadide.railroad.ide.ui.editor;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentStore;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.Event;
import javafx.scene.control.Tab;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactfx.Subscription;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class EditorTabManagerDocumentStoreTest {
    @TempDir
    public Path tempDirectory;
    private Project project;
    private EditorTab tab;

    @BeforeAll
    public static void startJavaFx() throws Exception {
        WorkspaceModes.initialize();
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    public void openProject() throws Exception {
        onFxThread(() -> {
            LanguageSupports.initialize();
            project = new RailroadProject(tempDirectory, "Backing publication test", new WritableImage(1, 1));
            Services.IDE_STATE.setCurrentProject(project);
            return null;
        });
    }

    @AfterEach
    public void closeProject() throws Exception {
        onFxThread(() -> {
            try {
                if (tab != null) {
                    var editor = tab.view().activeEditor();
                    editor.discardChangesOnClose();
                    // Cleanup discards even missing files without prompting to retry a failed save.
                    tab.tab().getTabPane().getTabs().remove(tab.tab());
                    Event.fireEvent(tab.tab(), new Event(tab.tab(), tab.tab(), Tab.CLOSED_EVENT));
                    editor.close();
                    editor.dispose();
                }
            } finally {
                Railroad.EVENT_BUS.publish(new ProjectEvent(project, ProjectEvent.EventType.CLOSED));
            }
            return null;
        });
    }

    @Test
    public void openingJavaFilePublishesInitialBackingSnapshot() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        Files.writeString(file, "class Example {}");

        onFxThread(() -> {
            tab = openInTabPane(file);
            assertNotNull(tab);
            var editor = tab.view().activeEditor();
            assertTrue(editor.isInitialContentLoaded());
            var initial = editor.captureSnapshot();
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var backing = assertInstanceOf(TextDocumentSnapshot.class, store.current(tab.documentId()).orElseThrow());

            assertEquals("class Example {}", backing.text());
            assertEquals(tab.documentId(), backing.id());
            assertEquals(initial.id(), backing.id());
            assertEquals(initial.version(), backing.version());
            assertSame(backing, store.state(tab.documentId()).orElseThrow().backing());
            assertNull(store.state(tab.documentId()).orElseThrow().overlay());
            assertSame(backing, store.capture().document(tab.documentId()).orElseThrow());
            assertEquals(1, store.capture().revision());
            return null;
        });
    }

    @Test
    public void editingJavaFilePublishesOverlayWhilePreservingBacking() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        String editedText = "class Example { int x; }";
        Files.writeString(file, originalText);

        onFxThread(() -> {
            tab = openInTabPane(file);
            var editor = tab.view().activeEditor();
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var id = tab.documentId();
            var originalWorkspace = store.capture();
            var backing = assertInstanceOf(TextDocumentSnapshot.class, store.state(id).orElseThrow().backing());

            editor.replaceText(editedText);

            var state = store.state(id).orElseThrow();
            var overlay = assertInstanceOf(TextDocumentSnapshot.class, state.overlay());
            assertSame(backing, state.backing());
            assertEquals(originalText, backing.text());
            assertEquals(editedText, overlay.text());
            assertEquals(id, backing.id());
            assertEquals(id, overlay.id());
            assertTrue(overlay.version().isAfter(backing.version()));
            assertEquals(editor.captureSnapshot().version(), overlay.version());
            assertEquals(backing.version(), state.baseBackingVersion());
            assertFalse(state.isStale());
            assertSame(overlay, store.current(id).orElseThrow());
            assertSame(overlay, store.capture().document(id).orElseThrow());
            assertSame(backing, originalWorkspace.document(id).orElseThrow());
            assertEquals(originalWorkspace.revision() + 1, store.capture().revision());
            return null;
        });
    }

    @Test
    public void deletingCleanFileCreatesOverlayBeforeRemovingBacking() throws Exception {
        assertBackingDeletion(false, false);
    }

    @Test
    public void deletingPreviouslySavedFileCreatesOverlayWithNewerVersion() throws Exception {
        assertBackingDeletion(false, true);
    }

    @Test
    public void deletingDirtyFilePreservesExistingOverlay() throws Exception {
        assertBackingDeletion(true, false);
    }

    @Test
    public void savingJavaFilePromotesExactOverlayWithoutChangingVersion() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String editedText = "class Example { int x; }";
        Files.writeString(file, "class Example {}");

        onFxThread(() -> {
            tab = openInTabPane(file);
            var editor = tab.view().activeEditor();
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var id = tab.documentId();
            editor.replaceText(editedText);
            var overlay = assertInstanceOf(TextDocumentSnapshot.class, store.state(id).orElseThrow().overlay());
            var beforeSave = store.capture();

            assertTrue(editor.saveNow());

            assertEquals(editedText, Files.readString(file));
            var savedState = store.state(id).orElseThrow();
            assertSame(overlay, savedState.backing());
            assertNull(savedState.overlay());
            assertSame(overlay, store.current(id).orElseThrow());
            assertSame(overlay, store.capture().document(id).orElseThrow());
            assertEquals(overlay.version(), editor.captureSnapshot().version());
            assertEquals(beforeSave.revision() + 1, store.capture().revision());
            return null;
        });
    }

    @Test
    public void savingEditsReturnedToBackingContentCommitsTheirNewerRevision() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        Files.writeString(file, originalText);

        onFxThread(() -> {
            tab = openInTabPane(file);
            var editor = tab.view().activeEditor();
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var id = tab.documentId();
            var originalBacking = store.state(id).orElseThrow().backing();
            editor.replaceText("class Example { int x; }");
            editor.replaceText(originalText);
            var overlay = assertInstanceOf(TextDocumentSnapshot.class, store.state(id).orElseThrow().overlay());
            assertTrue(overlay.version().isAfter(originalBacking.version()));

            assertTrue(editor.saveNow());

            assertEquals(originalText, Files.readString(file));
            assertSame(overlay, store.state(id).orElseThrow().backing());
            assertNull(store.state(id).orElseThrow().overlay());
            assertEquals(overlay.version(), editor.captureSnapshot().version());
            return null;
        });
    }

    @Test
    public void autosaveAcknowledgesCapturedRevisionAfterNewerEdits() throws Exception {
        assertOverlappingAutosave(false);
    }

    @Test
    public void autosaveWithoutDiskWriteAcknowledgesRevisionAfterNewerEdits() throws Exception {
        assertOverlappingAutosave(true);
    }

    @Test
    public void saveRetriesChangedOverlayUsingTheOriginallySavedSnapshot() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String savedText = "class Example { int x; }";
        String newerText = "class Example { int x; int y; }";
        Files.writeString(file, "class Example {}");

        onFxThread(() -> {
            tab = openInTabPane(file);
            var editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            editor.replaceText(savedText);
            var savedVersion = editor.captureSnapshot().version();
            var changeDuringCommit = new AtomicBoolean(true);
            var stateReads = new AtomicInteger();
            editor.setDocumentStore((DocumentStore) Proxy.newProxyInstance(DocumentStore.class.getClassLoader(),
                new Class<?>[]{DocumentStore.class}, (proxy, method, args) -> {
                    if (method.getName().equals("state")) {
                        stateReads.incrementAndGet();
                    }
                    if (method.getName().equals("commitOverlay") && changeDuringCommit.compareAndSet(true, false)) {
                        editor.replaceText(newerText);
                    }
                    try {
                        return method.invoke(store, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }));

            assertTrue(editor.saveNow());

            var state = store.state(tab.documentId()).orElseThrow();
            var backing = assertInstanceOf(TextDocumentSnapshot.class, state.backing());
            var overlay = assertInstanceOf(TextDocumentSnapshot.class, state.overlay());
            assertEquals(savedText, Files.readString(file));
            assertEquals(savedText, backing.text());
            assertEquals(savedVersion, backing.version());
            assertEquals(newerText, overlay.text());
            assertEquals(savedVersion, state.baseBackingVersion());
            assertFalse(state.isStale());
            assertSame(overlay, store.current(tab.documentId()).orElseThrow());
            assertTrue(stateReads.get() >= 2);
            return null;
        });
    }

    @Test
    public void olderCapturedSaveCannotOverwriteANewerSuccessfulSave() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String newerText = "class Example { int x; int y; }";
        Files.writeString(file, "class Example {}");

        onFxThread(() -> {
            tab = openInTabPane(file);
            var editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            editor.replaceText("class Example { int x; }");
            var olderSnapshot = editor.captureSnapshot();
            editor.replaceText(newerText);
            assertTrue(editor.saveNow());
            var savedState = store.state(tab.documentId()).orElseThrow();
            var savedWorkspace = store.capture();

            // Simulate an already-started older task acquiring saveLock after the manual save.
            var persist = TextEditorPane.class.getDeclaredMethod("persistSnapshot", TextDocumentSnapshot.class);
            persist.setAccessible(true);
            assertEquals(true, persist.invoke(editor, olderSnapshot));

            assertEquals(newerText, Files.readString(file));
            assertEquals(savedState, store.state(tab.documentId()).orElseThrow());
            assertSame(savedState.backing(), store.current(tab.documentId()).orElseThrow());
            assertNull(store.state(tab.documentId()).orElseThrow().overlay());
            assertEquals(savedWorkspace, store.capture());
            return null;
        });
    }

    @Test
    public void cleanSaveAsUpdatesBackingLocationWithoutChangingContentVersion() throws Exception {
        Path source = tempDirectory.resolve("Example.java");
        Path destination = tempDirectory.resolve("Renamed.java");
        String text = "class Example {}";
        Files.writeString(source, text);

        onFxThread(() -> {
            tab = openInTabPane(source);
            var editor = tab.view().activeEditor();
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var id = tab.documentId();
            var originalState = store.state(id).orElseThrow();
            var originalBacking = assertInstanceOf(TextDocumentSnapshot.class, originalState.backing());
            var originalWorkspace = store.capture();
            assertNull(originalState.overlay());
            assertFalse(editor.dirty());

            assertTrue(Services.EDITOR_TAB_MANAGER.saveAsActive(destination));

            var state = store.state(id).orElseThrow();
            var backing = assertInstanceOf(TextDocumentSnapshot.class, state.backing());
            assertEquals(id, tab.documentId());
            assertEquals(id, editor.captureSnapshot().id());
            assertEquals(id, backing.id());
            assertEquals(DocumentUri.fromPath(destination), backing.uri());
            assertEquals(text, backing.text());
            assertEquals(text, Files.readString(destination));
            assertEquals(text, Files.readString(source));
            assertEquals(originalBacking.version(), backing.version());
            assertEquals(originalBacking.version(), editor.captureSnapshot().version());
            assertNull(state.overlay());
            assertSame(backing, store.current(id).orElseThrow());
            var workspace = store.capture();
            assertEquals(originalWorkspace.revision() + 1, workspace.revision());
            assertSame(backing, workspace.document(id).orElseThrow());
            assertEquals(DocumentUri.fromPath(destination), workspace.document(id).orElseThrow().uri());
            assertSame(originalBacking, originalWorkspace.document(id).orElseThrow());
            assertEquals(DocumentUri.fromPath(source), originalBacking.uri());
            return null;
        });
    }

    @Test
    public void failedCleanSaveAsLeavesBackingAndWorkspaceUnchanged() throws Exception {
        Path source = tempDirectory.resolve("Example.java");
        Path destination = Files.createDirectory(tempDirectory.resolve("directory.java"));
        Files.writeString(source, "class Example {}");

        onFxThread(() -> {
            tab = openInTabPane(source);
            var editor = tab.view().activeEditor();
            var identity = tab.identity();
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var originalState = store.state(tab.documentId()).orElseThrow();
            var originalWorkspace = store.capture();
            assertNull(originalState.overlay());

            assertFalse(Services.EDITOR_TAB_MANAGER.saveAsActive(destination));

            var state = store.state(tab.documentId()).orElseThrow();
            assertEquals(originalState, state);
            assertSame(originalState.backing(), state.backing());
            assertNull(state.overlay());
            assertEquals(originalWorkspace.revision(), store.capture().revision());
            assertEquals(originalWorkspace, store.capture());
            assertSame(identity, tab.identity());
            assertEquals(source, editor.getFilePath());
            assertEquals("class Example {}", Files.readString(source));
            assertTrue(Files.isDirectory(destination));
            return null;
        });
    }

    @Test
    public void dirtySaveAsPreservesIdentityAndCommitsSnapshotAtNewLocation() throws Exception {
        Path source = tempDirectory.resolve("Old.java");
        Path destination = tempDirectory.resolve("New.java");
        String originalText = "class Example {}";
        String editedText = "class Example { int x; }";
        Files.writeString(source, originalText);

        onFxThread(() -> {
            tab = openInTabPane(source);
            var editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText(editedText);
            var id = tab.documentId();
            var before = editor.captureSnapshot();
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var workspace = store.capture();
            var originalOverlay = store.state(id).orElseThrow().overlay();
            assertTrue(Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(destination)).isEmpty());

            assertTrue(Services.EDITOR_TAB_MANAGER.saveAsActive(destination));

            assertEquals(editedText, Files.readString(destination));
            assertEquals(originalText, Files.readString(source));
            assertSame(id, tab.documentId());
            assertSame(id, editor.captureSnapshot().id());
            assertEquals(id,
                Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(destination)).orElseThrow().id());
            assertEquals(destination, tab.path());
            assertEquals(destination, editor.getFilePath());
            assertEquals(DocumentUri.fromPath(destination.toRealPath()), tab.identity().uri());
            assertEquals(DocumentUri.fromPath(destination), tab.document().getUri());
            assertEquals(DocumentUri.fromPath(destination), editor.captureSnapshot().uri());
            assertEquals(before.version(), editor.captureSnapshot().version());
            assertSame(tab, Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow());
            var state = store.state(id).orElseThrow();
            var backing = assertInstanceOf(TextDocumentSnapshot.class, state.backing());
            assertEquals(editedText, backing.text());
            assertEquals(before.version(), backing.version());
            assertEquals(id, backing.id());
            assertEquals(DocumentUri.fromPath(destination), backing.uri());
            assertNull(state.overlay());
            assertFalse(state.isStale());
            assertSame(backing, store.current(id).orElseThrow());
            assertSame(backing, store.capture().document(id).orElseThrow());
            assertEquals(workspace.revision() + 1, store.capture().revision());
            assertSame(originalOverlay, workspace.document(id).orElseThrow());
            return null;
        });
    }

    @Test
    public void failedIdentityRebindRetainsWrittenDestinationAndLeavesStoreUnchanged() throws Exception {
        Path source = tempDirectory.resolve("Old.java");
        Path destination = tempDirectory.resolve("New.java");
        String originalText = "class Old {}";
        String editedText = "class Old { int x; }";
        Files.writeString(source, originalText);
        Files.writeString(destination, "previous destination content");

        onFxThread(() -> {
            tab = openInTabPane(source);
            var editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText(editedText);
            var identity = tab.identity();
            var before = editor.captureSnapshot();
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var originalState = store.state(tab.documentId()).orElseThrow();
            var originalWorkspace = store.capture();
            var competingIdentity = new AtomicReference<DocumentIdentity>();
            ChangeListener<EditorSaveState> registerDestination = (observable, oldState, newState) -> {
                if (newState == EditorSaveState.SAVING && competingIdentity.get() == null) {
                    competingIdentity
                        .set(Services.IDE_STATE.identifyDocument(new FileSystemDocument(destination, "java")));
                }
            };
            editor.saveStateProperty().addListener(registerDestination);
            try {
                assertFalse(Services.EDITOR_TAB_MANAGER.saveAsActive(destination));
            } finally {
                editor.saveStateProperty().removeListener(registerDestination);
            }

            assertNotNull(competingIdentity.get());
            assertEquals(editedText, Files.readString(destination));
            assertEquals(originalText, Files.readString(source));
            assertSame(identity, tab.identity());
            assertEquals(source, tab.path());
            assertEquals(source, editor.getFilePath());
            assertEquals(identity.id(), editor.captureSnapshot().id());
            assertEquals(before.version(), editor.captureSnapshot().version());
            assertEquals(editedText, editor.getText());
            assertTrue(editor.dirty());
            assertEquals(identity, Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(source)).orElseThrow());
            assertEquals(competingIdentity.get(),
                Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(destination)).orElseThrow());
            assertEquals(originalState, store.state(tab.documentId()).orElseThrow());
            assertSame(originalState.backing(), store.state(tab.documentId()).orElseThrow().backing());
            assertSame(originalState.overlay(), store.state(tab.documentId()).orElseThrow().overlay());
            assertEquals(originalWorkspace, store.capture());

            // The previous saved baseline must still allow an ordinary save to the original file.
            assertTrue(editor.saveNow());
            assertEquals(editedText, Files.readString(source));
            assertEquals(DocumentUri.fromPath(source), store.state(tab.documentId()).orElseThrow().backing().uri());
            return null;
        });
    }

    @Test
    public void saveAsRejectsAnotherRegisteredIdentityEvenWithoutAnOpenTab() throws Exception {
        Path source = tempDirectory.resolve("Example.java");
        Path destination = tempDirectory.resolve("Other.java");
        String originalText = "class Example {}";
        String destinationText = "class Other {}";
        String editedText = "class Example { int x; }";
        Files.writeString(source, originalText);
        Files.writeString(destination, destinationText);

        onFxThread(() -> {
            tab = openInTabPane(source);
            var editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText(editedText);
            var identity = tab.identity();
            // Register the other document without opening an editor for it.
            var destinationIdentity = Services.IDE_STATE.identifyDocument(new FileSystemDocument(destination, "java"));
            assertNotEquals(identity.id(), destinationIdentity.id());
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var workspace = store.capture();

            assertFalse(Services.EDITOR_TAB_MANAGER.saveAsActive(destination));

            assertEquals(destinationText, Files.readString(destination));
            assertEquals(originalText, Files.readString(source));
            assertSame(identity, tab.identity());
            assertEquals(identity.id(), editor.captureSnapshot().id());
            assertEquals(identity, Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(source)).orElseThrow());
            assertEquals(destinationIdentity,
                Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(destination)).orElseThrow());
            assertEquals(source, tab.path());
            assertEquals(source, editor.getFilePath());
            assertEquals(DocumentUri.fromPath(source), editor.captureSnapshot().uri());
            assertEquals(editedText, editor.getText());
            assertTrue(editor.dirty());
            assertSame(tab, Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow());
            assertEquals(workspace, store.capture());
            return null;
        });
    }

    @Test
    public void openingEmptyFilePublishesSuccessfullyLoadedBacking() throws Exception {
        Path file = tempDirectory.resolve("Empty.java");
        Files.writeString(file, "");

        onFxThread(() -> {
            tab = openInTabPane(file);
            var editor = tab.view().activeEditor();
            assertTrue(editor.isInitialContentLoaded());
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var backing = assertInstanceOf(TextDocumentSnapshot.class, store.current(tab.documentId()).orElseThrow());
            assertEquals("", backing.text());
            assertSame(backing, store.capture().document(tab.documentId()).orElseThrow());
            return null;
        });
    }

    @Test
    public void failedInitialReadDoesNotPublishFallbackText() throws Exception {
        Path file = tempDirectory.resolve("Invalid.java");
        byte[] invalidUtf8 = {(byte) 0xc3, 0x28};
        Files.write(file, invalidUtf8);

        onFxThread(() -> {
            tab = openInTabPane(file);
            assertNotNull(tab);
            var editor = tab.view().activeEditor();
            assertFalse(editor.isInitialContentLoaded());
            assertEquals("", editor.getText());
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            assertTrue(store.current(tab.documentId()).isEmpty());
            assertTrue(store.capture().documents().isEmpty());
            assertEquals(0, store.capture().revision());
            return null;
        });
        assertArrayEquals(invalidUtf8, Files.readAllBytes(file));
    }

    private void assertBackingDeletion(boolean dirty, boolean saveFirst) throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        String editedText = "class Example { int x; }";
        Files.writeString(file, originalText);

        onFxThread(() -> {
            tab = openInTabPane(file);
            var editor = tab.view().activeEditor();
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var id = tab.documentId();
            if (dirty || saveFirst) {
                editor.replaceText(editedText);
            }
            if (saveFirst) {
                assertTrue(editor.saveNow());
            }
            assertEquals(dirty, editor.dirty());
            var before = store.state(id).orElseThrow();
            var beforeWorkspace = store.capture();
            String expectedText = dirty || saveFirst ? editedText : originalText;

            // Both notifications run before the watcher can deliver its own notification on the FX thread.
            Files.delete(file);
            editor.markBackingFileDeleted();

            var state = store.state(id).orElseThrow();
            var overlay = assertInstanceOf(TextDocumentSnapshot.class, state.overlay());
            assertNull(state.backing());
            assertEquals(expectedText, editor.getText());
            assertEquals(expectedText, overlay.text());
            assertEquals(id, overlay.id());
            assertTrue(overlay.version().isAfter(before.backing().version()));
            assertEquals(before.backing().version(), state.baseBackingVersion());
            assertTrue(state.isStale());
            if (dirty) {
                assertSame(before.overlay(), overlay);
            }
            assertEquals(overlay.version(), editor.captureSnapshot().version());
            assertSame(overlay, store.current(id).orElseThrow());
            var deletedWorkspace = store.capture();
            assertSame(overlay, deletedWorkspace.document(id).orElseThrow());
            assertEquals(beforeWorkspace.revision() + (dirty ? 1 : 2), deletedWorkspace.revision());
            assertTrue(editor.isBackingFileMissing());
            assertEquals(EditorSaveState.ERROR, editor.saveState());
            assertFalse(editor.saveNow());
            assertFalse(Files.exists(file));

            editor.markBackingFileDeleted();

            assertEquals(state, store.state(id).orElseThrow());
            assertSame(overlay, store.current(id).orElseThrow());
            assertEquals(deletedWorkspace, store.capture());
            assertEquals(overlay.version(), editor.captureSnapshot().version());
            assertEquals(expectedText, editor.getText());
            assertFalse(editor.saveNow());
            assertFalse(Files.exists(file));
            return null;
        });
    }

    private EditorTab openInTabPane(Path file) throws ReflectiveOperationException {
        // Exercise the opening path with a real tab pane without constructing unrelated IDE docks.
        Class<?> requestType = Class.forName(EditorTabManager.class.getName() + "$TabOpenRequest");
        var normalRequest = requestType.getDeclaredMethod("normal");
        normalRequest.setAccessible(true);
        var open = EditorTabManager.class.getDeclaredMethod("openInTabPane", Project.class, Path.class,
            DetachableTabPane.class, requestType);
        open.setAccessible(true);
        return (EditorTab) open.invoke(Services.EDITOR_TAB_MANAGER, project, file, new DetachableTabPane(),
            normalRequest.invoke(null));
    }

    private void assertOverlappingAutosave(boolean unchangedText) throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        String savedText = unchangedText ? originalText : "class Example { int x; }";
        String newerText = "class Example { int x; int y; }";
        Files.writeString(file, originalText);
        var saveInProgress = new CountDownLatch(1);
        var continueSave = new CountDownLatch(1);
        var acknowledged = new CompletableFuture<TextDocumentSnapshot>();
        var pause = new AtomicBoolean(true);

        try {
            TextDocumentSnapshot savedSnapshot = onFxThread(() -> {
                tab = openInTabPane(file);
                var editor = tab.view().activeEditor();
                disableDebouncedAutosave(editor);
                var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                if (unchangedText) {
                    editor.replaceText("class Example { int temporary; }");
                }
                editor.replaceText(savedText);
                var snapshot = editor.captureSnapshot();
                editor.setDocumentStore((DocumentStore) Proxy.newProxyInstance(DocumentStore.class.getClassLoader(),
                    new Class<?>[]{DocumentStore.class}, (proxy, method, args) -> {
                        try {
                            if (method.getName().equals("state") && !Platform.isFxApplicationThread()
                                && pause.compareAndSet(true, false)) {
                                saveInProgress.countDown();
                                assertTrue(continueSave.await(10, TimeUnit.SECONDS));
                            }
                            Object result;
                            try {
                                result = method.invoke(store, args);
                            } catch (InvocationTargetException exception) {
                                throw exception.getCause();
                            }
                            if (method.getName().equals("acknowledgeSavedRevision")) {
                                acknowledged.complete((TextDocumentSnapshot) args[3]);
                            }
                            return result;
                        } catch (Throwable failure) {
                            acknowledged.completeExceptionally(failure);
                            throw failure;
                        }
                    }));
                // Run the real scheduled autosave with a known snapshot, controlling its completion with latches.
                var schedule = TextEditorPane.class.getDeclaredMethod("scheduleSave", TextDocumentSnapshot.class);
                schedule.setAccessible(true);
                schedule.invoke(editor, snapshot);
                return snapshot;
            });

            assertTrue(saveInProgress.await(10, TimeUnit.SECONDS));
            TextDocumentSnapshot newerOverlay = onFxThread(() -> {
                tab.view().activeEditor().replaceText(newerText);
                return assertInstanceOf(TextDocumentSnapshot.class, Services.IDE_STATE.getDocumentStore().orElseThrow()
                    .state(tab.documentId()).orElseThrow().overlay());
            });
            continueSave.countDown();
            assertSame(savedSnapshot, acknowledged.get(10, TimeUnit.SECONDS));

            onFxThread(() -> {
                var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                var state = store.state(tab.documentId()).orElseThrow();
                assertEquals(savedText, Files.readString(file));
                assertSame(savedSnapshot, state.backing());
                assertSame(newerOverlay, state.overlay());
                assertEquals(savedSnapshot.version(), state.baseBackingVersion());
                assertTrue(newerOverlay.version().isAfter(savedSnapshot.version()));
                assertFalse(state.isStale());
                assertSame(newerOverlay, store.current(tab.documentId()).orElseThrow());
                assertSame(newerOverlay, store.capture().document(tab.documentId()).orElseThrow());
                assertEquals(newerText, tab.view().activeEditor().getText());
                assertTrue(tab.view().activeEditor().dirty());
                return null;
            });
        } finally {
            continueSave.countDown();
        }
    }

    private static void disableDebouncedAutosave(TextEditorPane editor) throws ReflectiveOperationException {
        // Keep the immediate overlay subscription active; tests explicitly schedule the save under examination.
        var subscription = TextEditorPane.class.getDeclaredField("changeSubscription");
        subscription.setAccessible(true);
        ((Subscription) subscription.get(editor)).unsubscribe();
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
