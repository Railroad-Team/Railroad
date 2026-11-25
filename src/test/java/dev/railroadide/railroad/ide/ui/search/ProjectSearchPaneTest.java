package dev.railroadide.railroad.ide.ui.search;

import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.ide.edit.WorkspaceEditService;
import dev.railroadide.railroad.ide.edit.WorkspaceEditorHost;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.search.ProjectTextSearch.FileMatches;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.localization.Languages;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorRequestTestSupport.onFx;
import static dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorRequestTestSupport.startFx;
import static org.junit.jupiter.api.Assertions.*;

public class ProjectSearchPaneTest {
    @TempDir
    public Path root;
    private DefaultDocumentStore store;
    private DocumentIdentityRegistry identities;
    private WorkspaceEditService edits;
    private final BackgroundActivities activities = new BackgroundActivities();
    private ProjectSearchPane pane;

    @BeforeAll
    public static void initializeFx() throws Exception {
        startFx();
        onFx(() -> {
            L18n.loadLanguage(Languages.EN_US);
            LanguageSupports.initialize();
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @BeforeEach
    public void createPane() throws Exception {
        store = new DefaultDocumentStore("project-search", new WorkspaceConfigurationSnapshot(0, Set.of()));
        identities = new DocumentIdentityRegistry();
        edits = new WorkspaceEditService(root, store, identities, new NoEditors());
        pane = onFx(() -> new ProjectSearchPane(root, () -> Optional.of(store), () -> Optional.of(edits),
            activities));
    }

    @AfterEach
    public void closePane() throws Exception {
        onFx(() -> {
            pane.close();
            edits.close();
            return null;
        });
    }

    @Test
    public void groupsMatchesByFileAndPreviewsTheReplacement() throws Exception {
        disk("src/main/resources/data/mymod/recipe/gear.json", "{\"result\": \"mymod:copper_gear\"}");
        disk("src/main/java/mymod/Items.java", "// mymod:copper_gear\nregister(\"mymod:copper_gear\");");

        search("mymod:copper_gear");
        onFx(() -> {
            assertEquals(ProjectSearchPane.State.COMPLETE, pane.state());
            assertEquals(List.of("src/main/java/mymod/Items.java", "src/main/resources/data/mymod/recipe/gear.json"),
                pane.results().stream().map(FileMatches::relativePath).toList());
            assertEquals("3 matches in 2 files", pane.statusText());

            new Scene(pane, 900, 400);
            pane.applyCss();
            pane.layout();
            assertEquals("2", ((Label) pane.lookup(".usages-file-count")).getText());
            assertEquals("1:4", ((Label) pane.lookup(".usages-position")).getText());

            pane.open(null, true);
            pane.replaceField().setText("mymod:bronze_gear");
            FileMatches java = pane.results().getFirst();
            assertEquals(Optional.of("mymod:bronze_gear"),
                pane.previewReplacement(java.document().id(), java.matches().getFirst()));
            pane.applyCss();
            pane.layout();
            Label replacement = (Label) pane.lookup(".project-search-replacement");
            assertTrue(replacement.isVisible());
            assertEquals("mymod:bronze_gear", replacement.getText());
            return null;
        });
    }

    @Test
    public void replaceAllAsksForConfirmationAndWritesOnlyIncludedMatches() throws Exception {
        disk("a.json", "mymod:copper_gear mymod:copper_gear");
        disk("b.json", "mymod:copper_gear");

        search("mymod:copper_gear");
        Optional<WorkspaceEditResult> result = onFx(() -> {
            pane.open(null, true);
            pane.replaceField().setText("mymod:bronze_gear");
            FileMatches first = pane.results().getFirst();
            assertTrue(pane.exclude(first.document().id(), first.matches().get(1)));
            assertEquals("2 matches in 2 files", pane.statusText());

            pane.requestReplaceAll();
            assertTrue(pane.isConfirmationVisible());
            assertEquals(new ProjectSearchPane.Message(ProjectSearchPane.Severity.QUESTION,
                "Replace 2 matches in 2 files?", "Files that aren't open in an editor are saved straight away."),
                pane.message().orElseThrow());
            return pane.replaceAll();
        });

        assertTrue(result.orElseThrow().applied(), () -> result.orElseThrow().problems().toString());
        assertEquals("mymod:bronze_gear mymod:copper_gear", Files.readString(root.resolve("a.json")));
        assertEquals("mymod:bronze_gear", Files.readString(root.resolve("b.json")));
        waitUntil(() -> pane.state() == ProjectSearchPane.State.COMPLETE);
        onFx(() -> {
            assertFalse(pane.isConfirmationVisible());
            assertEquals(List.of("a.json"), pane.results().stream().map(FileMatches::relativePath).toList());
            assertEquals("1 match in 1 file", pane.statusText());
            assertEquals(new ProjectSearchPane.Message(ProjectSearchPane.Severity.SUCCESS,
                "Replaced 2 matches in 2 files", ""), pane.message().orElseThrow());
            return null;
        });
    }

    @Test
    public void searchesAreShownAsStatusBarActivitiesUntilTheyFinish() throws Exception {
        disk("a.txt", "needle");
        disk("b.txt", "needle");
        List<String> seen = new ArrayList<>();
        activities.observe(() -> activities.running().forEach(activity -> {
            synchronized (seen) {
                seen.add(activity.titleKey() + " " + activity.completed() + "/" + activity.total());
            }
        }));

        search("needle");

        synchronized (seen) {
            assertTrue(seen.contains("railroad.activity.searching_files 2/2"), seen::toString);
        }

        assertTrue(activities.running().isEmpty());
    }

    @Test
    public void excludingTheLastMatchOfAFileRemovesTheFile() throws Exception {
        disk("a.txt", "needle");
        disk("b.txt", "needle");

        search("needle");
        onFx(() -> {
            FileMatches first = pane.results().getFirst();
            assertTrue(pane.exclude(first.document().id(), first.matches().getFirst()));
            assertEquals(List.of("b.txt"), pane.results().stream().map(FileMatches::relativePath).toList());
            assertEquals("1 match in 1 file", pane.statusText());
            return null;
        });
    }

    @Test
    public void changedFilesMarkTheResultsOutdatedAndRefuseTheReplacement() throws Exception {
        TextDocumentSnapshot document = disk("a.txt", "needle");

        search("needle");
        onFx(() -> {
            store.publishBacking(new TextDocumentSnapshot(document.id(), document.uri(), document.version().next(),
                "plaintext", DocumentOrigin.original(document.uri()), "moved needle", StandardCharsets.UTF_8),
                store.state(document.id()).orElseThrow());
            return null;
        });

        waitUntil(() -> pane.state() == ProjectSearchPane.State.OUTDATED);
        onFx(() -> {
            assertEquals(new ProjectSearchPane.Message(ProjectSearchPane.Severity.WARNING,
                "Results may be out of date", "Files changed since this search."), pane.message().orElseThrow());
            return null;
        });

        Optional<WorkspaceEditResult> result = onFx(() -> {
            pane.open(null, true);
            pane.replaceField().setText("pin");
            return pane.replaceAll();
        });

        assertFalse(result.orElseThrow().applied());
        assertEquals("needle", Files.readString(root.resolve("a.txt")));
        onFx(() -> {
            assertEquals(new ProjectSearchPane.Message(ProjectSearchPane.Severity.ERROR, "Nothing was replaced",
                "a.txt changed after the search. Search again, then replace."), pane.message().orElseThrow());
            return null;
        });
    }

    @Test
    public void invalidRegexAndReplacementAreReportedWithoutSearchingOrReplacing() throws Exception {
        disk("a.txt", "abc");

        onFx(() -> {
            pane.regexToggle().setSelected(true);
            pane.searchField().setText("(");
            pane.search();
            assertEquals(ProjectSearchPane.State.INVALID, pane.state());
            ProjectSearchPane.Message message = pane.message().orElseThrow();
            assertEquals(ProjectSearchPane.Severity.ERROR, message.severity());
            assertEquals("Invalid regular expression", message.title());
            assertTrue(message.detail().startsWith("Unclosed group"), message.detail());
            assertEquals("", pane.statusText());
            pane.searchField().setText("(b)");
            pane.search();
            return null;
        });

        waitUntil(() -> pane.state() == ProjectSearchPane.State.COMPLETE);
        onFx(() -> {
            pane.open(null, true);
            pane.replaceField().setText("$2");
            assertEquals(new ProjectSearchPane.Message(ProjectSearchPane.Severity.ERROR, "Invalid replacement",
                "$2 refers to a group the search doesn't have. The search has 1 group, so use $1."),
                pane.message().orElseThrow());
            pane.requestReplaceAll();
            assertFalse(pane.isConfirmationVisible());
            return null;
        });

        assertEquals("abc", Files.readString(root.resolve("a.txt")));
    }

    @Test
    public void openingAMatchedFileKeepsResultsCurrentAndReplaceable() throws Exception {
        TextDocumentSnapshot document = disk("a.txt", "needle");

        search("needle");
        onFx(() -> {
            store.publishOverlay(new TextDocumentSnapshot(document.id(), document.uri(), document.version().next(),
                "plaintext", DocumentOrigin.original(document.uri()), "needle", StandardCharsets.UTF_8));
            return null;
        });

        onFx(() -> null);
        Optional<WorkspaceEditResult> result = onFx(() -> {
            assertEquals(ProjectSearchPane.State.COMPLETE, pane.state());
            assertTrue(pane.message().isEmpty(), () -> pane.message().toString());
            pane.open(null, true);
            pane.replaceField().setText("pin");
            return pane.replaceAll();
        });

        assertTrue(result.orElseThrow().applied(), result.orElseThrow().problems()::toString);
        assertEquals("pin", ((TextDocumentSnapshot) store.current(document.id()).orElseThrow()).text());
    }

    @Test
    public void lineEndingNormalizationAfterTheSearchKeepsMatchesReplaceable() throws Exception {
        TextDocumentSnapshot document = disk("a.txt", "first\r\nneedle\r\n");

        search("needle");
        onFx(() -> {
            store.publishOverlay(new TextDocumentSnapshot(document.id(), document.uri(), document.version().next(),
                "plaintext", DocumentOrigin.original(document.uri()), "first\nneedle\n", StandardCharsets.UTF_8));
            return null;
        });

        onFx(() -> null);
        Optional<WorkspaceEditResult> result = onFx(() -> {
            assertEquals(ProjectSearchPane.State.COMPLETE, pane.state());
            pane.open(null, true);
            pane.replaceField().setText("pin");
            return pane.replaceAll();
        });

        assertTrue(result.orElseThrow().applied(), result.orElseThrow().problems()::toString);
        assertEquals("first\npin\n", ((TextDocumentSnapshot) store.current(document.id()).orElseThrow()).text());
    }

    @Test
    public void escapeCancelsTheConfirmation() throws Exception {
        disk("a.txt", "needle");

        search("needle");
        onFx(() -> {
            pane.open(null, true);
            pane.requestReplaceAll();
            assertEquals(new ProjectSearchPane.Message(ProjectSearchPane.Severity.QUESTION,
                "Delete 1 match in 1 file?", "Files that aren't open in an editor are saved straight away."),
                pane.message().orElseThrow());
            pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
            assertFalse(pane.isConfirmationVisible());
            assertTrue(pane.message().isEmpty());
            return null;
        });

        assertEquals("needle", Files.readString(root.resolve("a.txt")));
    }

    @Test
    public void fileMaskAndSeedNarrowTheSearch() throws Exception {
        disk("src/A.java", "copper");
        disk("src/a.json", "copper");

        onFx(() -> {
            pane.maskField().setText("*.json");
            pane.open("copper", false);
            return null;
        });

        waitUntil(() -> pane.state() == ProjectSearchPane.State.COMPLETE);
        onFx(() -> {
            assertEquals("copper", pane.searchField().getText());
            assertFalse(pane.isReplaceVisible());
            assertEquals(List.of("src/a.json"), pane.results().stream().map(FileMatches::relativePath).toList());
            return null;
        });
    }

    private void search(String text) throws Exception {
        onFx(() -> {
            pane.searchField().setText(text);
            pane.search();
            return null;
        });

        waitUntil(() -> pane.state() == ProjectSearchPane.State.COMPLETE);
    }

    private TextDocumentSnapshot disk(String name, String text) throws Exception {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        DocumentId id = identities.getOrCreate(path);
        var uri = DocumentUri.fromPath(path);
        var snapshot = new TextDocumentSnapshot(id, uri, DocumentVersion.initial(), "plaintext",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
        onFx(() -> {
            store.publishBacking(snapshot);
            return null;
        });

        return snapshot;
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!onFx(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) {
                fail("Condition was not reached in time");
            }

            Thread.sleep(10);
        }
    }

    private static final class NoEditors implements WorkspaceEditorHost {
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
            throw new UnsupportedOperationException("Project search never moves files");
        }
    }
}
