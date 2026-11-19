package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.BinaryEditorDiagnostic;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

public class EditorDiagnosticTest {
    @Test
    public void copiesMetadataAndPreservesOpaqueDataAndOrigin() {
        var snapshot = textSnapshot(DocumentId.create(), new DocumentVersion(3));
        var location = Location.TextLocation.from(snapshot, 0, 1);
        var tags = new HashSet<>(Set.of(DiagnosticTag.DEPRECATED));
        var related = new ArrayList<>(List.of(new RelatedLocation(location, "Declared here")));
        var diagnostic = new TextEditorDiagnostic(location, DiagnosticSeverity.WARNING, "OLD_NAME", "Old name",
            "sample:inspection", DiagnosticOrigin.INSPECTION, tags, related, "{\"fix\":\"rename\"}");
        tags.clear();
        related.clear();

        assertEquals(Set.of(DiagnosticTag.DEPRECATED), diagnostic.tags());
        assertEquals(1, diagnostic.relatedInformation().size());
        assertEquals("{\"fix\":\"rename\"}", diagnostic.data());
        assertEquals("sample:inspection", diagnostic.source());
        assertEquals(DiagnosticOrigin.INSPECTION, diagnostic.origin());
        assertThrows(UnsupportedOperationException.class, () -> diagnostic.tags().clear());
        assertThrows(UnsupportedOperationException.class, () -> diagnostic.relatedInformation().clear());
        assertThrows(NullPointerException.class,
            () -> new TextEditorDiagnostic(null, DiagnosticSeverity.ERROR, "BAD", "Missing location"));
    }

    @Test
    public void rejectsCachedReportsRelabelledWithANewRequest() {
        var id = DocumentId.create();
        var earlier = textSnapshot(id, new DocumentVersion(1));
        var current = textSnapshot(id, new DocumentVersion(2));
        DiagnosticsProvider<TextEditorDiagnostic> provider = _ -> List.of(new TextEditorDiagnostic(
            Location.TextLocation.from(earlier, 0, 1), DiagnosticSeverity.ERROR, "STALE", "Old result"));

        assertThrows(IllegalArgumentException.class, () -> provider.compute(context(current)));
    }

    @Test
    public void acceptsIndependentBinaryReportsWithTheSameVersionContract() {
        var id = DocumentId.create();
        var snapshot = new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id), new DocumentVersion(5),
            "sample:binary", new byte[]{1, 2});
        var diagnostic = new BinaryEditorDiagnostic(Location.BinaryLocation.from(snapshot, 0, 2),
            DiagnosticSeverity.INFORMATION, "HEADER", "Header decoded", "sample:binary",
            DiagnosticOrigin.SEMANTIC, Set.of(), List.of(), "header:2");
        DiagnosticsProvider<BinaryEditorDiagnostic> provider = _ -> List.of(diagnostic);
        var request = context(snapshot);
        var result = provider.compute(request);

        assertTrue(result.belongsTo(request));
        assertEquals(List.of(diagnostic), result.value());
        assertEquals(new DocumentVersion(5), result.value().getFirst().location().documentVersion());
    }

    @Test
    public void retainsOverlappingReportsFromDistinctSources() {
        var snapshot = textSnapshot(DocumentId.create(), DocumentVersion.initial());
        var location = Location.TextLocation.from(snapshot, 0, 1);
        var syntax = new TextEditorDiagnostic(location, DiagnosticSeverity.ERROR, "INVALID", "Invalid name",
            "sample:parser", DiagnosticOrigin.SYNTAX, Set.of(), List.of(), null);
        var build = new TextEditorDiagnostic(location, DiagnosticSeverity.ERROR, "INVALID", "Invalid name",
            "sample:builder", DiagnosticOrigin.BUILD, Set.of(), List.of(), null);
        var mutable = new ArrayList<>(List.of(syntax, build));
        var reports = DiagnosticsProvider.validated(mutable, context(snapshot));
        mutable.clear();

        assertEquals(List.of(syntax, build), reports);
        assertThrows(UnsupportedOperationException.class, reports::clear);
    }

    @Test
    public void checksCancellationBeforePublishingEvenEmptyResults() {
        var snapshot = textSnapshot(DocumentId.create(), DocumentVersion.initial());
        var cancellation = new CancellationTokenSource();
        var context = new FeatureRequestContext(context(snapshot).workspaceSnapshot(), snapshot,
            cancellation.token(), Deadline.none());
        DiagnosticsProvider<TextEditorDiagnostic> provider = _ -> {
            cancellation.cancel();
            return List.of();
        };
        assertThrows(CancellationException.class, () -> provider.compute(context));
    }

    private static TextDocumentSnapshot textSnapshot(DocumentId id, DocumentVersion version) {
        return new TextDocumentSnapshot(id, DocumentUri.inMemory(id), version, "sample:text", "value",
            StandardCharsets.UTF_8);
    }

    private static FeatureRequestContext context(DocumentSnapshot snapshot) {
        var workspace = new WorkspaceSnapshot("diagnostic-contract", Map.of(snapshot.id(), snapshot),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
        return new FeatureRequestContext(workspace, snapshot, CancellationToken.none(), Deadline.none());
    }
}
