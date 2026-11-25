package dev.railroadide.railroad.ide.sst.edit.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceEditModelTest {
    @Test
    public void builderPreservesDeclarationOrderAnnotationsAndSelection() {
        TextDocumentSnapshot document = text("abc");
        Path file = Path.of("Created.java").toAbsolutePath();
        WorkspaceEdit edit = WorkspaceEdit.builder("Example")
            .annotation("why", new ChangeAnnotation("Reason", null, true))
            .change(new WorkspaceChange.TextEdit(document, List.of(replacement(0, 1, "x")), "why"))
            .createText(DocumentUri.fromPath(file), "class Created {}")
            .delete(document)
            .select(document.id(), new TextSelection(1, 2))
            .build();

        assertEquals("Example", edit.label());
        assertEquals(3, edit.changes().size());
        assertInstanceOf(WorkspaceChange.TextEdit.class, edit.changes().get(0));
        assertInstanceOf(WorkspaceChange.CreateFile.class, edit.changes().get(1));
        assertInstanceOf(WorkspaceChange.DeleteFile.class, edit.changes().get(2));
        assertEquals("", edit.annotations().get("why").description());
        assertTrue(edit.annotations().get("why").needsConfirmation());
        assertEquals(new PostApplySelection(document.id(), new TextSelection(1, 2)), edit.selection());
    }

    @Test
    public void rejectsUnknownAnnotationsBlankLabelsAndNonFileResources() {
        TextDocumentSnapshot document = text("abc");
        var unannotated = new WorkspaceChange.TextEdit(document, List.of(), "missing");
        assertThrows(IllegalArgumentException.class,
            () -> new WorkspaceEdit("Edit", List.of(unannotated), Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> WorkspaceEdit.builder(" ").build());
        assertThrows(IllegalArgumentException.class, () -> new ChangeAnnotation(" ", "", false));
        assertThrows(IllegalArgumentException.class,
            () -> WorkspaceChange.CreateFile.text(DocumentUri.inMemory(DocumentId.create()), "x"));
        assertThrows(IllegalArgumentException.class,
            () -> new WorkspaceChange.RenameFile(document, DocumentUri.virtual("memory", "x"), null));
        assertThrows(NullPointerException.class,
            () -> new WorkspaceChange.TextEdit(document, Arrays.asList((TextReplacement) null)));
    }

    @Test
    public void changesAndEditsAreImmutableCopies() {
        TextDocumentSnapshot document = text("abc");
        List<TextReplacement> replacements = new ArrayList<>(List.of(replacement(0, 1, "x")));
        var change = new WorkspaceChange.TextEdit(document, replacements);
        replacements.clear();
        assertEquals(1, change.replacements().size());
        assertThrows(UnsupportedOperationException.class, () -> change.replacements().clear());

        byte[] content = "one".getBytes(StandardCharsets.UTF_8);
        var create = new WorkspaceChange.CreateFile(DocumentUri.fromPath(Path.of("a.txt").toAbsolutePath()), content,
            null);
        content[0] = (byte) 'X';
        create.content()[1] = (byte) 'Y';
        assertArrayEquals("one".getBytes(StandardCharsets.UTF_8), create.content());
        assertEquals(create, new WorkspaceChange.CreateFile(create.uri(), "one".getBytes(StandardCharsets.UTF_8),
            null));

        List<WorkspaceChange> changes = new ArrayList<WorkspaceChange>(List.of(change));
        var edit = new WorkspaceEdit("Edit", changes, Map.of(), null);
        changes.clear();
        assertEquals(1, edit.changes().size());
        assertThrows(UnsupportedOperationException.class, () -> edit.annotations().put("x", null));
    }

    @Test
    public void resultsAndPreviewsReportApplicability() {
        var problem = new WorkspaceEditProblem(WorkspaceEditProblem.Kind.STALE, 0, null, "stale");
        assertFalse(new WorkspaceEditResult(WorkspaceEditResult.Status.REJECTED, List.of(problem)).applied());
        assertTrue(new WorkspaceEditResult(WorkspaceEditResult.Status.APPLIED, List.of()).applied());
        assertFalse(new WorkspaceEditPreview("Edit", List.of(), List.of(), List.of(problem)).applicable());
        assertTrue(new WorkspaceEditPreview("Edit", List.of(), List.of(), List.of()).applicable());
        assertThrows(IllegalArgumentException.class,
            () -> new WorkspaceEditProblem(WorkspaceEditProblem.Kind.STALE, -2, null, "invalid"));
    }

    private static TextDocumentSnapshot text(String text) {
        DocumentId id = DocumentId.create();
        return new TextDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(), "plaintext", text,
            StandardCharsets.UTF_8);
    }

    private static TextReplacement replacement(int start, int end, String text) {
        return new TextReplacement(new TextRange(start, end), text);
    }
}
