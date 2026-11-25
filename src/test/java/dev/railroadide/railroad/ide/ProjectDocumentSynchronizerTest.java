package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import java.util.concurrent.LinkedBlockingQueue;
import dev.railroadide.railroad.ide.sst.document.api.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.BlockingQueue;
import static org.junit.jupiter.api.Assertions.*;

public class ProjectDocumentSynchronizerTest {
    @TempDir
    public Path directory;

    @Test
    public void initialScanCountsPublicationsAfterDiscovery() throws Exception {
        LanguageSupports.initialize();
        Files.writeString(directory.resolve("one.txt"), "one");
        Files.writeString(directory.resolve("two.txt"), "two");
        BlockingQueue<Runnable> publications = new LinkedBlockingQueue<Runnable>();
        var registry = BackgroundActivities.shared();
        var store = new DefaultDocumentStore("scan", new WorkspaceConfigurationSnapshot(0, Set.of()));
        try (var sync = new ProjectDocumentSynchronizer(directory, store, new DocumentIdentityRegistry(),
            _ -> false, publications::add)) {
            Runnable first = publications.poll(10, TimeUnit.SECONDS);
            Runnable second = publications.poll(10, TimeUnit.SECONDS);
            assertNotNull(first);
            assertNotNull(second);
            BackgroundActivities.Activity scan = registry.running().stream()
                .filter(a -> a.titleKey().equals("railroad.activity.scanning_files"))
                .findFirst().orElseThrow();
            assertEquals(2, scan.total());
            assertEquals(0, scan.completed());
            first.run();
            assertEquals(1,
                registry.running().stream().filter(a -> a.id() == scan.id()).findFirst().orElseThrow().completed());
            second.run();
            sync.initialScan().get(10, TimeUnit.SECONDS);
            assertTrue(registry.running().stream().noneMatch(a -> a.id() == scan.id()));
        }
    }

    @Test
    public void buildOutputsAreSkippedExceptGeneratedSourcesAndSourcePackages() throws Exception {
        LanguageSupports.initialize();
        Path output = Files.createDirectories(directory.resolve("common/build/version-audit")).resolve("Audit.java");
        Path log = directory.resolve("build/compile-output.log");
        Path generated = Files.createDirectories(directory.resolve("common/build/generated/sources"))
            .resolve("Generated.java");
        Path sourcePackage = Files.createDirectories(directory.resolve("common/src/main/java/example/build"))
            .resolve("Builder.java");
        Files.writeString(output, "class Audit {}");
        Files.createDirectories(log.getParent());
        Files.writeString(log, "BUILD SUCCESSFUL");
        Files.writeString(generated, "class Generated {}");
        Files.writeString(sourcePackage, "class Builder {}");
        var identities = new DocumentIdentityRegistry();
        DocumentStore store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        try (ProjectDocumentSynchronizer synchronizer = new ProjectDocumentSynchronizer(directory, store, identities,
            _ -> false, Runnable::run)) {
            synchronizer.initialScan().get(10, TimeUnit.SECONDS);
            assertTrue(identities.findIdentity(DocumentUri.fromPath(output)).isEmpty());
            assertTrue(identities.findIdentity(DocumentUri.fromPath(log)).isEmpty());
            assertTrue(identities.findIdentity(DocumentUri.fromPath(generated)).isPresent());
            assertTrue(identities.findIdentity(DocumentUri.fromPath(sourcePackage)).isPresent());
            assertFalse(ProjectDocumentSynchronizer.isWorkspacePath(directory, output));
            assertTrue(ProjectDocumentSynchronizer.isWorkspacePath(directory, generated));
            assertTrue(ProjectDocumentSynchronizer.isWorkspacePath(directory, sourcePackage));
        }
    }

    @Test
    public void unopenedAndClosedFilesTrackDiskWithoutDependingOnEditors() throws Exception {
        LanguageSupports.initialize();
        Path source = directory.resolve("Unopened.java");
        Path binary = directory.resolve("resource.bin");
        Files.writeString(source, "class Unopened {}");
        Files.write(binary, new byte[]{0, 1, 2});

        var identities = new DocumentIdentityRegistry();
        DocumentStore store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        try (ProjectDocumentSynchronizer synchronizer = new ProjectDocumentSynchronizer(directory, store, identities,
            _ -> false, Runnable::run)) {
            synchronizer.initialScan().get(10, TimeUnit.SECONDS);
            DocumentId id = identities.findIdentity(DocumentUri.fromPath(source)).orElseThrow().id();
            DocumentId resource = identities.findIdentity(DocumentUri.fromPath(binary)).orElseThrow().id();
            WorkspaceSnapshot before = store.capture();
            assertEquals("class Unopened {}", ((TextDocumentSnapshot) before.document(id).orElseThrow()).text());
            assertInstanceOf(BinaryDocumentSnapshot.class, before.document(resource).orElseThrow());
            synchronizer.refresh(source).get(10, TimeUnit.SECONDS);
            assertEquals(before, store.capture());
            Files.writeString(source, "class Unopened { int external; }");
            synchronizer.refresh(source).get(10, TimeUnit.SECONDS);
            TextDocumentSnapshot changed = (TextDocumentSnapshot) store.current(id).orElseThrow();
            assertEquals("class Unopened { int external; }", changed.text());
            assertEquals(before.document(id).orElseThrow().version().next(), changed.version());
            assertNull(store.state(id).orElseThrow().overlay());
            Files.delete(source);
            synchronizer.refresh(source).get(10, TimeUnit.SECONDS);
            assertTrue(store.current(id).isEmpty());
            Files.writeString(source, "class Recreated {}");
            synchronizer.refresh(source).get(10, TimeUnit.SECONDS);
            assertEquals(id, identities.findIdentity(DocumentUri.fromPath(source)).orElseThrow().id());
            assertEquals(changed.version().next(), store.current(id).orElseThrow().version());
            Files.write(binary, new byte[]{0, 9, 8});

            synchronizer.refresh(binary).get(10, TimeUnit.SECONDS);
            assertEquals(9, ((BinaryDocumentSnapshot) store.current(resource).orElseThrow()).bytes().get(1));
            assertEquals(1, ((BinaryDocumentSnapshot) before.document(resource).orElseThrow()).bytes().get(1));
        }
    }

    @Test
    public void deletionAndReadFailurePreserveRetainedDraft() throws Exception {
        LanguageSupports.initialize();
        Path file = directory.resolve("Open.java");
        Files.writeString(file, "class Open {}");
        var identities = new DocumentIdentityRegistry();
        DocumentStore store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        try (ProjectDocumentSynchronizer synchronizer = new ProjectDocumentSynchronizer(directory, store, identities,
            _ -> false, Runnable::run)) {
            synchronizer.initialScan().get(10, TimeUnit.SECONDS);
            DocumentId id = identities.findIdentity(DocumentUri.fromPath(file)).orElseThrow().id();
            DocumentSnapshot original = store.current(id).orElseThrow();
            TextDocumentSnapshot backing = (TextDocumentSnapshot) original;
            store.publishOverlay(new TextDocumentSnapshot(id, backing.uri(), backing.version().next(),
                backing.languageId(), backing.text(), backing.encoding()));
            Files.delete(file);
            synchronizer.refresh(file).get(10, TimeUnit.SECONDS);
            DocumentState deleted = store.state(id).orElseThrow();
            assertNull(deleted.backing());
            assertTrue(deleted.isStale());
            assertEquals(((TextDocumentSnapshot) original).text(), ((TextDocumentSnapshot) deleted.overlay()).text());
            WorkspaceSnapshot retained = store.capture();
            Files.write(file, new byte[]{(byte) 0xc3, 0x28});

            assertThrows(ExecutionException.class,
                () -> synchronizer.refresh(file).get(10, TimeUnit.SECONDS));
            assertEquals(retained, store.capture());
        }
    }

    @Test
    public void deletingOneHardLinkKeepsTheDocumentPublishedAtTheSurvivingLink() throws Exception {
        LanguageSupports.initialize();
        Path first = directory.resolve("First.txt");
        Path second = directory.resolve("Second.txt");
        Files.writeString(first, "shared");
        try {
            Files.createLink(second, first);
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.abort("Hard links are unavailable on this filesystem");
        }

        var identities = new DocumentIdentityRegistry();
        DocumentStore store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        try (ProjectDocumentSynchronizer synchronizer = new ProjectDocumentSynchronizer(directory, store, identities,
            _ -> false, Runnable::run)) {
            synchronizer.initialScan().get(10, TimeUnit.SECONDS);
            DocumentId id = identities.findIdentity(DocumentUri.fromPath(first)).orElseThrow().id();
            assertEquals(id, identities.findIdentity(DocumentUri.fromPath(second)).orElseThrow().id());
            Path deleted = store.current(id).orElseThrow().uri().filePath().orElseThrow();
            Path surviving = deleted.equals(first) ? second : first;

            Files.delete(deleted);
            synchronizer.refresh(deleted).get(10, TimeUnit.SECONDS);

            DocumentSnapshot current = store.current(id).orElseThrow();
            assertTrue(Files.isSameFile(surviving, current.uri().filePath().orElseThrow()), current.uri()::toString);
            assertEquals("shared", ((TextDocumentSnapshot) current).text());
            assertEquals(id, identities.findIdentity(DocumentUri.fromPath(surviving)).orElseThrow().id());
            assertTrue(identities.findIdentity(DocumentUri.fromPath(deleted)).isEmpty());

            Files.writeString(deleted, "independent");
            synchronizer.refresh(deleted).get(10, TimeUnit.SECONDS);
            DocumentId recreated = identities.findIdentity(DocumentUri.fromPath(deleted)).orElseThrow().id();
            assertNotEquals(id, recreated);
            assertEquals("independent", ((TextDocumentSnapshot) store.current(recreated).orElseThrow()).text());
            assertEquals("shared", ((TextDocumentSnapshot) store.current(id).orElseThrow()).text());
        }
    }
}
