package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceChange;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticsProvider;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.examples.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.plugin.spi.editor.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

public class IndependentLanguageProviderTest {
    @Test
    public void tinyLanguageOffersParsingDiagnosticsSymbolsNavigationAndVersionedFix() {
        TextDocumentSnapshot document = tiny("let answer 42\nuse answer\nuse missing\n");
        FeatureRequestContext request = request(document);
        try (LanguageSession session = new TinyLanguageContribution().openSession(context())) {
            var tree = session.capability(SstCapabilities.PARSING).orElseThrow().parse(request);
            assertTrue(tree.belongsTo(request));
            assertSame(document, tree.value().documentSnapshot());
            assertEquals(document.text().length(), tree.value().root().end());

            var diagnostics = session.capability(SstCapabilities.DIAGNOSTICS).orElseThrow().compute(request);
            List<?> reports = (List<?>) diagnostics.value();
            assertEquals(2, reports.size());
            for (Object report : reports) {
                var diagnostic = (EditorDiagnostic<?>) report;
                assertEquals("example:tiny", diagnostic.source());
                diagnostic.location().validateAgainst(document);
            }
            var symbols = session.capability(SstCapabilities.INDEX_EXTRACTION).orElseThrow().extract(request);
            assertEquals("answer", symbols.value().getFirst().symbol().simpleName());
            assertEquals("example:tiny.variable", symbols.value().getFirst().symbol().kind().id());
            var targets = session.capability(SstCapabilities.NAVIGATION).orElseThrow()
                .definitions(request, document.text().toString().indexOf("use answer") + 4);
            assertEquals(1, targets.value().size());
            Location<?> declaration = symbols.value().getFirst().location();
            Location<?> target = targets.value().getFirst();
            assertEquals(declaration.documentId(), target.documentId());
            assertEquals(declaration.documentVersion(), target.documentVersion());
            assertEquals(declaration.range().start, target.range().start);
            assertEquals(declaration.range().end, target.range().end);
            var query = session.capability(SstCapabilities.INDEX_QUERY).orElseThrow().query(
                new WorkspaceRequestContext(request.workspaceSnapshot(), CancellationToken.none(), Deadline.none()),
                "answer");
            assertEquals(1, query.value().size());
            assertTrue(session.capability(SstCapabilities.COMPLETION).isEmpty());

            var action = session.capability(SstCapabilities.CODE_ACTIONS).orElseThrow()
                .actions(request, whole(document)).value().getFirst();
            var change = (WorkspaceChange.TextEdit) action.edit().changes().getFirst();
            assertSame(document, change.expected());
            assertEquals(action.title(), action.edit().label());
            TextReplacement replacement = change.replacements().getFirst();
            String fixed = document.text().toString().substring(0, replacement.range().start)
                + replacement.text() + document.text().toString().substring(replacement.range().end);
            assertTrue(fixed.startsWith("let answer = 42"));
        }
    }

    @Test
    public void binaryProviderUsesRealBinaryLocationsWithoutSyntaxPlaceholders() {
        DocumentId id = DocumentId.create();
        var binary = new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(),
            PacketLanguageContribution.ID.value(), new byte[]{0, 1, 2});
        try (var session = new PacketLanguageContribution().openSession(context())) {
            assertTrue(session.capability(SstCapabilities.PARSING).isEmpty());
            var result = session.capability(SstCapabilities.DIAGNOSTICS).orElseThrow().compute(request(binary));
            var diagnostic = (EditorDiagnostic<?>) ((List<?>) result.value()).getFirst();
            assertInstanceOf(Location.BinaryLocation.class, diagnostic.location());
            diagnostic.location().validateAgainst(binary);
            var symbols = session.capability(SstCapabilities.INDEX_EXTRACTION).orElseThrow().extract(request(binary));
            assertEquals("header", symbols.value().getFirst().symbol().simpleName());
            assertTrue(symbols.value().getFirst().symbol().declaration().isEmpty());
        }
    }

    @Test
    public void cancellationAndUnloadRejectRequestsAndRetainedCapabilities() {
        var document = tiny("let answer = 42\n");
        var original = request(document);
        var cancellation = new CancellationTokenSource();
        cancellation.cancel();
        var cancelled = new FeatureRequestContext(original.workspaceSnapshot(), document, cancellation.token(),
            Deadline.none());
        var session = new TinyLanguageContribution().openSession(context());
        var parser = session.capability(SstCapabilities.PARSING).orElseThrow();
        assertThrows(CancellationException.class, () -> parser.parse(cancelled));
        session.close();
        session.close();
        assertThrows(CancellationException.class, () -> parser.parse(original));
        assertThrows(CancellationException.class, () -> session.capability(SstCapabilities.PARSING));
    }

    @Test
    public void diagnosticsAndActionsRemainBoundToTheirInputRevision() {
        var document = tiny("let value 10");
        try (var session = new TinyLanguageContribution().openSession(context())) {
            var result = session.capability(SstCapabilities.DIAGNOSTICS).orElseThrow().compute(request(document));
            var diagnostic = (EditorDiagnostic<?>) ((List<?>) result.value()).getFirst();
            var newer = new TextDocumentSnapshot(document.id(), document.uri(), document.version().next(),
                document.languageId(), "let value = 10", StandardCharsets.UTF_8);
            assertThrows(IllegalArgumentException.class, () -> diagnostic.location().validateAgainst(newer));
            var action = session.capability(SstCapabilities.CODE_ACTIONS).orElseThrow()
                .actions(request(document), whole(document)).value().getFirst();
            assertNotEquals(newer.version(),
                ((WorkspaceChange.TextEdit) action.edit().changes().getFirst()).expected().version());
        }
    }

    private static CodeActionRequest whole(TextDocumentSnapshot document) {
        return new CodeActionRequest(new TextRange(0, document.text().length()), List.of());
    }

    private static LanguageSessionContext context() {
        return new LanguageSessionContext(Path.of("."), "", CapabilityRegistry.empty());
    }

    private static TextDocumentSnapshot tiny(String text) {
        DocumentId id = DocumentId.create();
        return new TextDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(),
            TinyLanguageContribution.ID.value(), text, StandardCharsets.UTF_8);
    }

    private static FeatureRequestContext request(DocumentSnapshot document) {
        var workspace = new WorkspaceSnapshot("sample", Map.of(document.id(), document),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
        return new FeatureRequestContext(workspace, document, CancellationToken.none(), Deadline.none());
    }
}
