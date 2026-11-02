package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WorkspaceSnapshotTest {
    private final DocumentId mainId = DocumentId.create();
    private final DocumentId utilsId = DocumentId.create();
    private final TextDocumentSnapshot main = snapshot(mainId, DocumentVersion.initial(), "class Main {}");
    private final TextDocumentSnapshot utils = snapshot(utilsId, DocumentVersion.initial(), "class Utils {}");

    @Test
    public void changesToOriginalMapDoNotAffectCapturedDocuments() {
        Map<DocumentId, DocumentSnapshot> documents = new HashMap<>(Map.of(mainId, main, utilsId, utils));
        var workspace = new WorkspaceSnapshot(documents, 42L);
        var addedId = DocumentId.create();

        documents.put(mainId, snapshot(mainId, main.version().next(), "class Main { int x; }"));
        documents.remove(utilsId);
        documents.put(addedId, snapshot(addedId, DocumentVersion.initial(), "class Added {}"));

        assertEquals(2, workspace.documents().size());
        assertEquals(42L, workspace.revision());
        assertSame(main, workspace.document(mainId).orElseThrow());
        assertSame(utils, workspace.document(utilsId).orElseThrow());
        assertEquals(Optional.empty(), workspace.document(addedId));
    }

    @Test
    public void capturedDocumentMapCannotBeModified() {
        var workspace = new WorkspaceSnapshot(Map.of(mainId, main, utilsId, utils), 0L);
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
        var workspace = new WorkspaceSnapshot(Map.of(mainId, main, utilsId, utils), 0L);

        assertSame(main, workspace.document(mainId).orElseThrow());
        assertSame(utils, workspace.document(utilsId).orElseThrow());
        assertEquals(Optional.empty(), workspace.document(DocumentId.create()));
    }

    private TextDocumentSnapshot snapshot(DocumentId id, DocumentVersion version, String text) {
        return new TextDocumentSnapshot(id, DocumentUri.inMemory(id), version, "java", text, StandardCharsets.UTF_8);
    }
}
