package dev.railroadide.railroad.ide.search;

import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.ide.search.ProjectTextSearch.FileMatches;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceChange;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.ui.codeeditor.find.FindQuery;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectTextSearchTest {
    private static final Path ROOT = Path.of("project").toAbsolutePath().normalize();

    private final DefaultDocumentStore store = new DefaultDocumentStore("search",
        new WorkspaceConfigurationSnapshot(0, Set.of()));

    @Test
    public void findsTheSameIdAcrossSourcesAndResourcesInPathOrder() {
        publish("src/main/resources/data/mymod/recipe/gear.json", "{\"result\": \"mymod:copper_gear\"}");
        String java = "register(\"copper_gear\");\n// mymod:copper_gear";
        publish("src/main/java/mymod/Items.java", java);
        publish("src/main/resources/assets/mymod/lang/en_us.json", "{\"item.mymod.copper_gear\": \"Gear\"}");
        publish("src/main/resources/assets/mymod/models/item/gear.json", "{\"parent\": \"mymod:copper_gear\"}");

        List<FileMatches> streamed = new ArrayList<>();
        ProjectTextSearch.Result result = search(query("mymod:copper_gear"), FileMask.all(), streamed::add);

        assertEquals(List.of("src/main/java/mymod/Items.java",
            "src/main/resources/assets/mymod/models/item/gear.json",
            "src/main/resources/data/mymod/recipe/gear.json"),
            result.files().stream().map(FileMatches::relativePath).toList());
        assertEquals(result.files(), streamed);
        assertEquals(4, result.searchedFiles());
        assertEquals(3, result.matchCount());
        assertFalse(result.limitReached());
        int start = java.indexOf("mymod:copper_gear");
        assertEquals(List.of(new TextRange(start, start + 17)), result.files().getFirst().matches());
    }

    @Test
    public void searchesUnsavedEditorContentInsteadOfTheBackingFile() {
        TextDocumentSnapshot backing = publish("src/Example.java", "old text");
        var overlay = new TextDocumentSnapshot(backing.id(), backing.uri(), backing.version().next(), "java",
            DocumentOrigin.original(backing.uri()), "new text", StandardCharsets.UTF_8);
        store.publishOverlay(overlay);

        assertTrue(search(query("old"), FileMask.all()).files().isEmpty());
        assertEquals(1, search(query("new"), FileMask.all()).matchCount());
    }

    @Test
    public void skipsDocumentsOutsideTheProjectAndBinaryDocuments() {
        publish("src/Inside.txt", "needle");
        Path outside = ROOT.resolveSibling("other").resolve("Outside.txt");
        var uri = DocumentUri.fromPath(outside);
        store.publishBacking(new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "plaintext",
            DocumentOrigin.original(uri), "needle", StandardCharsets.UTF_8));
        var image = DocumentUri.fromPath(ROOT.resolve("icon.png"));
        store.publishBacking(new BinaryDocumentSnapshot(DocumentId.create(), image, DocumentVersion.initial(), "image",
            "needle".getBytes(StandardCharsets.UTF_8)));

        assertEquals(List.of("src/Inside.txt"), paths(search(query("needle"), FileMask.all())));
    }

    @Test
    public void fileMaskIncludesAndExcludesByNameOrPath() {
        publish("src/main/java/A.java", "gear");
        publish("src/main/resources/data/recipe.json", "gear");
        publish("src/main/resources/assets/lang/en_us.json", "gear");
        publish("README.md", "gear");

        assertEquals(List.of("src/main/resources/assets/lang/en_us.json", "src/main/resources/data/recipe.json"),
            paths(search(query("gear"), new FileMask("*.JSON"))));
        assertEquals(List.of("src/main/resources/data/recipe.json"),
            paths(search(query("gear"), new FileMask("src/main/resources, !**/lang/**"))));
        assertEquals(List.of("README.md", "src/main/java/A.java"),
            paths(search(query("gear"), new FileMask("!*.json"))));
        assertEquals(List.of("src/main/java/A.java"),
            paths(search(query("gear"), new FileMask("src/**/?.java"))));
    }

    @Test
    public void reportsSearchedFilesAsActivityProgress() {
        publish("a.txt", "needle");
        publish("b.txt", "hay");
        publish("c.txt", "needle");
        var activities = new BackgroundActivities();
        List<BackgroundActivities.Activity> reported = new ArrayList<>();
        activities.observe(() -> reported.addAll(activities.running()));

        try (BackgroundActivities.Handle activity = activities.start("railroad.activity.searching_files")) {
            ProjectTextSearch.search(store.capture(), ROOT, query("needle"), FileMask.all(), 10, () -> false,
                activity, _ -> {
                });
        }

        BackgroundActivities.Activity last = reported.getLast();
        assertEquals("railroad.activity.files", last.phase());
        assertEquals(3, last.completed());
        assertEquals(3, last.total());
        assertTrue(activities.running().isEmpty());
    }

    @Test
    public void stopsAtTheMatchLimitAndReportsIt() {
        publish("a.txt", "x x x");
        publish("b.txt", "x x");
        publish("c.txt", "x");

        ProjectTextSearch.Result result = ProjectTextSearch.search(store.capture(), ROOT, query("x"), FileMask.all(),
            4, () -> false, ActivityProgress.NONE, _ -> {
            });

        assertTrue(result.limitReached());
        assertEquals(4, result.matchCount());
        assertEquals(List.of("a.txt", "b.txt"), paths(result));
        assertEquals(1, result.files().get(1).matches().size());
    }

    @Test
    public void cancellationStopsTheSearch() {
        publish("a.txt", "x");

        assertThrows(CancellationException.class, () -> ProjectTextSearch.search(store.capture(), ROOT, query("x"),
            FileMask.all(), 10, () -> true, ActivityProgress.NONE,
            _ -> fail("Cancelled searches must not report files")));
    }

    @Test
    public void cancellationIsObservedWhileADocumentIsBeingMatched() {
        publish("large.txt", "a".repeat(1_000_000));
        int[] checks = new int[1];

        assertThrows(CancellationException.class, () -> ProjectTextSearch.search(store.capture(), ROOT,
            query("b"), FileMask.all(), 10, () -> ++checks[0] > 1, ActivityProgress.NONE, _ -> {
            }));

        assertEquals(2, checks[0], "The second check must happen inside the document");
    }

    @Test
    public void invalidRegexFailsBeforeSearching() {
        publish("a.txt", "x");

        assertThrows(PatternSyntaxException.class, () -> search(new FindQuery("(", false, false, true),
            FileMask.all()));
    }

    @Test
    public void replacementEditReplacesOnlyListedMatchesAndExpandsGroups() {
        TextDocumentSnapshot first = publish("a.json", "mymod:copper_gear mymod:tin_gear mymod:iron_gear");
        TextDocumentSnapshot second = publish("b.json", "mymod:gold_gear");
        var regex = new FindQuery("mymod:(\\w+)_gear", true, false, true);
        ProjectTextSearch.Result result = search(regex, FileMask.all());
        var withoutTin = new FileMatches(first, "a.json", List.of(result.files().getFirst().matches().get(0),
            result.files().getFirst().matches().get(2)));

        WorkspaceEdit edit = ProjectTextSearch.replacementEdit("Replace", List.of(withoutTin,
            result.files().get(1)), regex, "mymod:gear_$1");

        assertEquals(2, edit.changes().size());
        WorkspaceChange.TextEdit firstEdit = (WorkspaceChange.TextEdit) edit.changes().getFirst();
        assertSame(first, firstEdit.expected());
        assertEquals(List.of(new TextReplacement(new TextRange(0, 17), "mymod:gear_copper"),
            new TextReplacement(new TextRange(33, 48), "mymod:gear_iron")), firstEdit.replacements());
        WorkspaceChange.TextEdit secondEdit = (WorkspaceChange.TextEdit) edit.changes().get(1);
        assertSame(second, secondEdit.expected());
        assertEquals(List.of(new TextReplacement(new TextRange(0, 15), "mymod:gear_gold")),
            secondEdit.replacements());
        assertEquals(Map.of(new TextRange(0, 15), "mymod:gear_gold"),
            ProjectTextSearch.replacements(result.files().get(1), regex, "mymod:gear_$1"));
    }

    @Test
    public void literalReplacementDoesNotExpandGroupReferences() {
        publish("a.txt", "price");
        FindQuery literal = query("price");
        ProjectTextSearch.Result result = search(literal, FileMask.all());

        assertEquals(Map.of(new TextRange(0, 5), "$1 \\"),
            ProjectTextSearch.replacements(result.files().getFirst(), literal, "$1 \\"));
    }

    @Test
    public void invalidReplacementTemplateIsRejected() {
        publish("a.txt", "abc");
        var regex = new FindQuery("(b)", true, false, true);
        ProjectTextSearch.Result result = search(regex, FileMask.all());

        assertThrows(IndexOutOfBoundsException.class,
            () -> ProjectTextSearch.replacementEdit("Replace", result.files(), regex, "$2"));
    }

    @Test
    public void rebaseMovesMatchesOntoANewRevisionWithTheSameText() {
        TextDocumentSnapshot searched = publish("a.txt", "x needle");
        WorkspaceSnapshot before = store.capture();
        FileMatches matches = search(query("needle"), FileMask.all()).files().getFirst();
        TextDocumentSnapshot opened = republish(searched, "x needle");

        FileMatches rebased = ProjectTextSearch.rebase(matches, store.capture()).orElseThrow();

        assertSame(opened, rebased.document());
        assertEquals(matches.matches(), rebased.matches());
        assertTrue(ProjectTextSearch.sameText(before, store.capture()));
    }

    @Test
    public void rebaseShiftsMatchesWhenCrlfBecameLf() {
        TextDocumentSnapshot searched = publish("a.txt", "one\r\ntwo needle\r\nneedle");
        WorkspaceSnapshot before = store.capture();
        FileMatches matches = search(query("needle"), FileMask.all()).files().getFirst();
        TextDocumentSnapshot normalized = republish(searched, "one\ntwo needle\nneedle");

        FileMatches rebased = ProjectTextSearch.rebase(matches, store.capture()).orElseThrow();

        assertSame(normalized, rebased.document());
        assertEquals(List.of(new TextRange(8, 14), new TextRange(15, 21)), rebased.matches());
        assertEquals(List.of("needle", "needle"), rebased.matches().stream()
            .map(range -> normalized.text().substring(range.start, range.end)).toList());
        assertTrue(ProjectTextSearch.sameText(before, store.capture()));
    }

    @Test
    public void changedTextIsNeitherRebasedNorTheSame() {
        TextDocumentSnapshot searched = publish("a.txt", "needle");
        WorkspaceSnapshot before = store.capture();
        FileMatches matches = search(query("needle"), FileMask.all()).files().getFirst();
        republish(searched, "moved needle");

        assertTrue(ProjectTextSearch.rebase(matches, store.capture()).isEmpty());
        assertFalse(ProjectTextSearch.sameText(before, store.capture()));
    }

    @Test
    public void aNewFileMakesTheWorkspaceDifferent() {
        publish("a.txt", "needle");
        WorkspaceSnapshot before = store.capture();
        publish("b.txt", "needle");

        assertFalse(ProjectTextSearch.sameText(before, store.capture()));
    }

    private ProjectTextSearch.Result search(FindQuery query, FileMask mask) {
        return search(query, mask, _ -> {
        });
    }

    private ProjectTextSearch.Result search(FindQuery query, FileMask mask, Consumer<FileMatches> sink) {
        return ProjectTextSearch.search(store.capture(), ROOT, query, mask, 1_000, () -> false, ActivityProgress.NONE,
            sink);
    }

    private static FindQuery query(String text) {
        return new FindQuery(text, false, false, false);
    }

    private static List<String> paths(ProjectTextSearch.Result result) {
        return result.files().stream().map(FileMatches::relativePath).toList();
    }

    private TextDocumentSnapshot republish(TextDocumentSnapshot previous, String text) {
        var snapshot = new TextDocumentSnapshot(previous.id(), previous.uri(), previous.version().next(), "plaintext",
            DocumentOrigin.original(previous.uri()), text, StandardCharsets.UTF_8);
        store.publishOverlay(snapshot);
        return snapshot;
    }

    private TextDocumentSnapshot publish(String relativePath, String text) {
        var uri = DocumentUri.fromPath(ROOT.resolve(relativePath));
        var snapshot = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "plaintext",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
        store.publishBacking(snapshot);
        return snapshot;
    }
}
