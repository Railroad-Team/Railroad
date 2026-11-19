package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.sst.document.api.*;
import org.junit.jupiter.api.Test;

import dev.railroadide.railroad.ide.diagnostics.DiagnosticSeverity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collection;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import static org.junit.jupiter.api.Assertions.*;

public class CodeEditorDiagnosticsTest {
    @Test
    public void discardsInvalidLocationsWhileRetainingValidFirstLineAndEofDiagnostics() {
        var id = DocumentId.create();
        var version = new DocumentVersion(3);
        var snapshot = new TextDocumentSnapshot(id, DocumentUri.inMemory(id), version,
            "java", "Hello\nWorld", StandardCharsets.UTF_8);
        var firstLine = diagnostic(Location.TextLocation.from(snapshot, 0, 5));
        var eof = diagnostic(Location.TextLocation.from(snapshot, 11, 11));
        var otherDocument = diagnostic(new Location.TextLocation(DocumentId.create(), version,
            new TextRange(0, 5), 0, 0));
        var stale = diagnostic(new Location.TextLocation(id, new DocumentVersion(2), new TextRange(0, 5), 0, 0));
        var future = diagnostic(new Location.TextLocation(id, version.next(), new TextRange(0, 5), 0, 0));
        var wrongLine = diagnostic(new Location.TextLocation(id, version, new TextRange(0, 5), 1, 0));
        var wrongColumn = diagnostic(new Location.TextLocation(id, version, new TextRange(0, 5), 0, 1));
        var invalidEnd = diagnostic(new Location.TextLocation(id, version, new TextRange(0, 12), 0, 0));
        var invalidStart = diagnostic(new Location.TextLocation(id, version, new TextRange(12, 12), 1, 6));

        var input = new ArrayList<>(Arrays.asList(otherDocument, stale, firstLine, future, wrongLine,
            wrongColumn, invalidEnd, invalidStart, null, eof));
        List<TextEditorDiagnostic> accepted = CodeEditorPane.validatedDiagnostics(input, snapshot);

        assertEquals(List.of(firstLine, eof), accepted);
        input.clear();
        assertEquals(List.of(firstLine, eof), accepted);
    }

    @Test
    public void rejectsTextDiagnosticsForBinarySnapshots() {
        var id = DocumentId.create();
        var version = DocumentVersion.initial();
        var snapshot = new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id), version, "binary", new byte[5]);
        var diagnostic = diagnostic(new Location.TextLocation(id, version, new TextRange(0, 5), 0, 0));

        assertEquals(List.of(), CodeEditorPane.validatedDiagnostics(List.of(diagnostic), snapshot));
    }

    @Test
    public void acceptsEmptyDiagnosticResults() {
        var id = DocumentId.create();
        var snapshot = new TextDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(),
            "java", "", StandardCharsets.UTF_8);

        assertEquals(List.of(), CodeEditorPane.validatedDiagnostics(List.of(), snapshot));
    }

    @Test
    public void informationalDiagnosticsDoNotAppearAsWarningSquiggles() throws Exception {
        var id = DocumentId.create();
        var snapshot = new TextDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(),
            "sample:text", "value", StandardCharsets.UTF_8);
        var base = new StyleSpansBuilder<Collection<String>>().add(List.of("identifier"), 5).create();
        var merge = CodeEditorPane.class.getDeclaredMethod("mergeDiagnosticStyles", StyleSpans.class, List.class);
        merge.setAccessible(true);
        for (var severity : List.of(DiagnosticSeverity.INFORMATION, DiagnosticSeverity.HINT)) {
            var diagnostic = new TextEditorDiagnostic(Location.TextLocation.from(snapshot, 0, 5), severity,
                "INFO", "Information");
            var styled = (StyleSpans<?>) merge.invoke(null, base, List.of(diagnostic));
            assertEquals(List.of("identifier"), styled.getStyleSpan(0).getStyle());
        }
    }

    private static TextEditorDiagnostic diagnostic(Location.TextLocation location) {
        return new TextEditorDiagnostic(location, DiagnosticSeverity.ERROR, "TEST", "Test diagnostic");
    }
}
