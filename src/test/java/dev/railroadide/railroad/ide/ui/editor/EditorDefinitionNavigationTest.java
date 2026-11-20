package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.Commands;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.ui.IDEDockItem;
import dev.railroadide.railroad.ide.ui.IDEPane;
import dev.railroadide.railroad.ide.ui.MarkdownPreviewPane;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorPane;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorConfig;
import dev.railroadide.railroad.ide.sst.provider.api.NavigationProvider;
import dev.railroadide.railroad.ide.sst.provider.api.UsageQuery;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSearch;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSink;
import dev.railroadide.railroad.ide.sst.semantic.api.DefinitionTarget;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.ui.navigation.UsagesPane;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.theme.ThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.List;
import javafx.scene.Group;

import static org.junit.jupiter.api.Assertions.*;

public class EditorDefinitionNavigationTest {
    @TempDir
    public Path directory;
    @TempDir
    public Path external;
    private RailroadProject project;
    private IDEPane pane;
    private NavigationEditor sourceEditor;

    @BeforeAll
    public static void startFx() throws Exception {
        WorkspaceModes.initialize();
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        var hostServices = Railroad.class.getDeclaredField("hostServices");
        hostServices.setAccessible(true);
        if (hostServices.get(null) == null) {
            hostServices.set(null, new Railroad().getHostServices());
        }
    }

    @BeforeEach
    public void openWorkspace() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            LanguageSupports.initialize();
            if (ThemeManager.getTheme() == null) {
                ThemeManager.setTheme("");
            }
            project = new RailroadProject(directory, "Definition navigation", new WritableImage(1, 1));
            Services.IDE_STATE.setCurrentProject(project);
            return null;
        });
        Services.IDE_STATE.getWorkspaceConfiguration().ready().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            pane = new IDEPane(project);
            new Scene(pane, 800, 600);
            return null;
        });
    }

    @AfterEach
    public void closeWorkspace() throws Exception {
        onFx(() -> {
            try {
                if (sourceEditor != null) {
                    sourceEditor.discardChangesOnClose();
                    sourceEditor.close();
                    sourceEditor.dispose();
                }
                if (pane != null) {
                    pane.getScene().setRoot(new Group());
                    pane.close();
                }
            } finally {
                Railroad.EVENT_BUS.publish(new ProjectEvent(project, ProjectEvent.EventType.CLOSED));
            }
            return null;
        });
    }

    @Test
    public void opensUnopenedDestinationAndSelectsItsDefinition() throws Exception {
        onFx(() -> {
            var expected = publish("destination.txt", "prefix definition suffix");
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, expected,
                Location.TextLocation.from(expected, 7, 17)));
            var tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(expected.id(), tab.documentId());
            assertEquals(new TextSelection(7, 17), tab.view().textEditor().orElseThrow().selection());
            return null;
        });
    }

    @Test
    public void opensUnopenedCrlfDestinationAndSelectsItsDefinition() throws Exception {
        onFx(() -> {
            String text = "class Destination {\r\n    void first() {}\r\n    void definition() {}\r\n}\r\n";
            var expected = publish("Destination.java", text);
            int start = text.indexOf("definition");
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, expected,
                Location.TextLocation.from(expected, start, start + "definition".length())));
            var tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(expected.id(), tab.documentId());
            var editor = tab.view().textEditor().orElseThrow();
            String shown = editor.snapshot().text();
            int shownStart = shown.indexOf("definition");
            assertEquals(new TextSelection(shownStart, shownStart + "definition".length()), editor.selection());
            return null;
        });
    }

    @Test
    public void crlfDestinationWhoseFileChangedIsNotSelected() throws Exception {
        onFx(() -> {
            var expected = publish("Destination.java", "class Destination {\r\n    void definition() {}\r\n}\r\n");
            Files.writeString(expected.uri().filePath().orElseThrow(),
                "class Destination {\r\n    void renamed() {}\r\n}\r\n");
            assertFalse(Services.EDITOR_TAB_MANAGER.navigateTo(null, expected,
                Location.TextLocation.from(expected, 29, 39)));
            var editor = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow().view().textEditor().orElseThrow();
            assertEquals(new TextSelection(0, 0), editor.selection());
            return null;
        });
    }

    @Test
    public void definitionOutsideTheWorkspaceOpensItsFileReadOnly() throws Exception {
        String text = "prefix definition suffix";
        Path file = external.resolve("Library.txt");
        Files.writeString(file, text);
        assertTrue(file.toFile().setWritable(false));
        var uri = DocumentUri.fromPath(file);
        var view = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "text",
            new DocumentOrigin(BackingKind.FILE, ProvenanceKind.ATTACHED_SOURCE), text, StandardCharsets.UTF_8);
        onFx(() -> {
            assertTrue(Services.IDE_STATE.getDocumentStore().orElseThrow().capture().document(view.id()).isEmpty());
            openSource((context, offset) -> new FeatureResult<>(context, List.of(target(view, 7, 17))));
            sourceEditor.goToDefinition();
            return null;
        });
        sourceEditor.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            var tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(file.toAbsolutePath().normalize(), tab.path());
            var editor = tab.view().textEditor().orElseThrow();
            assertEquals(new TextSelection(7, 17), editor.selection());
            assertFalse(editor.isEditable());
            assertFalse(tab.view().activeEditor().isEditable());
            return null;
        });
    }

    @Test
    public void reusesExistingDestinationTabInsteadOfNavigatingThePreviouslyActiveTab() throws Exception {
        onFx(() -> {
            var destination = publish("destination.txt", "prefix definition suffix");
            Services.EDITOR_TAB_MANAGER.open(destination.uri().filePath().orElseThrow());
            var existing = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            var other = publish("source.txt", "source text");
            Services.EDITOR_TAB_MANAGER.open(other.uri().filePath().orElseThrow());
            var source = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            source.view().activeEditor().moveTo(2);
            int count = existing.tab().getTabPane().getTabs().size();
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, destination,
                Location.TextLocation.from(destination, 7, 17)));
            assertSame(existing, Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow());
            assertEquals(count, existing.tab().getTabPane().getTabs().size());
            assertEquals(2, source.view().activeEditor().getCaretPosition());
            assertEquals(new TextSelection(7, 17), existing.view().textEditor().orElseThrow().selection());
            return null;
        });
    }

    @TestFactory
    public Stream<DynamicTest> rejectsStaleOrInvalidDestinationWithoutChangingSelection() {
        return Stream.of("version", "text", "identity", "locationVersion", "bounds", "coordinates")
            .map(value -> DynamicTest.dynamicTest(String.valueOf(value),
                () -> rejectsStaleOrInvalidDestinationWithoutChangingSelection(value)));
    }

    private void rejectsStaleOrInvalidDestinationWithoutChangingSelection(String kind) throws Exception {
        onFx(() -> {
            var original = publish("destination.txt", "prefix definition suffix");
            Services.EDITOR_TAB_MANAGER.open(original.uri().filePath().orElseThrow());
            var editor = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow().view().textEditor().orElseThrow();
            var current = editor.snapshot();
            editor.selectAndReveal(current, new TextSelection(2, 2));
            var expected = new TextDocumentSnapshot(kind.equals("identity") ? DocumentId.create() : current.id(),
                current.uri(), kind.equals("version") ? current.version().next() : current.version(), "text",
                current.origin(), kind.equals("text") ? "PREFIX definition suffix" : current.text(),
                StandardCharsets.UTF_8);
            var location = new Location.TextLocation(expected.id(),
                kind.equals("locationVersion") ? expected.version().next() : expected.version(),
                new TextRange(7, kind.equals("bounds") ? 100 : 17), kind.equals("coordinates") ? 1 : 0, 7);
            assertFalse(Services.EDITOR_TAB_MANAGER.navigateTo(null, expected, location));
            assertEquals(new TextSelection(2, 2), editor.selection());
            return null;
        });
    }

    @Test
    public void revealsLazyMarkdownSourceBeforeCapturingItsSnapshot() throws Exception {
        onFx(() -> {
            project.getDataStore().writeJson("markdown.json", new MarkdownPreviewPane.MarkdownLayout(
                MarkdownPreviewPane.MarkdownLayoutType.PREVIEW));
            var expected = publish("destination.md", "# Heading\nDefinition");
            Services.EDITOR_TAB_MANAGER.open(expected.uri().filePath().orElseThrow());
            var tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertNull(tab.view().activeEditor());
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, expected,
                Location.TextLocation.from(expected, 10, 20)));
            assertNotNull(tab.view().activeEditor());
            assertEquals(new TextSelection(10, 20), tab.view().textEditor().orElseThrow().selection());
            return null;
        });
    }

    @Test
    public void rejectsNavigationOffTheFxThread() throws Exception {
        var expected = onFx(() -> publish("destination.txt", "definition"));
        assertThrows(IllegalStateException.class, () -> Services.EDITOR_TAB_MANAGER.navigateTo(null, expected,
            Location.TextLocation.from(expected, 0, 10)));
    }

    @Test
    public void goToDefinitionCommandOpensCrossFileTargetThroughTheEditorRequestPipeline() throws Exception {
        onFx(() -> {
            var destination = publish("destination.txt", "prefix definition suffix");
            openSource((context, offset) -> new FeatureResult<>(context,
                List.of(target(destination, 7, 17))));
            assertTrue(Commands.GO_TO_DEFINITION.execute(
                CommandContext.withArgument(project, sourceEditor, sourceEditor)));
            return null;
        });
        sourceEditor.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            var tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals("destination.txt", tab.path().getFileName().toString());
            assertEquals(new TextSelection(7, 17), tab.view().textEditor().orElseThrow().selection());
            assertEquals(1, sourceEditor.getCaretPosition());
            return null;
        });
    }

    @Test
    public void destinationEditDuringPendingF12PreventsOpeningTheStaleTarget() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var destination = onFx(() -> publish("destination.txt", "prefix definition suffix"));
        try {
            onFx(() -> {
                openSource((context, offset) -> {
                    entered.countDown();
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new CancellationException();
                    }
                    return new FeatureResult<>(context, List.of(target(destination, 7, 17)));
                });
                sourceEditor.goToDefinition();
                return null;
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            onFx(() -> {
                var store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                var current = (TextDocumentSnapshot) store.current(destination.id()).orElseThrow();
                var changed = new TextDocumentSnapshot(current.id(), current.uri(), current.version().next(),
                    current.languageId(), current.origin(), "changed destination", StandardCharsets.UTF_8);
                store.publishBacking(changed);
                return null;
            });
        } finally {
            release.countDown();
        }
        sourceEditor.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            assertTrue(Services.EDITOR_TAB_MANAGER.activeTab().isEmpty());
            assertEquals(1, sourceEditor.getCaretPosition());
            return null;
        });
    }

    @Test
    public void ambiguousDefinitionsOfferAChooserAndOnlyNavigateToThePickedTarget() throws Exception {
        var destination = onFx(() -> publish("destination.txt", "first second third"));
        onFx(() -> {
            openSource((context, offset) -> new FeatureResult<>(context, List.of(
                target(destination, 0, 5), target(destination, 6, 12))));
            sourceEditor.goToDefinition();
            return null;
        });
        sourceEditor.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            assertTrue(Services.EDITOR_TAB_MANAGER.activeTab().isEmpty());
            assertEquals(List.of(0, 6), sourceEditor.choices().stream().map(choice -> choice.range().start).toList());
            sourceEditor.choose(1);
            var tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(destination.id(), tab.documentId());
            assertEquals(new TextSelection(6, 12), tab.view().textEditor().orElseThrow().selection());
            assertTrue(sourceEditor.choices().isEmpty());
            return null;
        });
    }

    @Test
    public void chooserIsDiscardedWhenTheSourceChanges() throws Exception {
        var destination = onFx(() -> publish("destination.txt", "first second third"));
        onFx(() -> {
            openSource((context, offset) -> new FeatureResult<>(context, List.of(
                target(destination, 0, 5), target(destination, 6, 12))));
            sourceEditor.goToDefinition();
            return null;
        });
        sourceEditor.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            assertEquals(2, sourceEditor.choices().size());
            sourceEditor.replaceText(0, 0, "x");
            assertTrue(sourceEditor.choices().isEmpty());
            sourceEditor.choose(0);
            assertTrue(Services.EDITOR_TAB_MANAGER.activeTab().isEmpty());
            return null;
        });
    }

    @Test
    public void unresolvedDefinitionShowsAMessageWithoutNavigating() throws Exception {
        onFx(() -> {
            openSource((context, offset) -> new FeatureResult<>(context, List.of()));
            sourceEditor.goToDefinition();
            return null;
        });
        sourceEditor.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            assertNotNull(sourceEditor.message());
            assertTrue(Services.EDITOR_TAB_MANAGER.activeTab().isEmpty());
            assertEquals(1, sourceEditor.getCaretPosition());
            return null;
        });
    }

    @Test
    public void goToImplementationUsesTheImplementationQuery() throws Exception {
        var destination = onFx(() -> publish("destination.txt", "prefix implementation suffix"));
        onFx(() -> {
            openSource(new NavigationProvider() {
                @Override
                public FeatureResult<List<DefinitionTarget>> definitions(FeatureRequestContext context, int offset) {
                    throw new AssertionError("Definitions must not be queried");
                }

                @Override
                public FeatureResult<List<DefinitionTarget>> implementations(
                    FeatureRequestContext context,
                    int offset
                ) {
                    return new FeatureResult<>(context, List.of(target(destination, 7, 21)));
                }
            });
            sourceEditor.goToImplementation();
            return null;
        });
        sourceEditor.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            var tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(new TextSelection(7, 21), tab.view().textEditor().orElseThrow().selection());
            return null;
        });
    }

    @Test
    public void navigateBackRestoresTheOriginCaretAndForwardReturnsToTheDestination() throws Exception {
        onFx(() -> {
            var origin = publish("origin.txt", "origin text here");
            var destination = publish("destination.txt", "prefix definition suffix");
            Services.EDITOR_TAB_MANAGER.open(origin.uri().filePath().orElseThrow());
            var originTab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            originTab.view().activeEditor().moveTo(3);
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, destination,
                Location.TextLocation.from(destination, 7, 17)));
            var destinationTab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertNotSame(originTab, destinationTab);

            originTab.view().activeEditor().moveTo(0);
            pane.navigateBack();
            assertTrue(originTab.tab().isSelected());
            assertEquals(3, originTab.view().activeEditor().getCaretPosition());

            pane.navigateForward();
            assertTrue(destinationTab.tab().isSelected());
            assertEquals(17, destinationTab.view().activeEditor().getCaretPosition());
            return null;
        });
    }

    @Test
    public void navigateBackWithinOneFileReturnsToTheOriginCaret() throws Exception {
        onFx(() -> {
            var document = publish("document.txt", "use here and definition there");
            Services.EDITOR_TAB_MANAGER.open(document.uri().filePath().orElseThrow());
            var editor = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow().view().activeEditor();
            editor.moveTo(4);
            var current = editor.textEditorAccess().snapshot();
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, current,
                Location.TextLocation.from(current, 13, 23)));
            assertEquals(13, editor.getCaretPosition());
            pane.navigateBack();
            assertEquals(4, editor.getCaretPosition());
            pane.navigateForward();
            assertEquals(13, editor.getCaretPosition());
            return null;
        });
    }

    @Test
    public void navigateBackIgnoresAPositionFromAnEditedRevision() throws Exception {
        onFx(() -> {
            var origin = publish("origin.txt", "origin text here");
            var destination = publish("destination.txt", "prefix definition suffix");
            Services.EDITOR_TAB_MANAGER.open(origin.uri().filePath().orElseThrow());
            var originTab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            var originEditor = originTab.view().activeEditor();
            originEditor.moveTo(10);
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, destination,
                Location.TextLocation.from(destination, 7, 17)));

            originEditor.replaceText(0, 0, "edited ");
            originEditor.moveTo(1);
            pane.navigateBack();
            assertTrue(originTab.tab().isSelected());
            assertEquals(1, originEditor.getCaretPosition());
            return null;
        });
    }

    @Test
    public void findUsagesShowsResultsInTheWorkspaceToolAndOpensThem() throws Exception {
        var destination = onFx(() -> publish("destination.txt", "one usage here"));
        var usage = Location.TextLocation.from(destination, 4, 9);
        onFx(() -> {
            openSource(new NavigationProvider() {
                @Override
                public FeatureResult<List<DefinitionTarget>> definitions(FeatureRequestContext context, int offset) {
                    return new FeatureResult<>(context, List.of());
                }

                @Override
                public FeatureResult<UsageSearch> usages(
                    FeatureRequestContext context,
                    UsageQuery query,
                    UsageSink sink
                ) {
                    assertEquals(new UsageQuery.AtOffset(1), query);
                    sink.accept(List.of(usage), 1, 1);
                    return new FeatureResult<>(context, new UsageSearch(new SymbolId("test:usage"), "usage",
                        List.of(usage)));
                }
            });
            sourceEditor.findUsages();
            return null;
        });
        UsagesPane usages = onFx(() -> {
            pane.applyCss();
            pane.layout();
            return pane.lookupAll(".usages-pane").stream()
                .map(UsagesPane.class::cast)
                .findFirst()
                .orElseThrow();
        });
        usages.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> null);
        onFx(() -> {
            assertTrue(pane.isDockItemActive(IDEDockItem.USAGES));
            assertEquals(UsagesPane.State.COMPLETE, usages.state());
            assertEquals(List.of(usage), usages.usages());
            assertTrue(usages.open(usage));
            var tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(destination.id(), tab.documentId());
            assertEquals(new TextSelection(4, 9), tab.view().textEditor().orElseThrow().selection());
            return null;
        });
    }

    private void openSource(NavigationProvider provider) throws Exception {
        var source = publish("source.txt", "reference");
        sourceEditor = new NavigationEditor(project, source.id(), source.uri().filePath().orElseThrow(),
            new CodeEditorConfig("text", null, null, null, provider, null));
        sourceEditor.setDocumentStore(Services.IDE_STATE.getDocumentStore().orElseThrow());
        sourceEditor.moveTo(1);
    }

    private static final class NavigationEditor extends CodeEditorPane {
        private NavigationEditor(Project project, DocumentId id, Path path, CodeEditorConfig config) {
            super(project, id, path, config);
        }

        private List<Location.TextLocation> choices() {
            return navigationChoices();
        }

        private void choose(int index) {
            chooseNavigationTarget(index);
        }

        private String message() {
            return navigationMessage();
        }

        private CompletableFuture<Void> completion() {
            return featureRequests.completion();
        }

        @Override
        protected boolean supportsHighlighting() {
            return false;
        }

        @Override
        protected boolean shouldRequestSignatureHelp(String text, int caret, boolean changed) {
            return false;
        }
    }

    private static DefinitionTarget target(TextDocumentSnapshot destination, int start, int end) {
        return new DefinitionTarget(destination, Location.TextLocation.from(destination, start, end));
    }

    private TextDocumentSnapshot publish(String name, String text) throws Exception {
        // Markdown must stay under watcher coverage to keep its preview source lazy.
        Path path = (name.endsWith(".md") ? directory : directory.resolve(".railroad/navigation")).resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        String language = name.endsWith(".md") ? "markdown" : name.endsWith(".java") ? "java" : "text";
        var identity = Services.IDE_STATE.identifyDocument(new FileSystemDocument(path, language));
        var uri = DocumentUri.fromPath(path);
        var snapshot = new TextDocumentSnapshot(identity.id(), uri, DocumentVersion.initial(), language,
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
        Services.IDE_STATE.getDocumentStore().orElseThrow().publishBacking(snapshot);
        return snapshot;
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Error error)
                throw error;
            if (exception.getCause() instanceof Exception cause)
                throw cause;
            throw exception;
        }
    }
}
