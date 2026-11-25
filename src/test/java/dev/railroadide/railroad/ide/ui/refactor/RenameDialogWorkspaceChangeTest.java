package dev.railroadide.railroad.ide.ui.refactor;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.edit.WorkspaceEditService;
import dev.railroadide.railroad.ide.edit.WorkspaceEditorHost;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult;
import dev.railroadide.railroad.ide.sst.impl.java.CompiledJava;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSstServices;
import dev.railroadide.railroad.ide.sst.provider.api.RenamePreparation;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.localization.L18n;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Changes the workspace, on disk and in unsaved documents, between preparing, previewing and applying a rename, and
 * checks that the dialog only ever applies a rename computed for the prepared symbol against the current workspace.
 */
public class RenameDialogWorkspaceChangeTest {
    private static final String CALC = """
        package p;
        public class Calc {
            static String calc(long value) { return "long"; }
        }
        """;
    private static final String USE = """
        package p;
        public class Use {
            public static String call() { return Calc.calc(5); }
        }
        """;
    private static final String MAIN = """
        package p;
        public class Main {
            public static String run() { return %s; }
        }
        """;
    private static JavaSstServices services;

    @TempDir
    public Path root;
    @TempDir
    public Path scratch;
    private final Map<String, TextDocumentSnapshot> documents = new LinkedHashMap<>();
    private DefaultDocumentStore store;
    private WorkspaceEditService edits;
    private RenameDialog dialog;

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
        onFx(() -> {
            LanguageSupports.initialize();
            return null;
        });

        services = new JavaSstServices();
    }

    @AfterAll
    public static void closeServices() {
        services.close();
    }

    @BeforeEach
    public void createWorkspace() throws IOException {
        var configuration = new WorkspaceConfigurationSnapshot(0, Set.of("main"),
            Map.of("module.main.sources", root.toString()));
        store = new DefaultDocumentStore("rename-dialog", configuration);
        var identities = new DocumentIdentityRegistry();
        edits = new WorkspaceEditService(root, store, identities, new WorkspaceEditorHost() {
            @Override
            public Optional<TextEditorPane> openEditor(DocumentId id) {
                return Optional.empty();
            }

            @Override
            public boolean isOpen(DocumentId id) {
                return false;
            }

            @Override
            public void relocate(DocumentIdentity identity, Path newPath) {
                store.relocate(identity.id(), DocumentUri.fromPath(newPath));
            }
        });

        save("p/Calc.java", CALC);
        save("p/Use.java", USE);
        save("p/Main.java", MAIN.formatted("Use.call()"));
        assertEquals("long", CompiledJava.run(scratch, texts(), "p.Main"));
    }

    @AfterEach
    public void closeDialog() throws Exception {
        onFx(() -> {
            if (dialog != null) {
                dialog.close();
            }

            edits.close();
            return null;
        });
    }

    @Test
    public void recomputesThePreviewWhenAFileChangesOnDiskBeforeApplying() throws Exception {
        openDialog("compute");
        assertEquals(RenameDialog.State.PREVIEWED, onFx(dialog::preview).get(60, TimeUnit.SECONDS));
        assertTrue(onFx(dialog::previewLines).stream().noneMatch(line -> line.contains("Calc.compute(1L)")));

        save("p/Other.java", """
            package p;
            public class Other { public static String twice() { return Calc.calc(1L) + Calc.calc(2L); } }
            """);
        save("p/Main.java", MAIN.formatted("Use.call() + Other.twice()"));

        assertEquals(Optional.empty(), onFx(dialog::apply), "A stale preview must not be applied");
        assertEquals(CALC, Files.readString(root.resolve("p/Calc.java")));
        awaitPreview();
        assertEquals(L18n.localize("railroad.rename.status.refreshed", 4, 3), onFx(dialog::statusText));
        assertTrue(onFx(dialog::previewLines).stream().anyMatch(line -> line.contains("Calc.compute(1L)")),
            () -> String.valueOf(quietly(dialog::previewLines)));

        WorkspaceEditResult result = onFx(dialog::apply).orElseThrow();
        assertTrue(result.applied(), result.problems()::toString);
        assertTrue(Files.readString(root.resolve("p/Other.java")).contains("Calc.compute(1L) + Calc.compute(2L)"));
        assertEquals("longlonglong", CompiledJava.run(scratch, disk(), "p.Main"));
    }

    @Test
    public void recomputesThePreviewWhenAnUnsavedDocumentChangesBeforeApplying() throws Exception {
        openDialog("compute");
        assertEquals(RenameDialog.State.PREVIEWED, onFx(dialog::preview).get(60, TimeUnit.SECONDS));

        String unsaved = MAIN.formatted("Use.call() + Calc.calc(7L)");
        overlay("p/Main.java", unsaved);

        assertEquals(Optional.empty(), onFx(dialog::apply));
        awaitPreview();
        WorkspaceEditResult result = onFx(dialog::apply).orElseThrow();
        assertTrue(result.applied(), result.problems()::toString);

        TextDocumentSnapshot draft = (TextDocumentSnapshot) store.state(documents.get("p/Main.java").id()).orElseThrow()
            .overlay();
        assertNotNull(draft, "The unsaved document must stay unsaved");
        assertTrue(draft.text().contains("Calc.compute(7L)"), draft.text());
        assertEquals(MAIN.formatted("Use.call()"), Files.readString(root.resolve("p/Main.java")));
        Map<String, String> program = disk();
        program.put("p/Main.java", draft.text());
        assertEquals("longlong", CompiledJava.run(scratch, program, "p.Main"));
    }

    @Test
    public void appliesThePreviewWhenADocumentIsOnlySavedUnchanged() throws Exception {
        openDialog("compute");
        assertEquals(RenameDialog.State.PREVIEWED, onFx(dialog::preview).get(60, TimeUnit.SECONDS));
        save("p/Main.java", MAIN.formatted("Use.call()"));

        WorkspaceEditResult result = onFx(dialog::apply).orElseThrow();
        assertTrue(result.applied(), result.problems()::toString);
        assertEquals("long", CompiledJava.run(scratch, disk(), "p.Main"));
    }

    @Test
    public void blocksThePreviewWhenAChangeMakesTheCaretNameAnotherOverload() throws Exception {
        openDialog("compute");
        save("p/Calc.java", """
            package p;
            public class Calc {
                static String calc(long value) { return "long"; }
                static String calc(int value) { return "int"; }
            }
            """);

        assertEquals(RenameDialog.State.BLOCKED, onFx(dialog::preview).get(60, TimeUnit.SECONDS));
        assertTrue(onFx(dialog::messages).stream().anyMatch(message -> message.contains("different symbol")),
            () -> String.valueOf(quietly(dialog::messages)));
        assertEquals(USE, Files.readString(root.resolve("p/Use.java")));
    }

    private void openDialog(String newName) throws Exception {
        TextDocumentSnapshot use = documents.get("p/Use.java");
        int offset = use.text().indexOf("calc(5)");
        var context = new FeatureRequestContext(store.capture(), use, CancellationToken.none(), Deadline.none());
        RenamePreparation.Renameable symbol = assertInstanceOf(RenamePreparation.Renameable.class,
            services.prepareRename(context, offset).value());
        dialog = onFx(() -> {
            var created = new RenameDialog(new RenameDialog.Request(services, CancellationToken.none(), store, use,
                offset, symbol, () -> Optional.of(edits)));
            created.setNewName(newName);
            return created;
        });
    }

    private void awaitPreview() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (onFx(dialog::state) == RenameDialog.State.COMPUTING) {
            assertTrue(System.nanoTime() < deadline, "Timed out waiting for the preview");
            Thread.sleep(20);
        }

        assertEquals(RenameDialog.State.PREVIEWED, onFx(dialog::state),
            () -> String.valueOf(quietly(dialog::messages)));
    }

    /**
     * Writes a file and publishes it as a saved revision, as a change made outside the editor does.
     */
    private void save(String file, String text) throws IOException {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        TextDocumentSnapshot saved = documents.get(file);
        var uri = DocumentUri.fromPath(path);
        TextDocumentSnapshot document = saved == null
            ? new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
                DocumentOrigin.original(uri), text, StandardCharsets.UTF_8)
            : new TextDocumentSnapshot(saved.id(), saved.uri(), saved.version().next(), "java", saved.origin(), text,
                StandardCharsets.UTF_8);
        documents.put(file, document);
        store.publishBacking(document);
    }

    private void overlay(String file, String text) {
        TextDocumentSnapshot saved = documents.get(file);
        var document = new TextDocumentSnapshot(saved.id(), saved.uri(), saved.version().next(), "java",
            saved.origin(), text, StandardCharsets.UTF_8);
        documents.put(file, document);
        store.publishOverlay(document);
    }

    private Map<String, String> texts() {
        Map<String, String> texts = new LinkedHashMap<>();
        documents.forEach((name, document) -> texts.put(name, document.text()));
        return texts;
    }

    private Map<String, String> disk() throws IOException {
        Map<String, String> texts = new LinkedHashMap<>();
        for (String file : documents.keySet()) {
            texts.put(file, Files.readString(root.resolve(file)));
        }

        return texts;
    }

    private static <T> T quietly(Callable<T> action) {
        try {
            return onFx(action);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread())
            return action.call();

        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }
}
