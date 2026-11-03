package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;

public class ProjectDocumentSynchronizerTest {
    @TempDir
    public Path directory;

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
}
