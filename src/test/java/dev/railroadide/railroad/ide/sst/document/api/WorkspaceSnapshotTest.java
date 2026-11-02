package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkspaceSnapshotTest {
    private final WorkspaceConfigurationSnapshot configuration = new WorkspaceConfigurationSnapshot(0, Set.of());
    private final DocumentId mainId = DocumentId.create();
    private final DocumentId utilsId = DocumentId.create();
    private final TextDocumentSnapshot main = snapshot(mainId, DocumentVersion.initial(), "class Main {}");
    private final TextDocumentSnapshot utils = snapshot(utilsId, DocumentVersion.initial(), "class Utils {}");

    @Test
    public void changesToOriginalMapDoNotAffectCapturedDocuments() {
        Map<DocumentId, DocumentSnapshot> documents = new HashMap<>(Map.of(mainId, main, utilsId, utils));
        var workspace = new WorkspaceSnapshot("project-test", documents, configuration, 42L);
        var addedId = DocumentId.create();

        documents.put(mainId, snapshot(mainId, main.version().next(), "class Main { int x; }"));
        documents.remove(utilsId);
        documents.put(addedId, snapshot(addedId, DocumentVersion.initial(), "class Added {}"));

        assertEquals(2, workspace.documents().size());
        assertEquals(42L, workspace.revision());
        assertEquals("project-test", workspace.projectId());
        assertSame(main, workspace.document(mainId).orElseThrow());
        assertSame(utils, workspace.document(utilsId).orElseThrow());
        assertEquals(Optional.empty(), workspace.document(addedId));
    }

    @Test
    public void capturedDocumentMapCannotBeModified() {
        var workspace = new WorkspaceSnapshot("project-test", Map.of(mainId, main, utilsId, utils), configuration, 0L);
        var replacement = snapshot(mainId, main.version().next(), "class Main { int x; }");

        assertThrows(UnsupportedOperationException.class, () -> workspace.documents().put(mainId, replacement));
        assertThrows(UnsupportedOperationException.class, () -> workspace.documents().remove(utilsId));
        assertThrows(UnsupportedOperationException.class, () -> workspace.documents().clear());

        assertEquals(2, workspace.documents().size());
        assertSame(main, workspace.document(mainId).orElseThrow());
        assertSame(utils, workspace.document(utilsId).orElseThrow());
    }

    @Test
    public void lookupsReturnOriginalSnapshotsOrEmptyForUnknownDocuments() {
        var workspace = new WorkspaceSnapshot("project-test", Map.of(mainId, main, utilsId, utils), configuration, 0L);

        assertSame(main, workspace.document(mainId).orElseThrow());
        assertSame(utils, workspace.document(utilsId).orElseThrow());
        assertEquals(Optional.empty(), workspace.document(DocumentId.create()));
    }

    @Test
    public void nullProjectIdIsRejected() {
        assertThrows(NullPointerException.class, () -> new WorkspaceSnapshot(null, Map.of(), configuration, 0));
    }

    @Test
    public void blankProjectIdsAreRejected() {
        for (var projectId : List.of("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException.class,
                () -> new WorkspaceSnapshot(projectId, Map.of(), configuration, 0));
        }
    }

    @Test
    public void nullConfigurationIsRejected() {
        assertThrows(NullPointerException.class, () -> new WorkspaceSnapshot("project-test", Map.of(), null, 0));
    }

    @Test
    public void negativeWorkspaceRevisionIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkspaceSnapshot("project-test", Map.of(), configuration, -1));
    }

    @Test
    public void nullDocumentMapIsRejected() {
        assertThrows(NullPointerException.class, () -> new WorkspaceSnapshot("project-test", null, configuration, 0));
    }

    @Test
    public void nullDocumentKeyIsRejected() {
        Map<DocumentId, DocumentSnapshot> documents = new HashMap<>();
        documents.put(null, main);

        assertThrows(NullPointerException.class,
            () -> new WorkspaceSnapshot("project-test", documents, configuration, 0));
    }

    @Test
    public void nullDocumentValueIsRejected() {
        Map<DocumentId, DocumentSnapshot> documents = new HashMap<>();
        documents.put(mainId, null);

        assertThrows(NullPointerException.class,
            () -> new WorkspaceSnapshot("project-test", documents, configuration, 0));
    }

    @Test
    public void validEmptyWorkspaceAtRevisionZeroIsAccepted() {
        var workspace = new WorkspaceSnapshot("project-test", Map.of(), configuration, 0);

        assertEquals("project-test", workspace.projectId());
        assertSame(configuration, workspace.configuration());
        assertEquals(0L, workspace.revision());
        assertTrue(workspace.documents().isEmpty());
        assertEquals(Optional.empty(), workspace.document(mainId));
    }

    private TextDocumentSnapshot snapshot(DocumentId id, DocumentVersion version, String text) {
        return new TextDocumentSnapshot(id, DocumentUri.inMemory(id), version, "java", text, StandardCharsets.UTF_8);
    }
}
