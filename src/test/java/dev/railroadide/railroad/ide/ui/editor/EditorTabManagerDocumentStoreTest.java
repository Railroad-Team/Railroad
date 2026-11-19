package dev.railroadide.railroad.ide.ui.editor;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.ProjectDocumentSynchronizer;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.RequestPriority;
import dev.railroadide.railroad.ide.sst.request.api.SstScheduler;
import dev.railroadide.railroad.ide.sst.request.api.WorkspaceRequestContext;
import dev.railroadide.railroad.ide.ui.MarkdownPreviewPane;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorPane;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.editor.EditResult;
import dev.railroadide.railroad.plugin.spi.editor.TextEditorAccess;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;
import dev.railroadide.railroad.plugin.spi.event.EventListener;
import dev.railroadide.railroad.plugin.spi.events.DocumentEvent;
import dev.railroadide.railroad.plugin.spi.events.DocumentModifiedEvent;
import dev.railroadide.railroad.plugin.spi.events.DocumentRenamedEvent;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.theme.ThemeManager;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.Event;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactfx.Subscription;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
        Field hostServices = Railroad.class.getDeclaredField("hostServices");
        hostServices.setAccessible(true);
        if (hostServices.get(null) == null) {
            hostServices.set(null, new Railroad().getHostServices());
        }
    }

    @Test
    public void markdownSourceUsesStoreForSaveAsAndDiscard() throws Exception {
        Path file = tempDirectory.resolve("notes.md");
        Path moved = tempDirectory.resolve("moved.md");
        Files.writeString(file, "# Original");
        onFxThread(() -> {
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.CODE));
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            assertNotNull(editor);
            disableDebouncedAutosave(editor);
            DocumentId id = tab.documentId();
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            assertEquals(id, editor.captureSnapshot().id());
            editor.replaceText("# Saved markdown");
            assertTrue(tab.dirty());
            assertEquals(editor.getText(), ((TextDocumentSnapshot) store.current(id).orElseThrow()).text());
            assertTrue(Services.EDITOR_TAB_MANAGER.saveAsActive(moved));
            assertEquals(DocumentUri.fromPath(moved), store.current(id).orElseThrow().uri());
            assertNull(store.state(id).orElseThrow().overlay());
            editor.replaceText("# Discarded");
            closeManagedTab(true);
            assertEquals("# Saved markdown", ((TextDocumentSnapshot) store.current(id).orElseThrow()).text());
            assertNull(store.state(id).orElseThrow().overlay());
            tab = openInTabPane(moved);
            assertEquals(id, tab.documentId());
            assertEquals("# Saved markdown", tab.view().activeEditor().getText());
            return null;
        });
    }

    @Test
    public void markdownPreviewAttachesLazySourceEditorToStore() throws Exception {
        Path file = tempDirectory.resolve("preview.md");
        Files.writeString(file, "# Preview");
        onFxThread(() -> {
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.PREVIEW));
            tab = openInTabPane(file);
            assertNull(tab.view().activeEditor());
            TextEditorAccess access = tab.view().textEditor().orElseThrow();
            assertSame(access, Services.DOCUMENT_EDITOR_STATE.getActiveTextEditor().orElseThrow());
            MarkdownPreviewPane markdown = (MarkdownPreviewPane) tab.view().content();
            markdown.applyLayout(MarkdownPreviewPane.MarkdownLayoutType.CODE);
            TextEditorPane editor = tab.view().activeEditor();
            assertNotNull(editor);
            assertSame(access, Services.DOCUMENT_EDITOR_STATE.getActiveTextEditor().orElseThrow());
            disableDebouncedAutosave(editor);
            editor.replaceText("# Lazy draft");
            assertTrue(tab.dirty());
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            assertEquals(editor.getText(),
                ((TextDocumentSnapshot) store.current(tab.documentId()).orElseThrow()).text());
            assertTrue(Services.EDITOR_TAB_MANAGER.saveActive().failedTabs().isEmpty());
            assertFalse(tab.dirty());
            assertEquals("# Lazy draft", Files.readString(file));
            return null;
        });
    }

    @Test
    public void previewFirstReopenOwnsRetainedDraftWithoutRevealingSource() throws Exception {
        Path file = tempDirectory.resolve("retained.md");
        Files.writeString(file, "# Disk");
        onFxThread(() -> {
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.CODE));
            tab = openInTabPane(file);
            TextEditorPane source = tab.view().activeEditor();
            disableDebouncedAutosave(source);
            source.replaceText("# Retained draft");
            TextDocumentSnapshot draft = source.textEditorAccess().snapshot();
            source.closeRetainingDraft();
            tab.tab().getTabPane().getTabs().remove(tab.tab());
            Event.fireEvent(tab.tab(), new Event(tab.tab(), tab.tab(), Tab.CLOSED_EVENT));
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.PREVIEW));
            tab = openInTabPane(file);
            MarkdownPreviewPane markdown = (MarkdownPreviewPane) tab.view().content();
            TextEditorAccess access = tab.view().textEditor().orElseThrow();
            assertNotNull(tab.view().activeEditor());
            assertTrue(tab.dirty());
            assertEquals(MarkdownPreviewPane.MarkdownLayoutType.PREVIEW, markdownField(markdown, "currentLayout"));
            assertSame(draft, access.snapshot());
            assertEquals(draft.text(), markdownField(markdown, "renderedText"));
            assertEquals("# Disk", Files.readString(file));
            assertTrue(Services.EDITOR_TAB_MANAGER.saveActive().successful());
            assertFalse(tab.dirty());
            assertEquals(draft.text(), Files.readString(file));
            assertEquals(EditResult.APPLIED, access.selectAndReveal(draft, new TextSelection(2, 10)));
            assertEquals(draft.text(), tab.view().activeEditor().getText());
            assertEquals(draft.text(), markdownField(markdown, "renderedText"));
            assertEquals(MarkdownPreviewPane.MarkdownLayoutType.SPLIT, markdownField(markdown, "currentLayout"));
            assertEquals(new TextSelection(2, 10), access.selection());
            assertSame(access, Services.DOCUMENT_EDITOR_STATE.getActiveTextEditor().orElseThrow());
            markdown.applyLayout(MarkdownPreviewPane.MarkdownLayoutType.PREVIEW);
            tab.view().activeEditor().replaceText("# Saved on project close");
            DocumentId id = tab.documentId();
            assertTrue(Services.EDITOR_TAB_MANAGER.prepareProjectClose());
            assertEquals("# Saved on project close", Files.readString(file));
            assertNull(Services.IDE_STATE.getDocumentStore().orElseThrow().state(id).orElseThrow().overlay());
            tab = null;
            return null;
        });
    }

    @Test
    public void previewOnlyFollowsExternalBackingAndStopsQueuedObserversOnClose() throws Exception {
        Path file = tempDirectory.resolve("external-preview.md");
        Files.writeString(file, "# Before");
        var observed = new AtomicReference<TextDocumentSnapshot>();
        var callbacks = new AtomicInteger();
        var access = new AtomicReference<TextEditorAccess>();
        var markdown = new AtomicReference<MarkdownPreviewPane>();
        ProjectDocumentSynchronizer synchronizer = onFxThread(() -> {
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.PREVIEW));
            tab = openInTabPane(file);
            access.set(tab.view().textEditor().orElseThrow());
            markdown.set((MarkdownPreviewPane) tab.view().content());
            access.get().observe(snapshot -> {
                observed.set(snapshot);
                callbacks.incrementAndGet();
            });
            access.get().observeSelection(_ -> {
            });
            assertNull(tab.view().activeEditor());
            Field field = Services.IDE_STATE.getClass().getDeclaredField("documentSynchronizer");
            field.setAccessible(true);
            return (ProjectDocumentSynchronizer) field.get(Services.IDE_STATE);
        });
        Files.writeString(file, "# External");
        synchronizer.refresh(file.toRealPath()).get(10, TimeUnit.SECONDS);
        onFxThread(() -> {
            assertEquals("# External", observed.get().text());
            assertEquals("# External", markdownField(markdown.get(), "renderedText"));
            assertNull(tab.view().activeEditor());
            int before = callbacks.get();
            TextDocumentSnapshot expected = access.get().snapshot();
            access.get().observe(_ -> callbacks.incrementAndGet());
            Services.EDITOR_TAB_MANAGER.close(tab);
            tab = null;
            assertEquals(EditResult.CLOSED, access.get().replace(expected, List.of()));
            assertNull(markdownField(markdown.get(), "storeSubscription"));
            assertTrue(((List<?>) markdownField(markdown.get(), "snapshotObservers")).isEmpty());
            assertTrue(((List<?>) markdownField(markdown.get(), "selectionObservers")).isEmpty());
            assertEquals(before, callbacks.get());
            return null;
        });
        int closedCallbacks = callbacks.get();
        onFxThread(() -> {
            assertEquals(closedCallbacks, callbacks.get());
            return null;
        });
    }

    @Test
    public void markdownVetoedCloseKeepsObservationUntilForcedReset() throws Exception {
        Path file = tempDirectory.resolve("veto.md");
        Files.writeString(file, "# Saved");
        var callbacks = new AtomicInteger();
        var markdown = new AtomicReference<MarkdownPreviewPane>();
        onFxThread(() -> {
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.SPLIT));
            tab = openInTabPane(file);
            markdown.set((MarkdownPreviewPane) tab.view().content());
            TextEditorPane source = tab.view().activeEditor();
            disableDebouncedAutosave(source);
            source.replaceText("# Local");
            tab.view().textEditor().orElseThrow().observe(_ -> callbacks.incrementAndGet());
            Files.delete(file);
            Files.createDirectory(file);
            markdown.get().close();
            assertFalse(source.isEditorClosed());
            assertEquals(false, markdownField(markdown.get(), "closed"));
            assertNotNull(markdownField(markdown.get(), "storeSubscription"));
            source.replaceText("# Continuing local");
            return null;
        });
        onFxThread(() -> {
            assertTrue(callbacks.get() > 0);
            assertEquals("# Continuing local", markdownField(markdown.get(), "renderedText"));
            var reset = EditorTabManager.class.getDeclaredMethod("resetTracking");
            reset.setAccessible(true);
            reset.invoke(Services.EDITOR_TAB_MANAGER);
            assertEquals(true, markdownField(markdown.get(), "closed"));
            assertNull(markdownField(markdown.get(), "storeSubscription"));
            assertTrue(tab.view().textEditor().isEmpty());
            assertTrue(Services.DOCUMENT_EDITOR_STATE.getActiveTextEditor().isEmpty());
            assertEquals("# Continuing local", ((TextDocumentSnapshot) Services.IDE_STATE.getDocumentStore()
                .orElseThrow().current(tab.documentId()).orElseThrow()).text());
            tab = null;
            return null;
        });
    }

    private static Object markdownField(MarkdownPreviewPane markdown, String name) throws ReflectiveOperationException {
        Field field = MarkdownPreviewPane.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(markdown);
    }

    @Test
    public void newerPublishedBackingWinsOverPreviewConstructionRead() throws Exception {
        Path file = tempDirectory.resolve("construction.md");
        Files.writeString(file, "# Earlier disk read");
        onFxThread(() -> {
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.PREVIEW));
            DocumentId id = DocumentId.create();
            var markdown = new MarkdownPreviewPane(id, file, project);
            try {
                DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                DocumentUri uri = DocumentUri.fromPath(file);
                var newer = new TextDocumentSnapshot(id, uri, new DocumentVersion(1), "markdown",
                    DocumentOrigin.original(uri), "# New authoritative content", StandardCharsets.UTF_8);
                store.publishBacking(newer);
                markdown.setDocumentStore(store);
                assertSame(newer, markdown.textEditorAccess().snapshot());
                assertSame(newer, store.current(id).orElseThrow());
                assertEquals(newer.text(), markdownField(markdown, "renderedText"));
                assertNull(markdown.getMarkdownEditorPane());
                var stale = new TextDocumentSnapshot(id, uri, DocumentVersion.initial(), "markdown",
                    DocumentOrigin.original(uri), "# Earlier disk read", StandardCharsets.UTF_8);
                assertEquals(EditResult.STALE,
                    markdown.textEditorAccess().selectAndReveal(stale, new TextSelection(0, 1)));
                assertNull(markdown.getMarkdownEditorPane());
            } finally {
                markdown.close();
            }
            return null;
        });
    }

    @Test
    public void hiddenMarkdownSourcePreviewFollowsReloadAndRetainsConflictOverlay() throws Exception {
        Path file = tempDirectory.resolve("hidden.md");
        Files.writeString(file, "# Original");
        var markdown = new AtomicReference<MarkdownPreviewPane>();
        var draft = new AtomicReference<TextDocumentSnapshot>();
        onFxThread(() -> {
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.CODE));
            tab = openInTabPane(file);
            TextEditorPane source = tab.view().activeEditor();
            disableDebouncedAutosave(source);
            markdown.set((MarkdownPreviewPane) tab.view().content());
            markdown.get().applyLayout(MarkdownPreviewPane.MarkdownLayoutType.PREVIEW);
            Files.writeString(file, "# Clean external reload");
            var handleExternal = TextEditorPane.class.getDeclaredMethod("handleExternalChange", Path.class);
            handleExternal.setAccessible(true);
            handleExternal.invoke(source, file);
            return null;
        });
        onFxThread(() -> null);
        onFxThread(() -> {
            assertEquals("# Clean external reload", markdownField(markdown.get(), "renderedText"));
            TextEditorPane source = tab.view().activeEditor();
            source.replaceText("# Unsaved source");
            draft.set(tab.view().textEditor().orElseThrow().snapshot());
            var publishExternal = TextEditorPane.class.getDeclaredMethod("publishExternalBacking", String.class);
            publishExternal.setAccessible(true);
            publishExternal.invoke(source, "# Different external backing");
            assertTrue(source.captureSnapshot().version().isAfter(draft.get().version()));
            assertSame(draft.get(), tab.view().textEditor().orElseThrow().snapshot());
            return null;
        });
        onFxThread(() -> {
            assertEquals(draft.get().text(), markdownField(markdown.get(), "renderedText"));
            assertEquals(MarkdownPreviewPane.MarkdownLayoutType.PREVIEW,
                markdownField(markdown.get(), "currentLayout"));
            return null;
        });
    }

    @Test
    public void ignoredAndExternalMarkdownPreviewsRetainExistingSourceWatcher(@TempDir Path externalDirectory)
        throws Exception {
        Path ignored = Files.createDirectories(tempDirectory.resolve(".gradle")).resolve("ignored.md");
        for (Path file : List.of(ignored, externalDirectory.resolve("outside.md"))) {
            Files.writeString(file, "# Initial unwatched source");
            var changed = new CompletableFuture<TextDocumentSnapshot>();
            onFxThread(() -> {
                project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                    MarkdownPreviewPane.MarkdownLayoutType.PREVIEW));
                assertFalse(Services.IDE_STATE.isDocumentWatched(file));
                tab = openInTabPane(file);
                assertNotNull(tab.view().activeEditor());
                assertEquals(MarkdownPreviewPane.MarkdownLayoutType.PREVIEW,
                    markdownField((MarkdownPreviewPane) tab.view().content(), "currentLayout"));
                tab.view().textEditor().orElseThrow().observe(snapshot -> {
                    if (snapshot.text().equals("# External watcher update")) {
                        changed.complete(snapshot);
                    }
                });
                return null;
            });
            Files.writeString(file, "# External watcher update");
            assertEquals("# External watcher update", changed.get(10, TimeUnit.SECONDS).text());
            onFxThread(() -> {
                MarkdownPreviewPane markdown = (MarkdownPreviewPane) tab.view().content();
                assertEquals("# External watcher update", markdownField(markdown, "renderedText"));
                assertEquals(MarkdownPreviewPane.MarkdownLayoutType.PREVIEW, markdownField(markdown, "currentLayout"));
                assertFalse(tab.dirty());
                closeManagedTab(false);
                return null;
            });
        }
    }

    @Test
    public void movingLazyMarkdownPreviewOutsideWatcherCoverageAcquiresSourceWatcher() throws Exception {
        Path source = tempDirectory.resolve("watched.md");
        Path moved = Files.createDirectories(tempDirectory.resolve(".gradle")).resolve("moved.md");
        Files.writeString(source, "# Before move");
        var changed = new CompletableFuture<TextDocumentSnapshot>();
        onFxThread(() -> {
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.PREVIEW));
            assertTrue(Services.IDE_STATE.isDocumentWatched(source));
            assertFalse(Services.IDE_STATE.isDocumentWatched(moved));
            tab = openInTabPane(source);
            assertNull(tab.view().activeEditor());
            TextEditorAccess access = tab.view().textEditor().orElseThrow();
            TextDocumentSnapshot original = access.snapshot();
            Files.move(source, moved);
            Railroad.EVENT_BUS.publish(new DocumentRenamedEvent(new FileSystemDocument(moved, "markdown"),
                source.getFileName().toString(), moved.getFileName().toString(), DocumentUri.fromPath(source)));
            assertEquals(original.id(), tab.documentId());
            assertNotNull(tab.view().activeEditor());
            assertEquals(DocumentUri.fromPath(moved), access.snapshot().uri());
            assertEquals(EditResult.STALE, access.selectAndReveal(original, new TextSelection(0, 1)));
            assertEquals(MarkdownPreviewPane.MarkdownLayoutType.PREVIEW,
                markdownField((MarkdownPreviewPane) tab.view().content(), "currentLayout"));
            access.observe(snapshot -> {
                if (snapshot.text().equals("# External after move")) {
                    changed.complete(snapshot);
                }
            });
            return null;
        });
        Files.writeString(moved, "# External after move");
        assertEquals("# External after move", changed.get(10, TimeUnit.SECONDS).text());
        onFxThread(() -> {
            assertEquals("# External after move",
                markdownField((MarkdownPreviewPane) tab.view().content(), "renderedText"));
            assertFalse(tab.dirty());
            return null;
        });
    }

    @Test
    public void changedReopenAdvancesBeyondDiscardedOverlay() throws Exception {
        Path file = tempDirectory.resolve("History.java");
        Files.writeString(file, "class History {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText("class History { int discarded; }");
            DocumentId id = tab.documentId();
            DocumentVersion discarded = editor.captureSnapshot().version();
            closeManagedTab(true);
            Files.writeString(file, "class History { int external; }");
            tab = openInTabPane(file);
            disableDebouncedAutosave(tab.view().activeEditor());
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            assertEquals(discarded.next(), store.current(id).orElseThrow().version());
            tab.view().activeEditor().appendText("\n");
            assertTrue(store.current(id).orElseThrow().version().isAfter(discarded.next()));
            return null;
        });
    }

    @Test
    public void directSaveAsCannotBypassAttachedDocumentOwner() throws Exception {
        Path source = tempDirectory.resolve("Owned.java");
        Path target = tempDirectory.resolve("Bypassed.java");
        Files.writeString(source, "class Owned {}");
        onFxThread(() -> {
            tab = openInTabPane(source);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText("class Owned { int draft; }");
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            WorkspaceSnapshot before = store.capture();
            assertFalse(editor.saveAs(target));
            assertFalse(editor.saveAs(target, () -> fail("Unmanaged finalizer must not run")));
            assertFalse(Files.exists(target));
            assertEquals(source, editor.getFilePath());
            assertEquals(before, store.capture());
            assertTrue(editor.saveAs(source));
            assertNull(store.state(tab.documentId()).orElseThrow().overlay());
            return null;
        });
    }

    @Test
    public void unreadableReopenRestoresRetainedDraft() throws Exception {
        assertUnreadableDraftRecovery(false);
    }

    @Test
    public void unreadableReopenRestoresDraftWithoutBacking() throws Exception {
        assertUnreadableDraftRecovery(true);
    }

    private void assertUnreadableDraftRecovery(boolean removeBacking) throws Exception {
        Path file = tempDirectory.resolve("Recovery.java");
        Files.writeString(file, "class Recovery {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText("class Recovery { int preciousDraft; }");
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            DocumentId id = tab.documentId();
            TextDocumentSnapshot draft = (TextDocumentSnapshot) store.current(id).orElseThrow();
            editor.closeRetainingDraft();
            tab.tab().getTabPane().getTabs().remove(tab.tab());
            Event.fireEvent(tab.tab(), new Event(tab.tab(), tab.tab(), Tab.CLOSED_EVENT));
            if (removeBacking) {
                store.removeBacking(id, store.state(id).orElseThrow().backing().version());
            }
            Files.write(file, new byte[]{(byte) 0xc3, 0x28});
            WorkspaceSnapshot before = store.capture();
            tab = openInTabPane(file);
            TextEditorPane recovered = tab.view().activeEditor();
            disableDebouncedAutosave(recovered);
            assertFalse(recovered.isInitialContentLoaded());
            assertEquals(draft.text(), recovered.getText());
            assertEquals(draft.version(), recovered.captureSnapshot().version());
            assertSame(draft, store.current(id).orElseThrow());
            assertEquals(before, store.capture());
            assertEquals(EditorSaveState.ERROR, recovered.saveState());
            assertFalse(recovered.saveNow());
            assertArrayEquals(new byte[]{(byte) 0xc3, 0x28}, Files.readAllBytes(file));
            recovered.replaceText(draft.text() + " // continued");
            assertEquals(recovered.getText(), ((TextDocumentSnapshot) store.current(id).orElseThrow()).text());
            return null;
        });
    }

    @Test
    public void saveAsRemainsSuccessfulWhenPostCommitWatcherRestartFails() throws Exception {
        Path source = tempDirectory.resolve("Original.java");
        Path destination = tempDirectory.resolve("Saved.java");
        Files.writeString(source, "class Original {}");
        onFxThread(() -> {
            tab = openInTabPane(source);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText("class Original { int local; }");
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            long revision = store.capture().revision();
            var savedEvents = new AtomicInteger();
            EventListener<DocumentEvent> savedListener = event -> {
                if (event.isSavedEvent() && event.file().getUri().equals(DocumentUri.fromPath(destination))) {
                    savedEvents.incrementAndGet();
                }
            };
            Field watcherField = TextEditorPane.class.getDeclaredField("watcherExecutor");
            watcherField.setAccessible(true);
            ExecutorService watcher = (ExecutorService) watcherField.get(editor);
            Field storeField = Services.IDE_STATE.getClass().getDeclaredField("documentStore");
            storeField.setAccessible(true);
            DocumentStore injectingStore = (DocumentStore) Proxy.newProxyInstance(DocumentStore.class.getClassLoader(),
                new Class<?>[]{DocumentStore.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(store, args);
                        if (method.getName().equals("commitSaveAs")) {
                            // The disk write and store commit succeed. Reject the subsequent watcher submission.
                            watcher.shutdownNow();
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
            Railroad.EVENT_BUS.subscribe(DocumentEvent.class, savedListener);
            storeField.set(Services.IDE_STATE, injectingStore);
            try {
                assertTrue(Services.EDITOR_TAB_MANAGER.saveAsActive(destination));
            } finally {
                storeField.set(Services.IDE_STATE, store);
                Railroad.EVENT_BUS.unsubscribe(DocumentEvent.class, savedListener);
            }
            assertEquals(1, savedEvents.get(), "Watcher failure must not suppress the save notification");
            assertEquals(destination, editor.getFilePath());
            assertEquals(destination, tab.path());
            assertEquals(DocumentUri.fromPath(destination.toRealPath()), tab.identity().uri());
            TextDocumentSnapshot backing = (TextDocumentSnapshot) store.current(tab.documentId()).orElseThrow();
            assertEquals(editor.getText(), Files.readString(destination));
            assertEquals(editor.getText(), backing.text());
            assertEquals(DocumentUri.fromPath(destination), backing.uri());
            assertEquals(backing.version(), editor.captureSnapshot().version());
            assertEquals(EditorSaveState.CLEAN, editor.saveState());
            assertNull(store.state(tab.documentId()).orElseThrow().overlay());
            assertEquals(revision + 1, store.capture().revision());
            editor.replaceText("class Original { int next; }");
            assertTrue(editor.saveNow());
            assertEquals(editor.getText(), Files.readString(destination));
            return null;
        });
    }

    @Test
    public void saveAsStoreFailureRollsBackLocationAndRetainsRecoverableDraft() throws Exception {
        assertSaveAsStoreFailure(false);
    }

    @Test
    public void saveAsStoreFailureRetainsStaleOverlayAndOriginalVersion() throws Exception {
        assertSaveAsStoreFailure(true);
    }

    private void assertSaveAsStoreFailure(boolean stale) throws Exception {
        Path source = tempDirectory.resolve("Old.java");
        Path destination = tempDirectory.resolve("New.java");
        Files.writeString(source, "class Old {}");
        onFxThread(() -> {
            tab = openInTabPane(source);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText("class Old { int local; }");
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            if (stale) {
                Files.writeString(source, "class Old { int external; }");
                store.publishBacking(new TextDocumentSnapshot(tab.documentId(), DocumentUri.fromPath(source),
                    editor.captureSnapshot().version().next(), "java", "class Old { int external; }",
                    StandardCharsets.UTF_8));
            }
            WorkspaceSnapshot before = store.capture();
            DocumentVersion editorVersion = editor.captureSnapshot().version();
            DocumentIdentity identity = tab.identity();
            Field storeField = Services.IDE_STATE.getClass().getDeclaredField("documentStore");
            storeField.setAccessible(true);
            DocumentStore failingStore = (DocumentStore) Proxy.newProxyInstance(DocumentStore.class.getClassLoader(),
                new Class<?>[]{DocumentStore.class}, (proxy, method, args) -> {
                    if (method.getName().equals("commitSaveAs"))
                        throw new IllegalStateException("Injected Save As store failure");
                    try {
                        return method.invoke(store, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
            storeField.set(Services.IDE_STATE, failingStore);
            try {
                assertFalse(Services.EDITOR_TAB_MANAGER.saveAsActive(destination));
            } finally {
                storeField.set(Services.IDE_STATE, store);
            }
            assertEquals(before, store.capture());
            assertEquals(identity, tab.identity());
            assertEquals(source, tab.path());
            assertEquals(source, editor.getFilePath());
            assertEquals(editorVersion, editor.captureSnapshot().version());
            assertEquals(DocumentUri.fromPath(source), tab.document().getUri());
            assertEquals(identity, Services.IDE_STATE.findDocumentIdentity(identity.uri()).orElseThrow());
            assertTrue(Services.IDE_STATE.findDocumentIdentity(DocumentUri.fromPath(destination)).isEmpty());
            assertEquals("class Old { int local; }", editor.getText());
            assertEquals(editor.getText(), ((TextDocumentSnapshot) store.current(identity.id()).orElseThrow()).text());
            assertEquals(editor.getText(), Files.readString(destination));
            assertEquals(stale ? "class Old { int external; }" : "class Old {}", Files.readString(source));
            assertTrue(editor.dirty());
            assertTrue(Services.EDITOR_TAB_MANAGER.saveAsActive(destination));
            assertEquals(destination, editor.getFilePath());
            assertEquals(DocumentUri.fromPath(destination), store.current(identity.id()).orElseThrow().uri());
            assertNull(store.state(identity.id()).orElseThrow().overlay());
            assertEquals(before.revision() + 1, store.capture().revision());
            return null;
        });
    }

    @Test
    public void forcedClosureAfterFailedWriteRetainsDraftAndStopsEditorResources() throws Exception {
        Path file = tempDirectory.resolve("Forced.java");
        Files.writeString(file, "class Forced {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText("class Forced { int local; }");
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            DocumentState before = store.state(tab.documentId()).orElseThrow();
            // A directory at the original path deterministically makes persistence fail.
            Files.delete(file);
            Files.createDirectory(file);
            tab.tab().getTabPane().getTabs().remove(tab.tab());
            Event.fireEvent(tab.tab(), new Event(tab.tab(), tab.tab(), Tab.CLOSED_EVENT));
            assertTrue(editor.isEditorClosed());
            assertSame(before.overlay(), store.state(tab.documentId()).orElseThrow().overlay());
            Field watcher = TextEditorPane.class.getDeclaredField("watcherExecutor");
            watcher.setAccessible(true);
            assertTrue(((ExecutorService) watcher.get(editor)).isShutdown());
            Field requests = CodeEditorPane.class.getDeclaredField("featureRequests");
            requests.setAccessible(true);
            var scope = (SstScheduler.Scope) requests.get(editor);
            assertTrue(scope.completion().isDone());
            var cancellation = new CancellationTokenSource();
            var context = new WorkspaceRequestContext(store.capture(), cancellation.token(), Deadline.none());
            var rejected = scope.submit("closed-editor", RequestPriority.COMPLETION, context, cancellation,
                () -> fail("A closed editor must not dispatch additional work"));
            assertInstanceOf(RejectedExecutionException.class,
                assertThrows(CompletionException.class, rejected::join).getCause());
            DocumentId id = tab.documentId();
            Files.delete(file);
            Files.writeString(file, "class Forced {}");
            tab = openInTabPane(file);
            assertEquals(id, tab.documentId());
            assertEquals("class Forced { int local; }", tab.view().activeEditor().getText());
            assertTrue(tab.view().activeEditor().dirty());
            return null;
        });
    }

    @Test
    public void projectClosePreparationKeepsFailedEditorUntilDiscardApproved() throws Exception {
        Path file = tempDirectory.resolve("Shutdown.java");
        Files.writeString(file, "class Shutdown {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText("class Shutdown { int local; }");
            Files.delete(file);
            editor.markBackingFileDeleted();
            // The application normally initializes dialog styles. Register a harmless
            // existing dialog so this lifecycle test does not construct unrelated UI.
            Field dialogs = EditorTabManager.class.getDeclaredField("failedCloseDialogs");
            dialogs.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<EditorTab, Stage> failedDialogs = (Map<EditorTab, Stage>) dialogs.get(Services.EDITOR_TAB_MANAGER);
            failedDialogs.put(tab, new Stage());
            assertFalse(Services.EDITOR_TAB_MANAGER.prepareProjectClose());
            assertFalse(editor.isEditorClosed());
            assertNotNull(tab.tab().getTabPane());
            assertTrue(Services.IDE_STATE.getDocumentStore().orElseThrow().state(tab.documentId()).orElseThrow()
                .overlay() != null);
            Services.EDITOR_TAB_MANAGER.discardUnsavedChangesOnClose();
            assertTrue(Services.EDITOR_TAB_MANAGER.prepareProjectClose());
            assertTrue(editor.isEditorClosed());
            assertTrue(Services.IDE_STATE.getDocumentStore().orElseThrow().state(tab.documentId()).isEmpty());
            tab = null;
            return null;
        });
    }

    @Test
    public void projectCloseFailureLeavesEveryTabOpenAndSessionIntact() throws Exception {
        Path firstFile = tempDirectory.resolve("First.java");
        Path secondFile = tempDirectory.resolve("Second.java");
        Files.writeString(firstFile, "class First {}");
        Files.writeString(secondFile, "class Second {}");
        onFxThread(() -> {
            EditorTab first = openInTabPane(firstFile);
            tab = openInTabPane(secondFile);
            TextEditorPane firstEditor = first.view().activeEditor();
            TextEditorPane secondEditor = tab.view().activeEditor();
            disableDebouncedAutosave(firstEditor);
            disableDebouncedAutosave(secondEditor);
            firstEditor.replaceText("class First { int savedInPreflight; }");
            secondEditor.replaceText("class Second { int retained; }");
            Files.delete(secondFile);
            secondEditor.markBackingFileDeleted();
            Field dialogs = EditorTabManager.class.getDeclaredField("failedCloseDialogs");
            dialogs.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<EditorTab, Stage> failedDialogs = (Map<EditorTab, Stage>) dialogs.get(Services.EDITOR_TAB_MANAGER);
            failedDialogs.put(tab, new Stage());
            assertFalse(Services.EDITOR_TAB_MANAGER.prepareProjectClose());
            assertFalse(firstEditor.isEditorClosed());
            assertFalse(secondEditor.isEditorClosed());
            assertNotNull(first.tab().getTabPane());
            assertNotNull(tab.tab().getTabPane());
            assertEquals(2, Services.EDITOR_TAB_MANAGER.captureSessionState().size());
            assertEquals(firstEditor.getText(), Files.readString(firstFile));
            Services.EDITOR_TAB_MANAGER.discardUnsavedChangesOnClose();
            assertTrue(Services.EDITOR_TAB_MANAGER.prepareProjectClose());
            assertTrue(firstEditor.isEditorClosed());
            assertTrue(secondEditor.isEditorClosed());
            tab = null;
            return null;
        });
    }

    @BeforeEach
    public void openProject() throws Exception {
        onFxThread(() -> {
            LanguageSupports.initialize();
            if (ThemeManager.getTheme() == null) {
                ThemeManager.setTheme("");
            }
            project = new RailroadProject(tempDirectory, "Backing publication test", new WritableImage(1, 1));
            Services.IDE_STATE.setCurrentProject(project);
            return null;
        });
        Services.IDE_STATE.getWorkspaceConfiguration().ready().get(10, TimeUnit.SECONDS);
        initialWorkspaceRevision = Services.IDE_STATE.getDocumentStore().orElseThrow().capture().revision();
    }

    private long initialWorkspaceRevision;

    @Test
    public void projectStateTransitionDisposesLanguageSessionsEvenWhenProviderCleanupFails() throws Exception {
        var disposed = new AtomicInteger();
        var resource = new CapabilityKey<>("test:project-close-resource", AutoCloseable.class);
        LanguageContribution contribution = new LanguageContribution() {
            @Override
            public LanguageId id() {
                return new LanguageId("test:project-close");
            }

            @Override
            public String displayName() {
                return "Project close test";
            }

            @Override
            public LanguageMatcher matcher() {
                return LanguageMatcher.extension("project-close");
            }

            @Override
            public Set<CapabilityKey<?>> supportedCapabilities() {
                return Set.of(resource);
            }

            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                return new LanguageSession(CapabilityRegistry.builder().add(resource, () -> {
                    disposed.incrementAndGet();
                    throw new IllegalStateException("Provider cleanup failure");
                }).build());
            }
        };
        var runtime = LanguageSupportRegistry.runtime();
        runtime.register("project-close-test", contribution);
        try {
            onFxThread(() -> {
                var session = runtime.session(project, contribution.id(), "");
                assertDoesNotThrow(() -> Services.IDE_STATE.setCurrentProject(null));
                assertTrue(session.isClosed());
                assertEquals(1, disposed.get());
                assertNull(Services.IDE_STATE.getCurrentProject());
                assertTrue(Services.IDE_STATE.getDocumentStore().isEmpty());
                assertNull(Services.PROJECT_LANGUAGE_INDEX_SERVICE.session(project));
                return null;
            });
        } finally {
            runtime.unregisterOwner("project-close-test");
        }
    }

    @AfterEach
    public void closeProject() throws Exception {
        onFxThread(() -> {
            try {
                if (tab != null) {
                    var editor = tab.view().activeEditor();
                    if (editor != null) {
                        editor.discardChangesOnClose();
                    }
                    if (tab.tab().getTabPane() != null) {
                        tab.tab().getTabPane().getTabs().remove(tab.tab());
                    }
                    Event.fireEvent(tab.tab(), new Event(tab.tab(), tab.tab(), Tab.CLOSED_EVENT));
                    if (editor != null) {
                        editor.close();
                        editor.dispose();
                    }
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
            assertEquals(initialWorkspaceRevision + 1, store.capture().revision());
            return null;
        });
    }

    @Test
    public void autosavedDocumentClosesAndReopensWithoutAbandonedOverlay() throws Exception {
        Path file = tempDirectory.resolve("Autosave.java");
        String editedText = "class Autosave { int value; }";
        Files.writeString(file, "class Autosave {}");
        CompletableFuture<TextDocumentSnapshot> committed = new CompletableFuture<>();
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            editor.setDocumentStore((DocumentStore) Proxy.newProxyInstance(DocumentStore.class.getClassLoader(),
                new Class<?>[]{DocumentStore.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(store, args);
                        if (method.getName().equals("commitOverlay")) {
                            committed
                                .complete((TextDocumentSnapshot) store.state(tab.documentId()).orElseThrow().backing());
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }));
            editor.replaceText(editedText);
            return null;
        });
        TextDocumentSnapshot saved = committed.get(10, TimeUnit.SECONDS);
        onFxThread(() -> {
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            DocumentId id = tab.documentId();
            closeManagedTab(false);
            assertSame(saved, store.current(id).orElseThrow());
            assertNull(store.state(id).orElseThrow().overlay());
            WorkspaceSnapshot closed = store.capture();
            tab = openInTabPane(file);
            assertEquals(id, tab.documentId());
            assertEquals(saved.version(), tab.view().activeEditor().captureSnapshot().version());
            assertEquals(editedText, tab.view().activeEditor().getText());
            assertEquals(closed, store.capture());
            assertEquals(editedText, Files.readString(file));
            return null;
        });
    }

    @Test
    public void discardingOnCloseRemovesOverlayAndRetainsBacking() throws Exception {
        Path file = tempDirectory.resolve("Discard.java");
        Files.writeString(file, "class Discard {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            DocumentId id = tab.documentId();
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            TextDocumentSnapshot backing = (TextDocumentSnapshot) store.current(id).orElseThrow();
            editor.replaceText("class Discard { int unsaved; }");
            DocumentVersion discardedVersion = editor.captureSnapshot().version();
            closeManagedTab(true);
            assertSame(backing, store.current(id).orElseThrow());
            assertNull(store.state(id).orElseThrow().overlay());
            assertEquals(backing.text(), Files.readString(file));
            tab = openInTabPane(file);
            assertEquals(backing.text(), tab.view().activeEditor().getText());
            tab.view().activeEditor().replaceText("class Discard { int fresh; }");
            assertTrue(store.state(id).orElseThrow().overlay().version().isAfter(discardedVersion));
            return null;
        });
    }

    @Test
    public void discardingStaleOverlayOnCloseRetainsExternalBacking() throws Exception {
        Path file = tempDirectory.resolve("Stale.java");
        Files.writeString(file, "class Stale {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            DocumentId id = tab.documentId();
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            editor.replaceText("class Stale { int unsaved; }");
            var external = new TextDocumentSnapshot(id, DocumentUri.fromPath(file),
                editor.captureSnapshot().version().next(), "java", DocumentOrigin.original(DocumentUri.fromPath(file)),
                "class Stale { int external; }", StandardCharsets.UTF_8);
            Files.writeString(file, external.text());
            store.publishBacking(external);
            assertTrue(store.state(id).orElseThrow().isStale());
            closeManagedTab(true);
            assertSame(external, store.current(id).orElseThrow());
            assertNull(store.state(id).orElseThrow().overlay());
            assertEquals(external.text(), Files.readString(file));
            return null;
        });
    }

    @Test
    public void deletedDocumentCanBeDiscardedRecreatedAndReopened() throws Exception {
        Path file = tempDirectory.resolve("Recreated.java");
        Files.writeString(file, "class Recreated {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            DocumentId id = tab.documentId();
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            DocumentVersion oldVersion = store.current(id).orElseThrow().version();
            Files.delete(file);
            editor.markBackingFileDeleted();
            DocumentVersion deletedVersion = store.current(id).orElseThrow().version();
            closeManagedTab(true);
            assertTrue(store.state(id).isEmpty());
            assertEquals(oldVersion, store.lastBackingVersion(id).orElseThrow());
            Files.writeString(file, "class Recreated { int fresh; }");
            tab = openInTabPane(file);
            assertEquals(id, tab.documentId());
            TextDocumentSnapshot backing = (TextDocumentSnapshot) store.current(id).orElseThrow();
            assertEquals(deletedVersion.next(), backing.version());
            assertEquals("class Recreated { int fresh; }", backing.text());
            assertNull(store.state(id).orElseThrow().overlay());
            tab.view().activeEditor().replaceText("class Recreated { int edited; }");
            assertTrue(store.state(id).orElseThrow().overlay().version().isAfter(backing.version()));
            return null;
        });
    }

    @Test
    public void movingDirtyDocumentRelocatesBothSnapshotsBeforeSaving() throws Exception {
        Path file = tempDirectory.resolve("Move.java");
        Path destination = Files.createDirectories(tempDirectory.resolve("moved")).resolve("Moved.java");
        Files.writeString(file, "class Move {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            DocumentId id = tab.documentId();
            editor.replaceText("class Move { int unsaved; }");
            DocumentState before = store.state(id).orElseThrow();
            Files.move(file, destination);
            Railroad.EVENT_BUS.publish(new DocumentRenamedEvent(new FileSystemDocument(destination, "java"),
                file.getFileName().toString(), destination.getFileName().toString(), DocumentUri.fromPath(file)));
            DocumentState moved = store.state(id).orElseThrow();
            assertEquals(DocumentUri.fromPath(destination), moved.backing().uri());
            assertEquals(DocumentUri.fromPath(destination), moved.overlay().uri());
            assertEquals(before.backing().version(), moved.backing().version());
            assertEquals(before.overlay().version(), moved.overlay().version());
            assertEquals(before.baseBackingVersion(), moved.baseBackingVersion());
            editor.replaceText("class Move { int next; }");
            assertTrue(editor.saveNow());
            assertEquals("class Move { int next; }", Files.readString(destination));
            assertNull(store.state(id).orElseThrow().overlay());
            assertEquals(DocumentUri.fromPath(destination), store.current(id).orElseThrow().uri());
            return null;
        });
    }

    private void closeManagedTab(boolean discard) {
        EditorTab closing = tab;
        TextEditorPane editor = closing.view().activeEditor();
        TabPane pane = closing.tab().getTabPane();
        if (discard) {
            Services.EDITOR_TAB_MANAGER.discardUnsavedChangesOnClose();
        }
        Services.EDITOR_TAB_MANAGER.close(closing);
        assertTrue(editor.isEditorClosed());
        assertFalse(pane.getTabs().contains(closing.tab()));
        tab = null;
    }

    @Test
    public void saveAsAfterExternalChangeReconcilesBeforePromotingLocalContent() throws Exception {
        Path file = tempDirectory.resolve("Conflict.java");
        Path target = tempDirectory.resolve("Kept.java");
        Files.writeString(file, "class Conflict {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText("class Conflict { int local; }");
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            DocumentId id = tab.documentId();
            var external = new TextDocumentSnapshot(id, DocumentUri.fromPath(file),
                editor.captureSnapshot().version().next(), "java", "class Conflict { int external; }",
                StandardCharsets.UTF_8);
            Files.writeString(file, external.text());
            store.publishBacking(external);
            assertTrue(Services.EDITOR_TAB_MANAGER.saveAsActive(target));
            DocumentState saved = store.state(id).orElseThrow();
            assertNull(saved.overlay());
            assertEquals("class Conflict { int local; }", ((TextDocumentSnapshot) saved.backing()).text());
            assertTrue(saved.backing().version().isAfter(external.version()));
            assertEquals(DocumentUri.fromPath(target), saved.backing().uri());
            editor.replaceText("class Conflict { int later; }");
            assertTrue(editor.saveNow());
            assertEquals("class Conflict { int later; }", Files.readString(target));
            assertNull(store.state(id).orElseThrow().overlay());
            assertEquals(external.text(), Files.readString(file));
            return null;
        });
    }

    @Test
    public void failedCloseRetainsUnsavedOverlayUntilExplicitDiscard() throws Exception {
        Path file = tempDirectory.resolve("Unsaved.java");
        Files.writeString(file, "class Unsaved {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            DocumentId id = tab.documentId();
            editor.replaceText("class Unsaved { int local; }");
            Files.delete(file);
            editor.markBackingFileDeleted();
            WorkspaceSnapshot before = store.capture();
            editor.close();
            assertFalse(editor.isEditorClosed());
            assertEquals(before, store.capture());
            assertEquals("class Unsaved { int local; }", editor.getText());
            assertSame(before.document(id).orElseThrow(), store.current(id).orElseThrow());
            closeManagedTab(true);
            assertTrue(store.state(id).isEmpty());
            return null;
        });
    }

    @Test
    public void reopeningRetainedDraftRestoresEditorAndStoreToSameContent() throws Exception {
        Path file = tempDirectory.resolve("Recovered.java");
        Files.writeString(file, "class Recovered {}");
        onFxThread(() -> {
            DocumentId id = Services.IDE_STATE.identifyDocument(new FileSystemDocument(file, "java")).id();
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            DocumentUri uri = DocumentUri.fromPath(file);
            var backing = new TextDocumentSnapshot(id, uri, new DocumentVersion(4), "java",
                "class Recovered {}", StandardCharsets.UTF_8);
            var draft = new TextDocumentSnapshot(id, uri, new DocumentVersion(5), "java",
                "class Recovered { int draft; }", StandardCharsets.UTF_8);
            store.publishBacking(backing);
            store.publishOverlay(draft);
            WorkspaceSnapshot before = store.capture();
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            assertEquals(draft.text(), editor.getText());
            assertEquals(draft.version(), editor.captureSnapshot().version());
            assertSame(draft, store.current(id).orElseThrow());
            assertEquals(before, store.capture());
            editor.replaceText("class Recovered { int next; }");
            assertTrue(store.current(id).orElseThrow().version().isAfter(draft.version()));
            closeManagedTab(true);
            assertSame(backing, store.current(id).orElseThrow());
            return null;
        });
    }

    @Test
    public void externalChangeMatchingLocalTextMakesBackingAuthoritative() throws Exception {
        Path file = tempDirectory.resolve("Matched.java");
        String text = "class Matched { int local; }";
        Files.writeString(file, "class Matched {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            editor.replaceText(text);
            Files.writeString(file, text);
            Method handler = TextEditorPane.class.getDeclaredMethod("handleExternalChange",
                Path.class);
            handler.setAccessible(true);
            handler.invoke(editor, file);
            return null;
        });
        onFxThread(() -> {
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            DocumentState state = store.state(tab.documentId()).orElseThrow();
            assertNull(state.overlay());
            assertEquals(text, ((TextDocumentSnapshot) state.backing()).text());
            assertEquals(state.backing().version(), tab.view().activeEditor().captureSnapshot().version());
            assertEquals(EditorSaveState.CLEAN, tab.view().activeEditor().saveState());
            assertFalse(tab.view().activeEditor().hasPendingExternalChange());
            return null;
        });
    }

    @Test
    public void movingClosedDocumentKeepsIdentityAndReusesRelocatedBacking() throws Exception {
        Path file = tempDirectory.resolve("Closed.java");
        Path moved = Files.createDirectories(tempDirectory.resolve("other")).resolve("Moved.java");
        Files.writeString(file, "class Closed {}");
        onFxThread(() -> {
            tab = openInTabPane(file);
            DocumentId id = tab.documentId();
            closeManagedTab(false);
            Files.move(file, moved);
            Railroad.EVENT_BUS.publish(new DocumentRenamedEvent(new FileSystemDocument(moved, "java"),
                "Closed.java", "Moved.java", DocumentUri.fromPath(file)));
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            WorkspaceSnapshot relocated = store.capture();
            assertEquals(DocumentUri.fromPath(moved), store.current(id).orElseThrow().uri());
            tab = openInTabPane(moved);
            assertEquals(id, tab.documentId());
            assertEquals(relocated, store.capture());
            return null;
        });
    }

    @Test
    public void editsAfterFailedInitialReadCanBeSavedAsWithoutPublishingFallbackBacking() throws Exception {
        Path file = tempDirectory.resolve("Unreadable.java");
        Path destination = tempDirectory.resolve("Recovered.java");
        Files.write(file, new byte[]{(byte) 0xc3, 0x28});
        onFxThread(() -> {
            tab = openInTabPane(file);
            TextEditorPane editor = tab.view().activeEditor();
            disableDebouncedAutosave(editor);
            DocumentId id = tab.documentId();
            DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            assertFalse(editor.isInitialContentLoaded());
            assertTrue(store.state(id).isEmpty());
            editor.replaceText("class Recovered { int local; }");
            assertNull(store.state(id).orElseThrow().backing());
            assertEquals(editor.getText(), ((TextDocumentSnapshot) store.current(id).orElseThrow()).text());
            assertTrue(Services.EDITOR_TAB_MANAGER.saveAsActive(destination));
            assertNull(store.state(id).orElseThrow().overlay());
            assertEquals(DocumentUri.fromPath(destination), store.current(id).orElseThrow().uri());
            editor.replaceText("class Recovered { int next; }");
            assertTrue(editor.saveNow());
            assertEquals("class Recovered { int next; }", Files.readString(destination));
            assertNull(store.state(id).orElseThrow().overlay());
            return null;
        });
    }

    @Test
    public void reopeningSavedDocumentPreservesStoreVersion() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String savedText = "class Example { int savedValue; }";
        String reopenedEdit = "class Example { int savedValue; int nextValue; }";
        Files.writeString(file, "class Example {}");

        onFxThread(() -> {
            tab = openInTabPane(file);
            var originalTab = tab;
            var originalEditor = tab.view().activeEditor();
            disableDebouncedAutosave(originalEditor);
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var id = tab.documentId();
            var initialBacking = store.state(id).orElseThrow().backing();
            originalEditor.replaceText(savedText);
            assertTrue(originalEditor.saveNow());

            var savedBacking = assertInstanceOf(TextDocumentSnapshot.class, store.state(id).orElseThrow().backing());
            assertEquals(savedText, savedBacking.text());
            assertTrue(savedBacking.version().isAfter(initialBacking.version()));
            assertNull(store.state(id).orElseThrow().overlay());
            var savedWorkspace = store.capture();
            var originalTabPane = originalTab.tab().getTabPane();

            Services.EDITOR_TAB_MANAGER.close(originalTab);
            tab = null;
            originalEditor.close();
            originalEditor.dispose();

            assertFalse(originalTabPane.getTabs().contains(originalTab.tab()));
            assertSame(project, Services.IDE_STATE.getCurrentProject());
            assertSame(store, Services.IDE_STATE.getDocumentStore().orElseThrow());
            assertSame(savedBacking, store.state(id).orElseThrow().backing());
            assertNull(store.state(id).orElseThrow().overlay());
            assertSame(savedBacking, store.current(id).orElseThrow());
            assertSame(savedBacking, store.capture().document(id).orElseThrow());
            assertEquals(savedWorkspace, store.capture());

            tab = assertDoesNotThrow(() -> openInTabPane(file));
            assertNotNull(tab);
            var reopenedEditor = tab.view().activeEditor();
            disableDebouncedAutosave(reopenedEditor);
            assertNotSame(originalTab, tab);
            assertNotSame(originalEditor, reopenedEditor);
            assertEquals(id, tab.documentId());
            assertEquals(id, reopenedEditor.captureSnapshot().id());
            assertEquals(savedBacking.version(), reopenedEditor.captureSnapshot().version());
            assertEquals(savedText, reopenedEditor.getText());
            assertEquals(EditorSaveState.CLEAN, reopenedEditor.saveState());
            assertSame(savedBacking, store.state(id).orElseThrow().backing());
            assertNull(store.state(id).orElseThrow().overlay());
            assertEquals(savedWorkspace, store.capture());

            assertDoesNotThrow(() -> reopenedEditor.replaceText(reopenedEdit));

            var editedState = store.state(id).orElseThrow();
            var overlay = assertInstanceOf(TextDocumentSnapshot.class, editedState.overlay());
            assertSame(savedBacking, editedState.backing());
            assertEquals(id, overlay.id());
            assertEquals(reopenedEdit, overlay.text());
            assertTrue(overlay.version().isAfter(savedBacking.version()));
            assertEquals(overlay.version(), reopenedEditor.captureSnapshot().version());
            assertEquals(savedBacking.version(), editedState.baseBackingVersion());
            assertFalse(editedState.isStale());
            assertEquals(EditorSaveState.DIRTY, reopenedEditor.saveState());
            assertSame(overlay, store.current(id).orElseThrow());
            assertSame(overlay, store.capture().document(id).orElseThrow());
            assertEquals(savedWorkspace.revision() + 1, store.capture().revision());
            return null;
        });
    }

    @Test
    public void reopeningExternallyModifiedDocumentPublishesNewBacking() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String savedText = "class Example { int savedValue; }";
        String externalText = "class Example { int externalValue; }";
        String reopenedEdit = "class Example { int externalValue; int nextValue; }";
        Files.writeString(file, "class Example {}");

        onFxThread(() -> {
            tab = openInTabPane(file);
            var originalTab = tab;
            var originalEditor = tab.view().activeEditor();
            disableDebouncedAutosave(originalEditor);
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            var id = tab.documentId();
            var initialBacking = store.state(id).orElseThrow().backing();
            originalEditor.replaceText(savedText);
            assertTrue(originalEditor.saveNow());

            var savedBacking = assertInstanceOf(TextDocumentSnapshot.class, store.state(id).orElseThrow().backing());
            assertTrue(savedBacking.version().isAfter(initialBacking.version()));
            assertEquals(savedText, savedBacking.text());
            assertNull(store.state(id).orElseThrow().overlay());
            var savedWorkspace = store.capture();
            var originalTabPane = originalTab.tab().getTabPane();

            Services.EDITOR_TAB_MANAGER.close(originalTab);
            tab = null;
            originalEditor.close();
            originalEditor.dispose();

            assertFalse(originalTabPane.getTabs().contains(originalTab.tab()));
            assertSame(project, Services.IDE_STATE.getCurrentProject());
            assertSame(store, Services.IDE_STATE.getDocumentStore().orElseThrow());
            assertSame(savedBacking, store.state(id).orElseThrow().backing());
            assertNull(store.state(id).orElseThrow().overlay());
            assertEquals(savedWorkspace, store.capture());

            Files.writeString(file, externalText);
            assertSame(savedBacking, store.current(id).orElseThrow());
            assertEquals(savedWorkspace, store.capture());

            tab = assertDoesNotThrow(() -> openInTabPane(file));
            assertNotNull(tab);
            var reopenedEditor = tab.view().activeEditor();
            disableDebouncedAutosave(reopenedEditor);
            var reopenedState = store.state(id).orElseThrow();
            var newBacking = assertInstanceOf(TextDocumentSnapshot.class, reopenedState.backing());
            assertNotSame(originalEditor, reopenedEditor);
            assertEquals(id, tab.documentId());
            assertEquals(id, newBacking.id());
            assertEquals(id, reopenedEditor.captureSnapshot().id());
            assertEquals(DocumentUri.fromPath(file), newBacking.uri());
            assertEquals(externalText, reopenedEditor.getText());
            assertEquals(externalText, newBacking.text());
            assertEquals(savedBacking.version().next(), newBacking.version());
            assertEquals(newBacking.version(), reopenedEditor.captureSnapshot().version());
            assertEquals(EditorSaveState.CLEAN, reopenedEditor.saveState());
            assertNull(reopenedState.overlay());
            assertNull(reopenedState.baseBackingVersion());
            assertFalse(reopenedState.isStale());
            assertSame(newBacking, store.current(id).orElseThrow());
            assertSame(newBacking, store.capture().document(id).orElseThrow());
            assertEquals(savedWorkspace.revision() + 1, store.capture().revision());
            assertSame(savedBacking, savedWorkspace.document(id).orElseThrow());
            assertEquals(savedText, savedBacking.text());

            assertDoesNotThrow(() -> reopenedEditor.replaceText(reopenedEdit));

            var editedState = store.state(id).orElseThrow();
            var overlay = assertInstanceOf(TextDocumentSnapshot.class, editedState.overlay());
            assertSame(newBacking, editedState.backing());
            assertEquals(id, overlay.id());
            assertEquals(reopenedEdit, overlay.text());
            assertTrue(overlay.version().isAfter(newBacking.version()));
            assertEquals(overlay.version(), reopenedEditor.captureSnapshot().version());
            assertEquals(newBacking.version(), editedState.baseBackingVersion());
            assertFalse(editedState.isStale());
            assertEquals(EditorSaveState.DIRTY, reopenedEditor.saveState());
            assertSame(overlay, store.current(id).orElseThrow());
            assertSame(overlay, store.capture().document(id).orElseThrow());
            assertEquals(savedWorkspace.revision() + 2, store.capture().revision());
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
    public void externalModificationOfCleanFileUpdatesBackingWithoutOverlay() throws Exception {
        assertCleanExternalReload(false);
    }

    @Test
    public void externalModificationOfPreviouslySavedFileAdvancesBackingWithoutAutosave() throws Exception {
        assertCleanExternalReload(true);
    }

    @Test
    public void externalModificationOfDirtyFileMarksOverlayStale() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        String unsavedText = "class Example { int unsavedValue; }";
        String externalText = "class Example { int externalValue; }";
        Files.writeString(file, originalText);
        var dialogText = new AtomicReference<String>();
        var store = new DefaultDocumentStore("dirty-external-test",
            new WorkspaceConfigurationSnapshot(0, Set.of()));

        TextEditorPane editor = onFxThread(() -> {
            var pane = new TextEditorPane(DocumentId.create(), file, "java") {
                @Override
                protected void showExternalChangeDialog(String disk) {
                    dialogText.set(disk);
                }
            };
            disableDebouncedAutosave(pane);
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore(store);
            return pane;
        });

        try {
            var before = onFxThread(() -> {
                editor.replaceText(unsavedText);
                var state = store.state(editor.captureSnapshot().id()).orElseThrow();
                var overlay = assertInstanceOf(TextDocumentSnapshot.class, state.overlay());
                assertEquals(unsavedText, overlay.text());
                assertTrue(overlay.version().isAfter(state.backing().version()));
                assertEquals(state.backing().version(), state.baseBackingVersion());
                assertFalse(state.isStale());

                Files.writeString(file, externalText);
                var handleExternalChange = TextEditorPane.class.getDeclaredMethod("handleExternalChange", Path.class);
                handleExternalChange.setAccessible(true);
                handleExternalChange.invoke(editor, file);
                return state;
            });

            // Inspect after the handler's queued FX processing, without waiting for a watcher notification.
            onFxThread(() -> {
                var id = before.overlay().id();
                var state = store.state(id).orElseThrow();
                var backing = assertInstanceOf(TextDocumentSnapshot.class, state.backing());
                var overlay = assertInstanceOf(TextDocumentSnapshot.class, state.overlay());
                assertAll(
                    () -> assertEquals(externalText, backing.text()),
                    () -> assertTrue(backing.version().isAfter(before.backing().version())),
                    () -> assertTrue(backing.version().isAfter(before.overlay().version())),
                    () -> assertSame(before.overlay(), overlay),
                    () -> assertEquals(before.overlay().version(), overlay.version()),
                    () -> assertEquals(unsavedText, overlay.text()),
                    () -> assertEquals(before.baseBackingVersion(), state.baseBackingVersion()),
                    () -> assertTrue(state.isStale()),
                    () -> assertEquals(unsavedText, editor.getText()),
                    () -> assertEquals(EditorSaveState.DIRTY, editor.saveState()),
                    () -> assertSame(overlay, store.current(id).orElseThrow()),
                    () -> assertSame(overlay, store.capture().document(id).orElseThrow()),
                    () -> assertTrue(editor.hasPendingExternalChange()),
                    () -> assertEquals(externalText, dialogText.get()));

                var conflictedWorkspace = store.capture();
                var editorVersion = editor.captureSnapshot().version();
                assertTrue(editorVersion.compareTo(backing.version()) >= 0);
                var handleExternalChange = TextEditorPane.class.getDeclaredMethod("handleExternalChange", Path.class);
                handleExternalChange.setAccessible(true);
                handleExternalChange.invoke(editor, file);
                // Exercise duplicate conflict processing too, even if the watcher coalesces identical notifications.
                var processExternalChange = TextEditorPane.class.getDeclaredMethod("processExternalChange", Path.class);
                processExternalChange.setAccessible(true);
                processExternalChange.invoke(editor, file);
                assertSame(backing, store.state(id).orElseThrow().backing());
                assertSame(overlay, store.state(id).orElseThrow().overlay());
                assertEquals(conflictedWorkspace, store.capture());
                assertEquals(editorVersion, editor.captureSnapshot().version());
                assertTrue(editor.hasPendingExternalChange());

                String furtherEdits = "class Example { int unsavedValue; int moreEdits; }";
                editor.replaceText(furtherEdits);
                var editedState = store.state(id).orElseThrow();
                var editedOverlay = assertInstanceOf(TextDocumentSnapshot.class, editedState.overlay());
                assertSame(backing, editedState.backing());
                assertEquals(furtherEdits, editedOverlay.text());
                assertTrue(editedOverlay.version().isAfter(backing.version()));
                assertEquals(before.baseBackingVersion(), editedState.baseBackingVersion());
                assertTrue(editedState.isStale());
                assertEquals(furtherEdits, editor.getText());
                assertSame(editedOverlay, store.current(id).orElseThrow());
                assertTrue(editor.hasPendingExternalChange());
                return null;
            });
        } finally {
            onFxThread(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
                return null;
            });
        }
    }

    @Test
    public void reloadingExternalChangeDiscardsStaleOverlay() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String unsavedText = "class Example { int unsavedValue; }";
        String externalText = "class Example { int externalValue; }";
        Files.writeString(file, "class Example {}");
        var dialogText = new AtomicReference<String>();
        TextEditorPane editor = onFxThread(() -> {
            var identity = Services.IDE_STATE.identifyDocument(new FileSystemDocument(file, "java"));
            var pane = new TextEditorPane(identity.id(), file, "java") {
                @Override
                protected void showExternalChangeDialog(String disk) {
                    dialogText.set(disk);
                }
            };
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore(store);
            return pane;
        });
        var edited = new CompletableFuture<DocumentModifiedEvent>();
        var reloaded = new CompletableFuture<DocumentModifiedEvent>();
        EventListener<DocumentModifiedEvent> listener = event -> {
            if (file.equals(event.file().getPath())) {
                if (event.changes().stream().anyMatch(change -> change.newContent().contains("unsavedValue"))) {
                    edited.complete(event);
                }
                if (event.changes().stream().anyMatch(change -> change.newContent().contains("externalValue"))) {
                    reloaded.complete(event);
                }
            }
        };
        Railroad.EVENT_BUS.subscribe(DocumentModifiedEvent.class, listener);

        try {
            onFxThread(() -> {
                editor.replaceText(unsavedText);
                Files.writeString(file, externalText);
                var handleExternalChange = TextEditorPane.class.getDeclaredMethod("handleExternalChange", Path.class);
                handleExternalChange.setAccessible(true);
                handleExternalChange.invoke(editor, file);
                return null;
            });
            edited.get(10, TimeUnit.SECONDS);

            var restoredWorkspace = onFxThread(() -> {
                var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                var id = editor.captureSnapshot().id();
                var before = store.state(id).orElseThrow();
                var externalBacking = assertInstanceOf(TextDocumentSnapshot.class, before.backing());
                assertEquals(externalText, externalBacking.text());
                assertEquals(unsavedText, editor.getText());
                assertTrue(before.isStale());
                assertTrue(editor.hasPendingExternalChange());
                assertEquals(externalText, dialogText.get());

                // Distinct backing metadata ensures discard copies the backing rather than the stale overlay.
                var backingUri = DocumentUri.fromPath(tempDirectory.resolve("Backing.java"));
                var backing = new TextDocumentSnapshot(id, backingUri, externalBacking.version(), "java-source",
                    DocumentOrigin.unspecified(backingUri), externalText, StandardCharsets.UTF_16LE);
                store.publishBacking(backing);
                var beforeReload = store.capture();
                var pendingSaveTask = TextEditorPane.class.getDeclaredField("pendingSaveTask");
                pendingSaveTask.setAccessible(true);
                assertNotNull(pendingSaveTask.get(editor));

                var resolveExternalChange = TextEditorPane.class.getDeclaredMethod("resolveExternalChange",
                    boolean.class);
                resolveExternalChange.setAccessible(true);
                resolveExternalChange.invoke(editor, true);

                var state = store.state(id).orElseThrow();
                var restored = assertInstanceOf(TextDocumentSnapshot.class, state.backing());
                assertNull(state.overlay());
                assertNull(state.baseBackingVersion());
                assertFalse(state.isStale());
                assertEquals(externalText, restored.text());
                assertTrue(restored.version().isAfter(backing.version()));
                assertTrue(restored.version().isAfter(before.overlay().version()));
                assertEquals(backing.uri(), restored.uri());
                assertEquals(backing.languageId(), restored.languageId());
                assertEquals(backing.origin(), restored.origin());
                assertEquals(backing.encoding(), restored.encoding());
                assertEquals(externalText, editor.getText());
                assertEquals(restored.version(), editor.captureSnapshot().version());
                assertEquals(EditorSaveState.CLEAN, editor.saveState());
                assertFalse(editor.hasPendingExternalChange());
                assertSame(restored, store.current(id).orElseThrow());
                assertSame(restored, store.capture().document(id).orElseThrow());
                assertEquals(beforeReload.revision() + 1, store.capture().revision());
                assertNull(pendingSaveTask.get(editor));
                var savedVersion = TextEditorPane.class.getDeclaredField("savedVersion");
                savedVersion.setAccessible(true);
                assertEquals(restored.version().value(), ((AtomicLong) savedVersion.get(editor)).get());
                var pendingSnapshot = TextEditorPane.class.getDeclaredField("pendingSnapshot");
                pendingSnapshot.setAccessible(true);
                assertSame(restored, ((AtomicReference<?>) pendingSnapshot.get(editor)).get());
                return store.capture();
            });

            reloaded.get(10, TimeUnit.SECONDS);
            onFxThread(() -> {
                var pendingSaveTask = TextEditorPane.class.getDeclaredField("pendingSaveTask");
                pendingSaveTask.setAccessible(true);
                assertNull(pendingSaveTask.get(editor));
                assertEquals(restoredWorkspace, Services.IDE_STATE.getDocumentStore().orElseThrow().capture());
                assertEquals(externalText, Files.readString(file));
                return null;
            });
        } finally {
            Railroad.EVENT_BUS.unsubscribe(DocumentModifiedEvent.class, listener);
            onFxThread(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
                return null;
            });
        }
    }

    @Test
    public void keepingLocalChangesReconcilesAndSavesStaleOverlay() throws Exception {
        assertKeepingLocalChanges(false);
    }

    @Test
    public void keepingLocalChangesRetainsReconciledOverlayWhenDiskWriteFails() throws Exception {
        assertKeepingLocalChanges(true);
    }

    @Test
    public void secondExternalChangeBeforeResolutionUpdatesBacking() throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String unsavedText = "class Example { int unsavedValue; }";
        String firstExternalText = "class Example { int firstExternalValue; }";
        String latestExternalText = "class Example { int latestExternalValue; }";
        Files.writeString(file, "class Example {}");
        var dialogText = new AtomicReference<String>();
        var saveRequests = new AtomicInteger();
        var reconciliations = new AtomicInteger();
        var reconciledBackingVersion = new AtomicReference<DocumentVersion>();
        var reconciledOverlay = new AtomicReference<TextDocumentSnapshot>();
        TextEditorPane editor = onFxThread(() -> {
            var identity = Services.IDE_STATE.identifyDocument(new FileSystemDocument(file, "java"));
            var pane = new TextEditorPane(identity.id(), file, "java") {
                @Override
                protected void showExternalChangeDialog(String disk) {
                    dialogText.set(disk);
                }

                @Override
                public boolean saveNow() {
                    saveRequests.incrementAndGet();
                    return super.saveNow();
                }
            };
            disableDebouncedAutosave(pane);
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore((DocumentStore) Proxy.newProxyInstance(DocumentStore.class.getClassLoader(),
                new Class<?>[]{DocumentStore.class}, (proxy, method, args) -> {
                    if (method.getName().equals("reconcileOverlay")) {
                        reconciliations.incrementAndGet();
                        reconciledBackingVersion.set((DocumentVersion) args[1]);
                        reconciledOverlay.set((TextDocumentSnapshot) args[3]);
                    }
                    try {
                        return method.invoke(store, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }));
            return pane;
        });

        try {
            onFxThread(() -> {
                editor.replaceText(unsavedText);
                Files.writeString(file, firstExternalText);
                var handleExternalChange = TextEditorPane.class.getDeclaredMethod("handleExternalChange", Path.class);
                handleExternalChange.setAccessible(true);
                handleExternalChange.invoke(editor, file);
                return null;
            });
            onFxThread(() -> {
                var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                var id = editor.captureSnapshot().id();
                var before = store.state(id).orElseThrow();
                assertEquals(firstExternalText, assertInstanceOf(TextDocumentSnapshot.class, before.backing()).text());
                assertEquals(firstExternalText, dialogText.get());
                assertTrue(before.isStale());
                var beforeClick = store.capture();
                var resolveExternalChange = TextEditorPane.class.getDeclaredMethod("resolveExternalChange",
                    boolean.class);
                resolveExternalChange.setAccessible(true);

                // Write and click in the same FX task, before a watcher notification can be processed.
                Files.writeString(file, latestExternalText);
                resolveExternalChange.invoke(editor, false);

                var refreshed = store.state(id).orElseThrow();
                var latestBacking = assertInstanceOf(TextDocumentSnapshot.class, refreshed.backing());
                assertAll(
                    () -> assertEquals(latestExternalText, Files.readString(file)),
                    () -> assertEquals(latestExternalText, dialogText.get()),
                    () -> assertEquals(latestExternalText, latestBacking.text()),
                    () -> assertTrue(latestBacking.version().isAfter(before.backing().version())),
                    () -> assertTrue(latestBacking.version().isAfter(before.overlay().version())),
                    () -> assertSame(before.overlay(), refreshed.overlay()),
                    () -> assertEquals(before.baseBackingVersion(), refreshed.baseBackingVersion()),
                    () -> assertTrue(refreshed.isStale()),
                    () -> assertSame(before.overlay(), store.current(id).orElseThrow()),
                    () -> assertSame(before.overlay(), store.capture().document(id).orElseThrow()),
                    () -> assertEquals(unsavedText, editor.getText()),
                    () -> assertEquals(EditorSaveState.DIRTY, editor.saveState()),
                    () -> assertTrue(editor.hasPendingExternalChange()),
                    () -> assertEquals(0, saveRequests.get()),
                    () -> assertEquals(0, reconciliations.get()),
                    () -> assertEquals(beforeClick.revision() + 1, store.capture().revision()));
                var refreshedWorkspace = store.capture();

                resolveExternalChange.invoke(editor, false);

                var saved = store.state(id).orElseThrow();
                var savedBacking = assertInstanceOf(TextDocumentSnapshot.class, saved.backing());
                assertEquals(1, saveRequests.get());
                assertEquals(1, reconciliations.get());
                assertEquals(latestBacking.version(), reconciledBackingVersion.get());
                assertSame(reconciledOverlay.get(), savedBacking);
                assertTrue(savedBacking.version().isAfter(latestBacking.version()));
                assertEquals(unsavedText, savedBacking.text());
                assertNull(saved.overlay());
                assertNull(saved.baseBackingVersion());
                assertFalse(saved.isStale());
                assertEquals(unsavedText, Files.readString(file));
                assertEquals(unsavedText, editor.getText());
                assertEquals(savedBacking.version(), editor.captureSnapshot().version());
                assertEquals(EditorSaveState.CLEAN, editor.saveState());
                assertFalse(editor.hasPendingExternalChange());
                assertSame(savedBacking, store.current(id).orElseThrow());
                assertSame(savedBacking, store.capture().document(id).orElseThrow());
                assertEquals(refreshedWorkspace.revision() + 2, store.capture().revision());
                return null;
            });
        } finally {
            onFxThread(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
                return null;
            });
        }
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
            assertEquals(initialWorkspaceRevision, store.capture().revision());
            return null;
        });
        assertArrayEquals(invalidUtf8, Files.readAllBytes(file));
    }

    private void assertKeepingLocalChanges(boolean failWrite) throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        Path failedWriteTarget = Files.createDirectory(tempDirectory.resolve("write-target-directory"));
        String unsavedText = "class Example { int unsavedValue; }";
        String externalText = "class Example { int externalValue; }";
        Files.writeString(file, "class Example {}");
        var dialogText = new AtomicReference<String>();
        TextEditorPane editor = onFxThread(() -> {
            var identity = Services.IDE_STATE.identifyDocument(new FileSystemDocument(file, "java"));
            var pane = new TextEditorPane(identity.id(), file, "java") {
                @Override
                protected void showExternalChangeDialog(String disk) {
                    dialogText.set(disk);
                }

                @Override
                public boolean saveNow() {
                    if (!failWrite)
                        return super.saveNow();

                    Path originalPath = filePath;
                    ChangeListener<EditorSaveState> failDiskWrite = (_, _, state) -> {
                        if (state == EditorSaveState.SAVING) {
                            // Fail the actual write after baseline validation, leaving the external file intact.
                            filePath = failedWriteTarget;
                        }
                    };
                    saveStateProperty().addListener(failDiskWrite);
                    try {
                        return super.saveNow();
                    } finally {
                        filePath = originalPath;
                        saveStateProperty().removeListener(failDiskWrite);
                    }
                }
            };
            disableDebouncedAutosave(pane);
            var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
            store.publishBacking(pane.captureSnapshot());
            pane.setDocumentStore(store);
            return pane;
        });

        try {
            onFxThread(() -> {
                editor.replaceText(unsavedText);
                Files.writeString(file, externalText);
                var handleExternalChange = TextEditorPane.class.getDeclaredMethod("handleExternalChange", Path.class);
                handleExternalChange.setAccessible(true);
                handleExternalChange.invoke(editor, file);
                return null;
            });
            onFxThread(() -> {
                var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                var id = editor.captureSnapshot().id();
                var before = store.state(id).orElseThrow();
                assertEquals(externalText, assertInstanceOf(TextDocumentSnapshot.class, before.backing()).text());
                assertTrue(before.isStale());
                assertEquals(unsavedText, editor.getText());
                assertTrue(editor.hasPendingExternalChange());
                assertEquals(externalText, dialogText.get());
                var beforeKeep = store.capture();
                var beforeWrite = new AtomicReference<DocumentState>();
                ChangeListener<EditorSaveState> captureBeforeWrite = (_, _, state) -> {
                    if (state == EditorSaveState.SAVING) {
                        beforeWrite.set(store.state(id).orElseThrow());
                    }
                };
                editor.saveStateProperty().addListener(captureBeforeWrite);
                try {
                    var resolveExternalChange = TextEditorPane.class.getDeclaredMethod("resolveExternalChange",
                        boolean.class);
                    resolveExternalChange.setAccessible(true);
                    resolveExternalChange.invoke(editor, false);
                } finally {
                    editor.saveStateProperty().removeListener(captureBeforeWrite);
                }

                var reconciled = beforeWrite.get();
                assertNotNull(reconciled);
                var reconciledOverlay = assertInstanceOf(TextDocumentSnapshot.class, reconciled.overlay());
                assertSame(before.backing(), reconciled.backing());
                assertEquals(unsavedText, reconciledOverlay.text());
                assertTrue(reconciledOverlay.version().isAfter(before.backing().version()));
                assertTrue(reconciledOverlay.version().isAfter(before.overlay().version()));
                assertEquals(before.backing().version(), reconciled.baseBackingVersion());
                assertFalse(reconciled.isStale());

                var state = store.state(id).orElseThrow();
                assertFalse(state.isStale());
                assertEquals(unsavedText, editor.getText());
                assertEquals(reconciledOverlay.version(), editor.captureSnapshot().version());
                assertFalse(editor.hasPendingExternalChange());
                if (failWrite) {
                    assertEquals(EditorSaveState.ERROR, editor.saveState());
                    assertTrue(editor.dirty());
                    assertSame(before.backing(), state.backing());
                    assertSame(reconciledOverlay, state.overlay());
                    assertEquals(before.backing().version(), state.baseBackingVersion());
                    assertEquals(externalText, Files.readString(file));
                    assertSame(reconciledOverlay, store.current(id).orElseThrow());
                    assertSame(reconciledOverlay, store.capture().document(id).orElseThrow());
                    assertEquals(beforeKeep.revision() + 1, store.capture().revision());
                } else {
                    assertEquals(EditorSaveState.CLEAN, editor.saveState());
                    assertFalse(editor.dirty());
                    assertSame(reconciledOverlay, state.backing());
                    assertNull(state.overlay());
                    assertNull(state.baseBackingVersion());
                    assertEquals(unsavedText, Files.readString(file));
                    assertSame(reconciledOverlay, store.current(id).orElseThrow());
                    assertSame(reconciledOverlay, store.capture().document(id).orElseThrow());
                    assertEquals(beforeKeep.revision() + 2, store.capture().revision());
                }
                return null;
            });
        } finally {
            onFxThread(() -> {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
                return null;
            });
        }
    }

    private void assertCleanExternalReload(boolean saveFirst) throws Exception {
        Path file = tempDirectory.resolve("Example.java");
        String originalText = "class Example {}";
        String updatedText = "class Example { int externalValue; }";
        Files.writeString(file, originalText);
        var modified = new CompletableFuture<DocumentModifiedEvent>();
        var userModified = new CompletableFuture<DocumentModifiedEvent>();
        String userText = "class Example { int userValue; }";
        EventListener<DocumentModifiedEvent> modifiedListener = event -> {
            if (file.equals(event.file().getPath())) {
                if (event.changes().stream().anyMatch(change -> change.newContent().contains("externalValue"))) {
                    modified.complete(event);
                }
                if (event.changes().stream().anyMatch(change -> change.newContent().contains("userValue"))) {
                    userModified.complete(event);
                }
            }
        };
        var becameDirty = new AtomicBoolean();
        Railroad.EVENT_BUS.subscribe(DocumentModifiedEvent.class, modifiedListener);

        try {
            var originalBacking = onFxThread(() -> {
                tab = openInTabPane(file);
                var editor = tab.view().activeEditor();
                if (saveFirst) {
                    editor.replaceText("class Example { int savedValue; }");
                    assertTrue(editor.saveNow());
                }
                editor.saveStateProperty().addListener((_, _, newState) -> {
                    if (newState == EditorSaveState.DIRTY) {
                        becameDirty.set(true);
                    }
                });
                var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                var state = store.state(tab.documentId()).orElseThrow();
                assertEquals(EditorSaveState.CLEAN, editor.saveState());
                assertNull(state.overlay());
                var backing = assertInstanceOf(TextDocumentSnapshot.class, state.backing());
                assertEquals(saveFirst ? "class Example { int savedValue; }" : originalText, backing.text());

                Files.writeString(file, updatedText);
                var handleExternalChange = TextEditorPane.class.getDeclaredMethod("handleExternalChange", Path.class);
                handleExternalChange.setAccessible(true);
                handleExternalChange.invoke(editor, file);
                return backing;
            });

            // The handler queues reload processing before this next FX task, independently of watcher timing.
            var reloadedWorkspace = onFxThread(() -> {
                var editor = tab.view().activeEditor();
                var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                var id = tab.documentId();
                assertEquals(updatedText, editor.getText());
                assertEquals(EditorSaveState.CLEAN, editor.saveState());
                assertFalse(editor.dirty());
                assertFalse(becameDirty.get());
                assertFalse(editor.hasPendingExternalChange());

                var state = store.state(id).orElseThrow();
                var backing = assertInstanceOf(TextDocumentSnapshot.class, state.backing());
                assertAll(
                    () -> assertEquals(updatedText, backing.text()),
                    () -> assertTrue(backing.version().isAfter(originalBacking.version())),
                    () -> assertEquals(backing.version(), editor.captureSnapshot().version()),
                    () -> assertNull(state.overlay()),
                    () -> assertSame(backing, store.current(id).orElseThrow()),
                    () -> assertSame(backing, store.capture().document(id).orElseThrow()));
                return store.capture();
            });

            // Wait for the real debounced callback to notify language features, then inspect its completed work.
            assertFalse(modified.get(10, TimeUnit.SECONDS).changes().isEmpty());
            onFxThread(() -> {
                var pendingSaveTask = TextEditorPane.class.getDeclaredField("pendingSaveTask");
                pendingSaveTask.setAccessible(true);
                assertNull(pendingSaveTask.get(tab.view().activeEditor()));
                assertEquals(reloadedWorkspace, Services.IDE_STATE.getDocumentStore().orElseThrow().capture());
                assertEquals(updatedText, Files.readString(file));

                var editor = tab.view().activeEditor();
                editor.replaceText(userText);
                var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                var state = store.state(tab.documentId()).orElseThrow();
                assertSame(reloadedWorkspace.document(tab.documentId()).orElseThrow(), state.backing());
                assertEquals(userText, assertInstanceOf(TextDocumentSnapshot.class, state.overlay()).text());
                assertEquals(EditorSaveState.DIRTY, editor.saveState());
                return null;
            });
            assertFalse(userModified.get(10, TimeUnit.SECONDS).changes().isEmpty());
            onFxThread(() -> {
                var pendingSaveTask = TextEditorPane.class.getDeclaredField("pendingSaveTask");
                pendingSaveTask.setAccessible(true);
                assertNotNull(pendingSaveTask.get(tab.view().activeEditor()));
                return null;
            });
        } finally {
            Railroad.EVENT_BUS.unsubscribe(DocumentModifiedEvent.class, modifiedListener);
        }
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
