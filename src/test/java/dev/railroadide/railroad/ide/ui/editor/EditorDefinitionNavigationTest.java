package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.Commands;
import dev.railroadide.railroad.command.EditCommands;
import dev.railroadide.railroad.command.RunCommands;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.defaults.GradleRunConfigurationType;
import dev.railroadide.railroad.ide.runconfig.defaults.data.GradleRunConfigurationData;
import dev.railroadide.railroad.ide.ui.BuildProblemsPane;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.utility.JavaVersion;
import java.io.File;
import org.gradle.tooling.GradleConnector;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.provider.api.NavigationProvider;
import dev.railroadide.railroad.ide.sst.provider.api.UsageQuery;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSearch;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSink;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.sst.semantic.api.DefinitionTarget;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.ui.IDEDockItem;
import dev.railroadide.railroad.ide.ui.IDEPane;
import dev.railroadide.railroad.ide.ui.MarkdownPreviewPane;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorConfig;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorPane;
import dev.railroadide.railroad.ide.ui.navigation.UsagesPane;
import dev.railroadide.railroad.ide.ui.search.ProjectSearchPane;
import dev.railroadide.railroad.ide.search.ProjectTextSearch;
import dev.railroadide.railroad.plugin.defaults.FileSystemDocument;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.theme.ThemeManager;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;
import dev.railroadide.railroad.ide.diagnostics.BuildProblems;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.editor.TextEditorAccess;
import java.lang.reflect.Field;

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
        Field hostServices = Railroad.class.getDeclaredField("hostServices");
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
            TextDocumentSnapshot expected = publish("destination.txt", "prefix definition suffix");
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, expected,
                Location.TextLocation.from(expected, 7, 17)));
            EditorTab tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(expected.id(), tab.documentId());
            assertEquals(new TextSelection(7, 17), tab.view().textEditor().orElseThrow().selection());
            return null;
        });
    }

    @Test
    public void opensUnopenedCrlfDestinationAndSelectsItsDefinition() throws Exception {
        onFx(() -> {
            String text = "class Destination {\r\n    void first() {}\r\n    void definition() {}\r\n}\r\n";
            TextDocumentSnapshot expected = publish("Destination.java", text);
            int start = text.indexOf("definition");
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, expected,
                Location.TextLocation.from(expected, start, start + "definition".length())));
            EditorTab tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(expected.id(), tab.documentId());
            TextEditorAccess editor = tab.view().textEditor().orElseThrow();
            String shown = editor.snapshot().text();
            int shownStart = shown.indexOf("definition");
            assertEquals(new TextSelection(shownStart, shownStart + "definition".length()), editor.selection());
            return null;
        });
    }

    @Test
    public void crlfDestinationWhoseFileChangedIsNotSelected() throws Exception {
        onFx(() -> {
            TextDocumentSnapshot expected = publish("Destination.java",
                "class Destination {\r\n    void definition() {}\r\n}\r\n");
            Files.writeString(expected.uri().filePath().orElseThrow(),
                "class Destination {\r\n    void renamed() {}\r\n}\r\n");
            assertFalse(Services.EDITOR_TAB_MANAGER.navigateTo(null, expected,
                Location.TextLocation.from(expected, 29, 39)));
            TextEditorAccess editor = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow().view().textEditor()
                .orElseThrow();
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
            EditorTab tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(file.toAbsolutePath().normalize(), tab.path());
            TextEditorAccess editor = tab.view().textEditor().orElseThrow();
            assertEquals(new TextSelection(7, 17), editor.selection());
            assertFalse(editor.isEditable());
            assertFalse(tab.view().activeEditor().isEditable());
            return null;
        });
    }

    @Test
    public void reusesExistingDestinationTabInsteadOfNavigatingThePreviouslyActiveTab() throws Exception {
        onFx(() -> {
            TextDocumentSnapshot destination = publish("destination.txt", "prefix definition suffix");
            Services.EDITOR_TAB_MANAGER.open(destination.uri().filePath().orElseThrow());
            EditorTab existing = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            TextDocumentSnapshot other = publish("source.txt", "source text");
            Services.EDITOR_TAB_MANAGER.open(other.uri().filePath().orElseThrow());
            EditorTab source = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
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
            TextDocumentSnapshot original = publish("destination.txt", "prefix definition suffix");
            Services.EDITOR_TAB_MANAGER.open(original.uri().filePath().orElseThrow());
            TextEditorAccess editor = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow().view().textEditor()
                .orElseThrow();
            TextDocumentSnapshot current = editor.snapshot();
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
            TextDocumentSnapshot expected = publish("destination.md", "# Heading\nDefinition");
            Services.EDITOR_TAB_MANAGER.open(expected.uri().filePath().orElseThrow());
            EditorTab tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
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
        TextDocumentSnapshot expected = onFx(() -> publish("destination.txt", "definition"));
        assertThrows(IllegalStateException.class, () -> Services.EDITOR_TAB_MANAGER.navigateTo(null, expected,
            Location.TextLocation.from(expected, 0, 10)));
    }

    @Test
    public void goToDefinitionCommandOpensCrossFileTargetThroughTheEditorRequestPipeline() throws Exception {
        onFx(() -> {
            TextDocumentSnapshot destination = publish("destination.txt", "prefix definition suffix");
            openSource((context, offset) -> new FeatureResult<>(context,
                List.of(target(destination, 7, 17))));
            assertTrue(Commands.GO_TO_DEFINITION.execute(
                CommandContext.withArgument(project, sourceEditor, sourceEditor)));
            return null;
        });

        sourceEditor.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            EditorTab tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
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
        TextDocumentSnapshot destination = onFx(() -> publish("destination.txt", "prefix definition suffix"));
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
                DocumentStore store = Services.IDE_STATE.getDocumentStore().orElseThrow();
                TextDocumentSnapshot current = (TextDocumentSnapshot) store.current(destination.id()).orElseThrow();
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
        TextDocumentSnapshot destination = onFx(() -> publish("destination.txt", "first second third"));
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
            EditorTab tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(destination.id(), tab.documentId());
            assertEquals(new TextSelection(6, 12), tab.view().textEditor().orElseThrow().selection());
            assertTrue(sourceEditor.choices().isEmpty());
            return null;
        });
    }

    @Test
    public void chooserIsDiscardedWhenTheSourceChanges() throws Exception {
        TextDocumentSnapshot destination = onFx(() -> publish("destination.txt", "first second third"));
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
        TextDocumentSnapshot destination = onFx(() -> publish("destination.txt", "prefix implementation suffix"));
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
            EditorTab tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(new TextSelection(7, 21), tab.view().textEditor().orElseThrow().selection());
            return null;
        });
    }

    @Test
    public void navigateBackRestoresTheOriginCaretAndForwardReturnsToTheDestination() throws Exception {
        onFx(() -> {
            TextDocumentSnapshot origin = publish("origin.txt", "origin text here");
            TextDocumentSnapshot destination = publish("destination.txt", "prefix definition suffix");
            Services.EDITOR_TAB_MANAGER.open(origin.uri().filePath().orElseThrow());
            EditorTab originTab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            originTab.view().activeEditor().moveTo(3);
            assertTrue(Services.EDITOR_TAB_MANAGER.navigateTo(null, destination,
                Location.TextLocation.from(destination, 7, 17)));
            EditorTab destinationTab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
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
            TextDocumentSnapshot document = publish("document.txt", "use here and definition there");
            Services.EDITOR_TAB_MANAGER.open(document.uri().filePath().orElseThrow());
            TextEditorPane editor = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow().view().activeEditor();
            editor.moveTo(4);
            TextDocumentSnapshot current = editor.textEditorAccess().snapshot();
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
            TextDocumentSnapshot origin = publish("origin.txt", "origin text here");
            TextDocumentSnapshot destination = publish("destination.txt", "prefix definition suffix");
            Services.EDITOR_TAB_MANAGER.open(origin.uri().filePath().orElseThrow());
            EditorTab originTab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            TextEditorPane originEditor = originTab.view().activeEditor();
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
    public void runSavesPendingSubprojectEditsAndOpensBuildProblemsAtTheReportedLine() throws Exception {
        Path file = directory.resolve("app/src/main/java/Demo.java");
        Files.createDirectories(file.getParent());
        String valid = "class Demo {\n    static int value = 1;\n    public static void main(String[] args) {} }\n";
        String invalid = valid.replace("= 1", "= missing");
        Files.writeString(file, valid);
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'test'\ninclude 'app'");
        Files.writeString(directory.resolve("app/build.gradle"), """
            plugins { id 'java' }
            tasks.register('launch', JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'Demo'
            }
            """);
        BlockingQueue<CompletableFuture<Void>> launches = new LinkedBlockingQueue<>();
        var type = new GradleRunConfigurationType(() -> GradleConnector.newConnector()
            .useInstallation(new File(System.getProperty("railroad.test.gradleHome")))) {
            @Override
            public CompletableFuture<Void> run(Project target, RunConfiguration<GradleRunConfigurationData> config) {
                CompletableFuture<Void> result = super.run(target, config);
                launches.add(result);
                return result;
            }
        };

        var config = new RunConfiguration<>(type, new GradleRunConfigurationData());
        config.data().setName("Launch");
        config.data().setTask(":app:launch");
        config.data().setGradleProjectPath(directory);
        config.data()
            .setJavaHome(new JDK(Path.of(System.getProperty("java.home")), "Test JDK", JavaVersion.fromMajor(25)));
        onFx(() -> {
            Services.EDITOR_TAB_MANAGER.open(file);
            TextEditorPane editor = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow().view().activeEditor();
            editor.replaceText(invalid);
            assertTrue(RunCommands.RUN.execute(CommandContext.withArgument(project, pane, config)));
            assertEquals(invalid, Files.readString(file).replace("\r\n", "\n"));
            return null;
        });

        CompletableFuture<Void> first = launches.remove();
        assertThrows(ExecutionException.class, () -> first.get(60, TimeUnit.SECONDS));
        BuildProblems problems = project.getRunSessions().buildProblems();
        assertEquals(1, problems.problems().size(), problems.problems()::toString);
        BuildProblems.Problem error = problems.problems().getFirst();
        assertEquals(file, error.file());
        assertEquals(2, error.line());
        onFx(() -> {
            pane.applyCss();
            pane.layout();
            assertTrue(pane.isDockItemActive(IDEDockItem.PROBLEMS));
            BuildProblemsPane view = (BuildProblemsPane) pane.lookup(".build-problems-pane");
            ListView<?> rows = (ListView<?>) view.getCenter();
            rows.getSelectionModel().select(0);
            rows.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            return null;
        });

        onFx(() -> {
            EditorTab tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertTrue(Files.isSameFile(file, tab.path()));
            TextEditorPane editor = tab.view().activeEditor();
            assertEquals(1, editor.getCurrentParagraph());
            editor.replaceText(valid);
            assertTrue(RunCommands.RUN.execute(CommandContext.withArgument(project, pane, config)));
            assertTrue(problems.problems().isEmpty());
            return null;
        });

        launches.remove().get(60, TimeUnit.SECONDS);
        assertTrue(problems.problems().isEmpty());
    }

    @Test
    public void findUsagesShowsResultsInTheWorkspaceToolAndOpensThem() throws Exception {
        TextDocumentSnapshot destination = onFx(() -> publish("destination.txt", "one usage here"));
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
                    assertEquals(new UsageQuery.AtOffset("reference".length() - 1), query);
                    sink.accept(List.of(usage), 1, 1);
                    return new FeatureResult<>(context, new UsageSearch(new SymbolId("test:usage"), "usage",
                        List.of(usage)));
                }
            });

            sourceEditor.selectRange(0, "reference".length());
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
            EditorTab tab = Services.EDITOR_TAB_MANAGER.activeTab().orElseThrow();
            assertEquals(destination.id(), tab.documentId());
            assertEquals(new TextSelection(4, 9), tab.view().textEditor().orElseThrow().selection());
            return null;
        });
    }

    @Test
    public void findUsagesAtTheEndOfALibraryMethodNameUsesTheActiveJavaEditor() throws Exception {
        String text = "class Main { String format(int value) { return Integer.toHexString(value); } }";
        int start = text.indexOf("toHexString");
        int end = text.indexOf('(', start);
        UsagesPane usages = onFx(() -> {
            TextDocumentSnapshot source = publish("Main.java", text);
            Services.EDITOR_TAB_MANAGER.open(source.uri().filePath().orElseThrow());
            CodeEditorPane editor = Commands.activeCodeEditor();
            assertNotNull(editor);
            editor.moveTo(end);
            assertEquals(0, editor.getSelection().getLength());
            assertTrue(Commands.FIND_USAGES.execute(CommandContext.withArgument(project, editor, editor)));
            pane.applyCss();
            pane.layout();
            return (UsagesPane) pane.lookup(".usages-pane");
        });

        assertNotNull(usages);
        usages.completion().get(30, TimeUnit.SECONDS);
        onFx(() -> {
            assertEquals(UsagesPane.State.COMPLETE, usages.state(), usages.statusText());
            assertEquals(List.of(new TextRange(start, end)), usages.usages().stream().map(Location::range).toList());
            return null;
        });
    }

    @Test
    public void replaceInFilesSearchesTheProjectForTheActiveEditorSelection() throws Exception {
        String text = "class Gear { String id = \"mymod:copper_gear\"; }";
        int start = text.indexOf("mymod:copper_gear");
        ProjectSearchPane search = onFx(() -> {
            TextDocumentSnapshot source = publish("Gear.java", text);
            publish("gear.json", "{\"result\": \"mymod:copper_gear\"}");
            publish("other.json", "{\"result\": \"mymod:tin_gear\"}");
            Services.EDITOR_TAB_MANAGER.open(source.uri().filePath().orElseThrow());
            CodeEditorPane editor = Commands.activeCodeEditor();
            assertNotNull(editor);
            editor.selectRange(start, start + "mymod:copper_gear".length());
            assertTrue(EditCommands.REPLACE_IN_FILES.execute(CommandContext.forProject(project, editor)));
            pane.applyCss();
            pane.layout();
            return (ProjectSearchPane) pane.lookup(".project-search-pane");
        });

        assertNotNull(search);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (onFx(search::state) != ProjectSearchPane.State.COMPLETE) {
            assertTrue(System.nanoTime() < deadline, "Project search did not complete");
            Thread.sleep(10);
        }

        onFx(() -> {
            assertTrue(pane.isDockItemActive(IDEDockItem.FIND_IN_FILES));
            assertEquals("mymod:copper_gear", search.searchField().getText());
            assertTrue(search.isReplaceVisible());
            assertEquals(List.of(".railroad/navigation/Gear.java", ".railroad/navigation/gear.json"),
                search.results().stream().map(ProjectTextSearch.FileMatches::relativePath).toList());
            return null;
        });
    }

    private void openSource(NavigationProvider provider) throws Exception {
        TextDocumentSnapshot source = publish("source.txt", "reference");
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
        DocumentIdentity identity = Services.IDE_STATE.identifyDocument(new FileSystemDocument(path, language));
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
