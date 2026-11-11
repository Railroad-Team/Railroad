package dev.railroadide.railroad.ide.ui.codeeditor;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.ui.MarkdownPreviewPane;
import dev.railroadide.railroad.ide.ui.editor.EditorTab;
import dev.railroadide.railroad.ide.ui.editor.EditorTabManager;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.editor.*;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.theme.ThemeManager;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.Tab;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactfx.Subscription;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TextEditorAccessConformanceTest {
    @TempDir
    public Path tempDirectory;
    private Project project;
    private DocumentStore standaloneStore;
    private final List<TextEditorPane> standaloneEditors = new ArrayList<>();

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

    @BeforeEach
    public void openProject() throws Exception {
        onFxThread(() -> {
            LanguageSupports.initialize();
            if (ThemeManager.getTheme() == null) {
                ThemeManager.setTheme("");
            }
            project = new RailroadProject(tempDirectory, "Shared editor conformance", new WritableImage(1, 1));
            Services.IDE_STATE.setCurrentProject(project);
            return null;
        });
        Services.IDE_STATE.getWorkspaceConfiguration().ready().get(10, TimeUnit.SECONDS);
    }

    @AfterEach
    public void closeProject() throws Exception {
        onFxThread(() -> {
            for (TextEditorPane editor : standaloneEditors) {
                editor.discardChangesOnClose();
                editor.close();
                editor.dispose();
            }
            Services.EDITOR_TAB_MANAGER.discardUnsavedChangesOnClose();
            Services.EDITOR_TAB_MANAGER.prepareProjectClose();
            Railroad.EVENT_BUS.publish(new ProjectEvent(project, ProjectEvent.EventType.CLOSED));
            return null;
        });
    }

    @Test
    public void commonConsumerEditsJava() throws Exception {
        assertCommonConsumer("Example.java", null);
    }

    @Test
    public void commonConsumerEditsJson() throws Exception {
        assertCommonConsumer("example.json", null);
    }

    @Test
    public void commonConsumerEditsPlainText() throws Exception {
        assertCommonConsumer("example.txt", null);
    }

    @Test
    public void commonConsumerEditsMarkdownSource() throws Exception {
        assertCommonConsumer("source.md", MarkdownPreviewPane.MarkdownLayoutType.CODE);
    }

    @Test
    public void commonConsumerEditsMarkdownSplit() throws Exception {
        assertCommonConsumer("split.md", MarkdownPreviewPane.MarkdownLayoutType.SPLIT);
    }

    @Test
    public void commonConsumerRevealsAndEditsPreviewFirstMarkdown() throws Exception {
        assertCommonConsumer("preview.md", MarkdownPreviewPane.MarkdownLayoutType.PREVIEW);
    }

    @Test
    public void commonConsumerRetargetsManagedTabsAndReopenedEditorsWithoutQueuedOldNotifications() throws Exception {
        Path firstFile = tempDirectory.resolve("first.txt");
        Path secondFile = tempDirectory.resolve("second.txt");
        Path imageFile = tempDirectory.resolve("image.png");
        Files.writeString(firstFile, "first first");
        Files.writeString(secondFile, "second second");
        assertTrue(ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", imageFile.toFile()));
        var consumerReference = new AtomicReference<FindReplaceConsumer>();
        try {
            List<EditorTab> opened = onFxThread(() -> {
                var tabs = new DetachableTabPane();
                EditorTab first = openInTabPane(firstFile, tabs);
                disableDebouncedAutosave(first.view().activeEditor());
                var consumer = new FindReplaceConsumer(Services.DOCUMENT_EDITOR_STATE);
                consumerReference.set(consumer);
                assertEquals(2, consumer.find("first").size());
                assertEquals(EditResult.APPLIED, consumer.replaceAll("draft"));

                EditorTab second = openInTabPane(secondFile, tabs);
                disableDebouncedAutosave(second.view().activeEditor());
                assertSame(second.view().textEditor().orElseThrow(),
                    Services.DOCUMENT_EDITOR_STATE.getActiveTextEditor().orElseThrow());
                assertTrue(consumer.find("first").isEmpty());
                assertEquals(2, consumer.find("second").size());
                assertEquals(EditResult.APPLIED, consumer.select(1));
                assertEquals(new TextSelection(7, 13), second.view().textEditor().orElseThrow().selection());

                EditorTab image = openInTabPane(imageFile, tabs);
                assertTrue(image.view().textEditor().isEmpty());
                assertTrue(Services.DOCUMENT_EDITOR_STATE.getActiveTextEditor().isEmpty());
                assertTrue(consumer.find("second").isEmpty());

                tabs.getSelectionModel().select(first.tab());
                return List.of(first, second);
            });
            onFxThread(() -> {
                EditorTab first = opened.getFirst();
                EditorTab second = opened.getLast();
                var tabs = (DetachableTabPane) first.tab().getTabPane();
                TextEditorAccess oldAccess = first.view().textEditor().orElseThrow();
                TextDocumentSnapshot original = oldAccess.snapshot();
                FindReplaceConsumer consumer = consumerReference.get();
                assertEquals(0, consumer.notifications(), "Pending observations of previous targets must be cancelled");
                assertSame(oldAccess, Services.DOCUMENT_EDITOR_STATE.getActiveTextEditor().orElseThrow());
                assertEquals(2, consumer.find("draft").size());
                first.view().activeEditor().discardChangesOnClose();
                tabs.getTabs().remove(first.tab());
                Event.fireEvent(first.tab(), new Event(first.tab(), first.tab(), Tab.CLOSED_EVENT));
                assertEquals(EditResult.CLOSED, oldAccess.replace(original, List.of(edit(0, 5, "obsolete"))));
                assertTrue(first.view().textEditor().isEmpty());

                EditorTab reopened = openInTabPane(firstFile, tabs);
                disableDebouncedAutosave(reopened.view().activeEditor());
                TextEditorAccess newAccess = reopened.view().textEditor().orElseThrow();
                assertNotSame(oldAccess, newAccess);
                assertEquals(original.id(), newAccess.snapshot().id());
                assertEquals("first first", newAccess.snapshot().text());
                assertEquals(2, consumer.find("first").size());
                assertEquals(EditResult.APPLIED, consumer.select(1));
                assertEquals(EditResult.APPLIED, consumer.replaceAll("reopened"));
                assertEquals("reopened reopened", newAccess.snapshot().text());
                assertEquals("second second", second.view().textEditor().orElseThrow().snapshot().text());
                assertEquals(0, consumer.notifications(), "Source changes are delivered only after this turn settles");
                return null;
            });
            onFxThread(() -> {
                assertEquals(1, consumerReference.get().notifications(),
                    "Queued callbacks from previous targets and the closed editor must be cancelled");
                return null;
            });
        } finally {
            onFxThread(() -> {
                if (consumerReference.get() != null) {
                    consumerReference.get().close();
                }
                return null;
            });
        }
    }

    private void assertCommonConsumer(String name, MarkdownPreviewPane.MarkdownLayoutType layout) throws Exception {
        String diskText = "find \uD83D\uDE80\r\nfind end";
        String original = "find \uD83D\uDE80\nfind end";
        Path file = tempDirectory.resolve(name);
        Files.writeString(file, diskText);
        var consumerReference = new AtomicReference<FindReplaceConsumer>();
        var editorReference = new AtomicReference<TextEditorPane>();
        try {
            onFxThread(() -> {
                if (layout != null) {
                    project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(layout));
                }
                EditorTab tab = openInTabPane(file);
                TextEditorAccess access = tab.view().textEditor().orElseThrow();
                assertSame(access, Services.DOCUMENT_EDITOR_STATE.getActiveTextEditor().orElseThrow());
                TextDocumentSnapshot previewSnapshot = null;
                if (layout == MarkdownPreviewPane.MarkdownLayoutType.PREVIEW) {
                    assertNull(tab.view().activeEditor(), "Capability discovery must preserve lazy source creation");
                    previewSnapshot = access.snapshot();
                    assertEquals(diskText, previewSnapshot.text());
                    assertEquals(9, previewSnapshot.lineMap().offsetAt(new TextPosition(1, 0)));
                }
                var consumer = new FindReplaceConsumer(Services.DOCUMENT_EDITOR_STATE);
                consumerReference.set(consumer);
                List<TextRange> matches = consumer.find("find");
                TextEditorPane editor = tab.view().activeEditor();
                editorReference.set(editor);
                disableDebouncedAutosave(editor);
                editor.getUndoManager().forgetHistory();
                assertEquals(original, access.snapshot().text());
                assertSame(store().current(tab.documentId()).orElseThrow(), access.snapshot());
                if (previewSnapshot != null) {
                    assertTrue(access.snapshot().version().isAfter(previewSnapshot.version()));
                    assertEquals(EditResult.STALE,
                        access.selectAndReveal(previewSnapshot, new TextSelection(9, 13)));
                    assertEquals(EditResult.STALE, access.replace(previewSnapshot, List.of(edit(9, 13, "stale"))));
                }
                assertEquals(2, matches.size());
                assertEquals(original.indexOf("find", 1), matches.get(1).start);
                assertEquals(EditResult.APPLIED, consumer.select(1));
                assertEquals(new TextSelection(matches.get(1).start, matches.get(1).end), access.selection());
                assertEquals(EditResult.APPLIED, access.selectAndReveal(access.snapshot(), new TextSelection(7, 5)));
                assertEquals(new TextSelection(7, 5), access.selection());
                assertEquals(1, access.snapshot().lineMap().positionAt(matches.get(1).start).line());
                assertEquals(EditResult.APPLIED, consumer.replaceAll("replacement"));
                assertEquals("replacement \uD83D\uDE80\nreplacement end", access.snapshot().text());
                editor.undo();
                assertEquals(original, access.snapshot().text());
                editor.redo();
                assertEquals("replacement \uD83D\uDE80\nreplacement end", access.snapshot().text());
                editor.setEditable(false);
                assertFalse(access.isEditable());
                consumer.find("replacement");
                assertEquals(EditResult.READ_ONLY, consumer.replaceAll("blocked"));
                assertEquals(EditResult.APPLIED, consumer.select(0));
                editor.setEditable(true);
                return null;
            });
            onFxThread(() -> {
                assertTrue(consumerReference.get().notifications() > 0);
                assertEquals(new TextSelection(0, "replacement".length()),
                    editorReference.get().textEditorAccess().selection(),
                    "Lazy source initialization must not reset a newly selected match on a later pulse");
                consumerReference.get().close();
                return null;
            });
            int notifications = consumerReference.get().notifications();
            onFxThread(() -> {
                editorReference.get().appendText("!");
                return null;
            });
            onFxThread(() -> {
                assertEquals(notifications, consumerReference.get().notifications());
                return null;
            });
        } finally {
            onFxThread(() -> {
                if (consumerReference.get() != null) {
                    consumerReference.get().close();
                }
                return null;
            });
        }
    }

    @Test
    public void rejectsEveryInvalidBatchBeforeMutating() throws Exception {
        TextEditorPane editor = createStandalone("abcd efgh");
        onFxThread(() -> {
            TextEditorAccess access = editor.textEditorAccess();
            TextDocumentSnapshot before = access.snapshot();
            long revision = store().capture().revision();
            editor.getUndoManager().forgetHistory();
            assertEquals(EditResult.INVALID, access.replace(before, List.of(
                edit(0, 1, "valid"), edit(8, 20, "invalid"))));
            assertEquals(EditResult.INVALID, access.replace(before, List.of(edit(0, 4, "a"), edit(3, 5, "b"))));
            assertEquals(EditResult.INVALID, access.replace(before, List.of(edit(2, 2, "a"), edit(2, 2, "b"))));
            assertEquals(EditResult.INVALID,
                access.selectAndReveal(before, new TextSelection(0, before.text().length() + 1)));
            assertSame(before, access.snapshot());
            assertEquals(revision, store().capture().revision());
            assertFalse(editor.isUndoAvailable());
            assertEquals("abcd efgh", editor.getText());
            return null;
        });
    }

    @Test
    public void noOpReplacementPreservesSnapshotAndUndoHistory() throws Exception {
        TextEditorPane editor = createStandalone("original");
        onFxThread(() -> {
            TextEditorAccess access = editor.textEditorAccess();
            TextDocumentSnapshot before = access.snapshot();
            editor.getUndoManager().forgetHistory();
            assertEquals(EditResult.APPLIED, access.replace(before, List.of()));
            assertEquals(EditResult.APPLIED, access.replace(before, List.of(edit(0, 8, "original"))));
            assertSame(before, access.snapshot());
            assertFalse(editor.isUndoAvailable());
            assertFalse(editor.dirty());
            return null;
        });
    }

    @Test
    public void selectionObservationPreservesDirectionAndStopsAfterDisposal() throws Exception {
        TextEditorPane editor = createStandalone("a\uD83D\uDE80\nb");
        var selections = new ArrayList<TextSelection>();
        EditorSubscription subscription = onFxThread(() -> editor.textEditorAccess().observeSelection(selections::add));
        onFxThread(() -> {
            assertEquals(List.of(new TextSelection(0, 0)), selections);
            selections.clear();
            TextEditorAccess access = editor.textEditorAccess();
            assertEquals(EditResult.APPLIED, access.selectAndReveal(access.snapshot(), new TextSelection(1, 3)));
            assertEquals(EditResult.APPLIED, access.selectAndReveal(access.snapshot(), new TextSelection(5, 1)));
            return null;
        });
        onFxThread(() -> {
            assertEquals(List.of(new TextSelection(5, 1)), selections);
            editor.selectRange(0, 0);
            subscription.close();
            return null;
        });
        onFxThread(() -> {
            assertEquals(List.of(new TextSelection(5, 1)), selections);
            subscription.close();
            return null;
        });
    }

    @Test
    public void rejectsStaleIdentityRevisionTextAndMetadata() throws Exception {
        TextEditorPane editor = createStandalone("abcd");
        onFxThread(() -> {
            TextEditorAccess access = editor.textEditorAccess();
            TextDocumentSnapshot before = access.snapshot();
            List<TextDocumentSnapshot> wrongSnapshots = List.of(
                new TextDocumentSnapshot(DocumentId.create(), before.uri(), before.version(), before.languageId(),
                    before.origin(), before.text(), before.encoding()),
                new TextDocumentSnapshot(before.id(), before.uri(), before.version().next(), before.languageId(),
                    before.origin(), before.text(), before.encoding()),
                new TextDocumentSnapshot(before.id(), before.uri(), before.version(), before.languageId(),
                    before.origin(), "different", before.encoding()),
                new TextDocumentSnapshot(before.id(), DocumentUri.fromPath(tempDirectory.resolve("other.txt")),
                    before.version(), before.languageId(), before.origin(), before.text(), before.encoding()),
                new TextDocumentSnapshot(before.id(), before.uri(), before.version(), "other-language",
                    before.origin(), before.text(), before.encoding()),
                new TextDocumentSnapshot(before.id(), before.uri(), before.version(), before.languageId(),
                    DocumentOrigin.unspecified(before.uri()), before.text(), before.encoding()),
                new TextDocumentSnapshot(before.id(), before.uri(), before.version(), before.languageId(),
                    before.origin(), before.text(), StandardCharsets.UTF_16));
            for (TextDocumentSnapshot wrong : wrongSnapshots) {
                assertEquals(EditResult.STALE, access.replace(wrong, List.of(edit(0, 1, "x"))));
                assertEquals(EditResult.STALE, access.selectAndReveal(wrong, new TextSelection(0, 1)));
                assertSame(before, access.snapshot());
            }
            var equivalent = new TextDocumentSnapshot(before.id(), before.uri(), before.version(),
                before.languageId(), before.origin(), before.text(), before.encoding());
            assertEquals(EditResult.APPLIED, access.replace(equivalent, List.of(edit(0, 1, "x"))));
            assertEquals(EditResult.STALE, access.replace(before, List.of(edit(0, 1, "y"))));
            assertEquals("xbcd", editor.getText());
            return null;
        });
    }

    @Test
    public void observesOnlySettledCurrentSnapshotsAndDisposesQueuedCallbacks() throws Exception {
        TextEditorPane editor = createStandalone("original");
        var received = new ArrayList<TextDocumentSnapshot>();
        var subscription = new AtomicReference<EditorSubscription>();
        onFxThread(() -> {
            subscription.set(editor.textEditorAccess().observe(snapshot -> {
                assertTrue(Platform.isFxApplicationThread());
                assertSame(store().current(snapshot.id()).orElseThrow(), snapshot);
                assertEquals(editor.getText(), snapshot.text());
                received.add(snapshot);
            }));
            return null;
        });
        onFxThread(() -> {
            received.clear();
            editor.replaceText("first");
            editor.replaceText("final");
            return null;
        });
        onFxThread(() -> {
            assertEquals(1, received.size());
            assertEquals("final", received.getFirst().text());
            var unrelated = new TextDocumentSnapshot(DocumentId.create(),
                DocumentUri.fromPath(tempDirectory.resolve("unrelated.txt")), new DocumentVersion(0), "plaintext",
                "unrelated", StandardCharsets.UTF_8);
            store().publishBacking(unrelated);
            assertTrue(editor.saveNow());
            return null;
        });
        onFxThread(() -> {
            assertEquals(1, received.size(), "Unrelated changes and same-revision save must be deduplicated");
            editor.appendText("cancelled callback");
            subscription.get().close();
            return null;
        });
        onFxThread(() -> {
            assertEquals(1, received.size());
            subscription.get().close();
            return null;
        });
    }

    @Test
    public void observesExternalReloadAfterItsRevisionSettles() throws Exception {
        TextEditorPane editor = createStandalone("original");
        TextDocumentSnapshot original = onFxThread(() -> editor.textEditorAccess().snapshot());
        var received = new ArrayList<TextDocumentSnapshot>();
        EditorSubscription subscription = onFxThread(() -> editor.textEditorAccess().observe(received::add));
        try {
            onFxThread(() -> {
                received.clear();
                Files.writeString(editor.getFilePath(), "external\r\n\uD83D\uDE80");
                invokeExternalChange(editor);
                return null;
            });
            onFxThread(() -> null);
            onFxThread(() -> {
                assertEquals(1, received.size());
                assertEquals("external\n\uD83D\uDE80", received.getFirst().text());
                assertSame(editor.textEditorAccess().snapshot(), received.getFirst());
                assertFalse(editor.dirty());
                assertEquals(EditResult.STALE,
                    editor.textEditorAccess().replace(original, List.of(edit(0, 1, "obsolete"))));
                return null;
            });
        } finally {
            onFxThread(() -> {
                subscription.close();
                return null;
            });
        }
    }

    @Test
    public void replacesCanonicalDirtyOverlayWithoutResolvingExternalConflict() throws Exception {
        TextEditorPane editor = createStandalone("disk");
        TextDocumentSnapshot overlay = onFxThread(() -> {
            editor.replaceText("local local");
            TextDocumentSnapshot snapshot = editor.textEditorAccess().snapshot();
            Files.writeString(editor.getFilePath(), "external");
            invokeExternalChange(editor);
            return snapshot;
        });
        onFxThread(() -> {
            TextEditorAccess access = editor.textEditorAccess();
            DocumentState before = store().state(overlay.id()).orElseThrow();
            assertSame(overlay, access.snapshot());
            assertTrue(before.backing().version().isAfter(overlay.version()));
            assertTrue(editor.captureSnapshot().version().isAfter(overlay.version()));
            assertTrue(before.isStale());
            assertEquals(EditResult.APPLIED,
                access.replace(overlay, List.of(edit(0, 5, "changed"), edit(6, 11, "draft"))));
            assertEquals("changed draft", access.snapshot().text());
            DocumentState after = store().state(overlay.id()).orElseThrow();
            assertSame(before.backing(), after.backing());
            assertEquals(before.baseBackingVersion(), after.baseBackingVersion());
            assertTrue(after.isStale());
            assertTrue(editor.hasPendingExternalChange());
            assertFalse(editor.saveNow());
            assertEquals("external", Files.readString(editor.getFilePath()));
            editor.undo();
            assertEquals("local local", access.snapshot().text());
            editor.redo();
            assertEquals("changed draft", access.snapshot().text());
            return null;
        });
    }

    @Test
    public void acceptedCloseRejectsEditsAndRemovesDocumentSubscriptions() throws Exception {
        TextEditorPane editor = createStandalone("original");
        var calls = new AtomicInteger();
        var callsAtClose = new AtomicInteger();
        onFxThread(() -> {
            TextEditorAccess access = editor.textEditorAccess();
            TextDocumentSnapshot before = access.snapshot();
            int baseline = storeListenerCount();
            EditorSubscription registration = access.observe(_ -> calls.incrementAndGet());
            access.observeSelection(_ -> calls.incrementAndGet());
            editor.appendText("pending");
            editor.discardChangesOnClose();
            editor.close();
            assertTrue(editor.isEditorClosed());
            assertEquals(EditResult.CLOSED, access.replace(before, List.of(edit(0, 1, "x"))));
            assertEquals(EditResult.CLOSED, access.selectAndReveal(before, new TextSelection(0, 1)));
            assertEquals(baseline, storeListenerCount());
            registration.close();
            callsAtClose.set(calls.get());
            return null;
        });
        onFxThread(() -> {
            assertEquals(callsAtClose.get(), calls.get());
            return null;
        });
    }

    @Test
    public void failedCloseKeepsCapabilityAndObservationUsable() throws Exception {
        TextEditorPane editor = createStandalone("disk");
        var observedText = new AtomicReference<String>();
        EditorSubscription subscription = onFxThread(() -> editor.textEditorAccess().observe(
            snapshot -> observedText.set(snapshot.text())));
        try {
            onFxThread(() -> {
                editor.replaceText("local");
                Files.writeString(editor.getFilePath(), "external");
                invokeExternalChange(editor);
                return null;
            });
            onFxThread(() -> {
                editor.close();
                assertFalse(editor.isEditorClosed());
                TextEditorAccess access = editor.textEditorAccess();
                assertEquals(EditResult.APPLIED, access.replace(access.snapshot(), List.of(edit(0, 5, "retained"))));
                return null;
            });
            onFxThread(() -> {
                assertEquals("retained", observedText.get());
                assertEquals("retained", editor.textEditorAccess().snapshot().text());
                return null;
            });
        } finally {
            onFxThread(() -> {
                subscription.close();
                return null;
            });
        }
    }

    @Test
    public void checkedReplacementWaitsForSaveAndRetainsItsNewerOverlay() throws Exception {
        TextEditorPane editor = createStandalone("original");
        var saveInProgress = new CountDownLatch(1);
        var continueSave = new CountDownLatch(1);
        var applyStarted = new CountDownLatch(1);
        var pause = new AtomicBoolean(true);
        try {
            TextDocumentSnapshot expected = onFxThread(() -> {
                editor.replaceText("saved");
                TextDocumentSnapshot snapshot = editor.textEditorAccess().snapshot();
                DocumentStore actualStore = store();
                editor.setDocumentStore((DocumentStore) Proxy.newProxyInstance(DocumentStore.class.getClassLoader(),
                    new Class<?>[]{DocumentStore.class}, (proxy, method, args) -> {
                        if (method.getName().equals("state") && !Platform.isFxApplicationThread()
                            && pause.compareAndSet(true, false)) {
                            saveInProgress.countDown();
                            assertTrue(continueSave.await(10, TimeUnit.SECONDS));
                        }
                        try {
                            return method.invoke(actualStore, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }));
                var schedule = TextEditorPane.class.getDeclaredMethod("scheduleSave", TextDocumentSnapshot.class);
                schedule.setAccessible(true);
                schedule.invoke(editor, snapshot);
                return snapshot;
            });
            assertTrue(saveInProgress.await(10, TimeUnit.SECONDS));
            var apply = new FutureTask<>(() -> {
                applyStarted.countDown();
                return editor.textEditorAccess().replace(expected, List.of(edit(0, 5, "new overlay")));
            });
            Platform.runLater(apply);
            assertTrue(applyStarted.await(10, TimeUnit.SECONDS));
            assertFalse(apply.isDone());
            continueSave.countDown();
            assertEquals(EditResult.APPLIED, apply.get(10, TimeUnit.SECONDS));
            onFxThread(() -> {
                DocumentState state = store().state(expected.id()).orElseThrow();
                assertEquals("saved", Files.readString(editor.getFilePath()));
                assertEquals("saved", ((TextDocumentSnapshot) state.backing()).text());
                assertEquals("new overlay", ((TextDocumentSnapshot) state.overlay()).text());
                assertTrue(state.overlay().version().isAfter(state.backing().version()));
                assertFalse(state.isStale());
                assertEquals("new overlay", editor.textEditorAccess().snapshot().text());
                return null;
            });
        } finally {
            continueSave.countDown();
        }
    }

    @Test
    public void rechecksClosureAfterWaitingForTheOwnershipLock() throws Exception {
        TextEditorPane editor = createStandalone("original");
        TextDocumentSnapshot expected = onFxThread(() -> editor.textEditorAccess().snapshot());
        var field = TextEditorPane.class.getDeclaredField("saveLock");
        field.setAccessible(true);
        Object saveLock = field.get(editor);
        var lockHeld = new CountDownLatch(1);
        var closeNow = new CountDownLatch(1);
        var applyStarted = new CountDownLatch(1);
        var close = new FutureTask<Void>(() -> {
            synchronized (saveLock) {
                lockHeld.countDown();
                assertTrue(closeNow.await(10, TimeUnit.SECONDS));
                editor.closeRetainingDraft();
            }
            return null;
        });
        Thread.ofPlatform().daemon().start(close);
        try {
            assertTrue(lockHeld.await(10, TimeUnit.SECONDS));
            var apply = new FutureTask<>(() -> {
                applyStarted.countDown();
                return editor.textEditorAccess().replace(expected, List.of(edit(0, 1, "x")));
            });
            Platform.runLater(apply);
            assertTrue(applyStarted.await(10, TimeUnit.SECONDS));
            assertFalse(apply.isDone());
            closeNow.countDown();
            close.get(10, TimeUnit.SECONDS);
            assertEquals(EditResult.CLOSED, apply.get(10, TimeUnit.SECONDS));
            onFxThread(() -> {
                assertEquals("original", editor.getText());
                return null;
            });
        } finally {
            closeNow.countDown();
        }
    }

    @Test
    public void rejectsControlAccessFromWorkerThreads() throws Exception {
        TextEditorPane editor = createStandalone("original");
        TextEditorAccess access = onFxThread(editor::textEditorAccess);
        TextDocumentSnapshot before = onFxThread(access::snapshot);
        assertThrows(IllegalStateException.class, access::snapshot);
        assertThrows(IllegalStateException.class, access::selection);
        assertThrows(IllegalStateException.class, () -> access.replace(before, List.of(edit(0, 1, "x"))));
        onFxThread(() -> {
            assertSame(before, access.snapshot());
            return null;
        });
    }

    private TextEditorPane createStandalone(String text) throws Exception {
        Path file = tempDirectory.resolve("standalone.txt");
        Files.writeString(file, text);
        return onFxThread(() -> {
            standaloneStore = new DefaultDocumentStore("standalone-conformance",
                new WorkspaceConfigurationSnapshot(0, Set.of()));
            DocumentId id = Services.IDE_STATE.identifyDocument(new FileSystemDocument(file, "plaintext")).id();
            var editor = new TextEditorPane(id, file, "plaintext") {
                @Override
                protected void showExternalChangeDialog(String disk) {
                }
            };
            standaloneEditors.add(editor);
            disableDebouncedAutosave(editor);
            store().publishBacking(editor.captureSnapshot());
            editor.setDocumentStore(store());
            return editor;
        });
    }

    private EditorTab openInTabPane(Path file) throws ReflectiveOperationException {
        return openInTabPane(file, new DetachableTabPane());
    }

    private EditorTab openInTabPane(Path file, DetachableTabPane tabs) throws ReflectiveOperationException {
        Class<?> requestType = Class.forName(EditorTabManager.class.getName() + "$TabOpenRequest");
        var normalRequest = requestType.getDeclaredMethod("normal");
        normalRequest.setAccessible(true);
        var open = EditorTabManager.class.getDeclaredMethod("openInTabPane", Project.class, Path.class,
            DetachableTabPane.class, requestType);
        open.setAccessible(true);
        return (EditorTab) open.invoke(Services.EDITOR_TAB_MANAGER, project, file, tabs,
            normalRequest.invoke(null));
    }

    private static void invokeExternalChange(TextEditorPane editor) throws ReflectiveOperationException {
        var method = TextEditorPane.class.getDeclaredMethod("handleExternalChange", Path.class);
        method.setAccessible(true);
        method.invoke(editor, editor.getFilePath());
    }

    private static void disableDebouncedAutosave(TextEditorPane editor) throws ReflectiveOperationException {
        var field = TextEditorPane.class.getDeclaredField("changeSubscription");
        field.setAccessible(true);
        ((Subscription) field.get(editor)).unsubscribe();
    }

    private int storeListenerCount() throws ReflectiveOperationException {
        var field = DefaultDocumentStore.class.getDeclaredField("changeListeners");
        field.setAccessible(true);
        return ((List<?>) field.get(store())).size();
    }

    private DocumentStore store() {
        return standaloneStore == null ? Services.IDE_STATE.getDocumentStore().orElseThrow() : standaloneStore;
    }

    private static TextReplacement edit(int start, int end, String text) {
        return new TextReplacement(new TextRange(start, end), text);
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
