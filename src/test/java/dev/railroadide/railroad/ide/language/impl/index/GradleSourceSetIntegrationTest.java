package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.gradle.model.*;
import dev.railroadide.railroad.ide.ProjectWorkspaceConfiguration;
import dev.railroadide.railroad.ide.diagnostics.JavaDiagnosticsProvider;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceChange;
import dev.railroadide.railroad.ide.sst.impl.java.*;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySources;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroadplugin.action.RailroadProjectAction;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroadplugin.dto.RailroadProject;
import static org.junit.jupiter.api.Assertions.*;

public class GradleSourceSetIntegrationTest {
    @TempDir
    public Path directory;

    @Test
    public void editorMatchesGradleBeforeAndAfterChangingClientDependencies() throws Exception {
        WorkspaceModes.initialize();
        LanguageSupports.initialize();
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'fixture'\ninclude 'library'");
        Path build = directory.resolve("build.gradle");
        Files.writeString(build, """
            plugins { id 'java' }
            sourceSets {
                client { java.srcDirs = ['client-code']; compileClasspath += main.output }
                independent
            }
            """);
        TextDocumentSnapshot game = file("src/main/java/demo/Game.java",
            "package demo; public class Game { public static int value() { return 1; } }");
        TextDocumentSnapshot test = file("src/test/java/demo/OnlyInTests.java",
            "package demo; public class OnlyInTests { public int read() { return Game.value(); } }");
        TextDocumentSnapshot client = file("client-code/demo/Client.java",
            "package demo; public class Client { int read() { return Game.value() + ClientApi.answer(); } }");
        TextDocumentSnapshot duplicate = file("src/independent/java/demo/Game.java",
            "package demo; public class Game { public static int value() { return 2; } int read() { return value(); } }");
        TextDocumentSnapshot dependency = file("library/src/main/java/demo/ClientApi.java",
            "package demo; public class ClientApi { public static int answer() { return 3; } }");
        Files.writeString(directory.resolve("library/build.gradle"), "plugins { id 'java-library' }");
        Path init = directory.resolve("import.gradle");
        String plugin = Path.of(RailroadProjectAction.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString().replace('\\', '/').replace("'", "\\'");
        Files.writeString(init, """
            initscript { dependencies { classpath files('%s') } }
            allprojects { apply plugin: dev.railroadide.railroadplugin.RailroadProjectPlugin }
            """.formatted(plugin));
        try (ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(directory.toFile())
            .useInstallation(new File(System.getProperty("railroad.test.gradleHome"))).connect();
            var provider = new JavaWorkspaceAnalysisProvider(() -> true,
                (roots, _) -> JavaLibrarySymbolIndex.build(roots));
            var services = new JavaSstServices(JavaLibrarySources.shared(), provider::capture)) {
            var initial = ProjectWorkspaceConfiguration.captureGradleConfiguration(model(connection, init));
            var store = new DefaultDocumentStore("fixture", initial);
            List.of(game, test, client, duplicate, dependency).forEach(store::publishBacking);
            WorkspaceSnapshot before = store.capture();
            var diagnostics = new JavaDiagnosticsProvider(null, null, provider::capture);
            assertFalse(provider.capture(before, client.id()).typeNames().contains("ClientApi"));
            assertTrue(
                diagnostics.compute(before, client.id()).stream().anyMatch(d -> d.code().startsWith("SEM_UNRESOLVED")));
            connection.newBuild().forTasks(":compileTestJava", ":compileIndependentJava").run();
            assertThrows(BuildException.class, () -> connection.newBuild().forTasks("compileClientJava").run());

            Files.writeString(build, "\ndependencies { clientImplementation project(':library') }",
                StandardOpenOption.APPEND);
            GradleBuildModel updated = model(connection, init);
            var config = ProjectWorkspaceConfiguration.captureGradleConfiguration(updated);
            store.publishConfiguration(new WorkspaceConfigurationSnapshot(1, config.moduleIds(), config.settings()));
            WorkspaceSnapshot workspace = store.capture();
            assertFalse(Files.exists(directory.resolve("library/build/classes/java/main/demo/ClientApi.class")));
            assertTrue(offersAnswer(provider.capture(workspace, client.id())));
            assertEquals(dependency.id(),
                services.definitions(context(workspace, client), client.text().indexOf("answer"))
                    .value().getFirst().location().documentId());
            assertFalse(offersAnswer(provider.capture(workspace, game.id())));
            assertFalse(provider.capture(workspace, game.id()).typeNames().contains("ClientApi"));
            assertFalse(provider.capture(before, client.id()).typeNames().contains("ClientApi"));
            connection.newBuild().forTasks("compileClientJava").run();
            assertFalse(diagnostics.compute(workspace, client.id()).stream()
                .anyMatch(d -> d.code().startsWith("SEM_UNRESOLVED")));

            FeatureRequestContext clientContext = context(workspace, client);
            assertEquals(game.id(), services.definitions(clientContext, client.text().indexOf("value")).value()
                .getFirst().location().documentId());
            FeatureRequestContext gameContext = context(workspace, game);
            int offset = game.text().indexOf("value");
            UsageSearch usages = services.usages(gameContext, new UsageQuery.AtOffset(offset), UsageSink.NONE).value();
            assertEquals(Set.of(test.id(), client.id()),
                usages.usages().stream().map(Location::documentId).collect(Collectors.toSet()));
            RenameProposal rename = services.rename(gameContext, offset, usages.symbol(), "amount").value();
            assertTrue(rename.applicable(), () -> rename.conflicts().toString());
            assertEquals(Set.of(game.id(), test.id(), client.id()), rename.edit().changes().stream()
                .map(WorkspaceChange.TextEdit.class::cast).map(edit -> edit.expected().id())
                .collect(Collectors.toSet()));

            var cache = new PersistedGradleModelStore(directory.resolve("cache"), "test");
            assertTrue(cache.save(directory, updated, Instant.now()));
            PersistedGradleModelStore.Restored reopened = cache.load(directory).orElseThrow();
            assertTrue(reopened.current());
            var restored = ProjectWorkspaceConfiguration.captureGradleConfiguration(reopened.model());
            assertEquals(config, restored);
            var reopenedStore = new DefaultDocumentStore("reopened", restored);
            List.of(game, client, dependency).forEach(reopenedStore::publishBacking);
            assertTrue(offersAnswer(provider.capture(reopenedStore.capture(), client.id())));
            assertFalse(offersAnswer(provider.capture(reopenedStore.capture(), game.id())));

            TextDocumentSnapshot leak = file("src/main/java/demo/Leak.java",
                "package demo; class Leak { void run(OnlyInTests t) { t.read(); } }");
            store.publishBacking(leak);
            JavaWorkspaceAnalysis analysis = provider.capture(store.capture(), leak.id());
            var completion = JavaSemanticCompletionEngine.compute(leak.text(), leak.text().indexOf("t.read") + 1,
                analysis);
            assertTrue(
                completion == null || completion.items().stream().noneMatch(item -> item.insertText().equals("read")));
            assertTrue(diagnostics.compute(store.capture(), leak.id()).stream()
                .anyMatch(d -> d.code().equals("SEM_UNRESOLVED_TYPE")));
            FeatureRequestContext check = context(store.capture(), test);
            int readOffset = test.text().indexOf("read");
            UsageSearch read = services.usages(check, new UsageQuery.AtOffset(readOffset), UsageSink.NONE).value();
            assertTrue(read.usages().isEmpty());
            RenameProposal renameRead = services.rename(check, readOffset, read.symbol(), "inspect").value();
            assertTrue(renameRead.applicable(), () -> renameRead.conflicts().toString());
            assertEquals(List.of(test.id()),
                renameRead.edit().changes().stream().map(WorkspaceChange.TextEdit.class::cast)
                    .map(edit -> edit.expected().id()).toList());
            assertThrows(BuildException.class, () -> connection.newBuild().forTasks("compileJava").run());
        }
    }

    private static boolean offersAnswer(JavaWorkspaceAnalysis analysis) {
        String text = "package demo; class Example { void use() { ClientApi. } }";
        var result = JavaSemanticCompletionEngine.compute(text, text.indexOf("ClientApi.") + "ClientApi".length(),
            analysis);
        return result != null && result.items().stream().anyMatch(item -> item.insertText().equals("answer"));
    }

    private GradleBuildModel model(ProjectConnection connection, Path init) {
        RailroadProject project = connection.action(new RailroadProjectAction())
            .withArguments("--offline", "--init-script", init.toString()).run();
        return new GradleBuildModel("fixture", directory, null, project);
    }

    private TextDocumentSnapshot file(String relative, String text) throws Exception {
        Path path = directory.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        return new TextDocumentSnapshot(DocumentId.create(), DocumentUri.fromPath(path), DocumentVersion.initial(),
            "java", text, StandardCharsets.UTF_8);
    }

    private static FeatureRequestContext context(WorkspaceSnapshot workspace, TextDocumentSnapshot document) {
        return new FeatureRequestContext(workspace, document, CancellationToken.NONE, Deadline.none());
    }
}
