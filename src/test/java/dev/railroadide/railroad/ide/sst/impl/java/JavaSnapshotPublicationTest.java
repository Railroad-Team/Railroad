package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.internal.SyntaxInternalFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaSnapshotPublicationTest {
    @Test
    public void incrementalParsingRetainsTheOldSourceTreeAndSemanticModel() {
        DocumentId id = DocumentId.create();
        var uri = DocumentUri.inMemory(id);
        String source = "class Example { int x = 1; }";
        var old = new TextDocumentSnapshot(id, uri, DocumentVersion.initial(), "java", source, StandardCharsets.UTF_8);
        var tree = JavaSyntaxParser.parse(old);
        var model = JavaSemanticAnalyzer.analyzeFacts(tree);
        int offset = source.indexOf('1');
        String edited = source.substring(0, offset) + "2" + source.substring(offset + 1);
        var newer = new TextDocumentSnapshot(id, uri, old.version().next(), "java", edited, StandardCharsets.UTF_8);
        var result = JavaSyntaxParser.parseIncremental(tree, newer, new JavaSyntaxParser.TextEdit(offset, 1, "2"));
        assertSame(old, tree.documentSnapshot());
        assertSame(tree, model.syntaxTree());
        assertSame(newer, result.tree().documentSnapshot());
        assertEquals(source, SyntaxInternalFactory.sourceText(SyntaxInternalFactory.greenRoot(tree)));
        assertEquals(edited, SyntaxInternalFactory.sourceText(SyntaxInternalFactory.greenRoot(result.tree())));
        assertFalse(old.hasSameRevision(newer));
        assertNotSame(tree, result.tree());
    }

    @Test
    public void parseDiagnosticsAndReuseCandidatesAreDetachedFromCallerLists() {
        var candidates = new ArrayList<>(List.of(new JavaSyntaxParser.ReuseCandidate("kind", 0, 1, 0, 1)));
        var plan = new JavaSyntaxParser.ReusePlan(0, 1, 0, 1, 0, candidates);
        candidates.clear();
        assertEquals(1, plan.candidates().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.candidates().clear());
        var parsed = JavaSyntaxParser.parseWithDiagnostics("class {");
        assertFalse(parsed.diagnostics().isEmpty());
        var diagnostics = new ArrayList<SyntaxDiagnostic>(parsed.diagnostics());
        var copied = new JavaSyntaxParser.ParseResult(parsed.tree(), diagnostics);
        diagnostics.clear();
        assertEquals(parsed.diagnostics(), copied.diagnostics());
        assertThrows(UnsupportedOperationException.class, () -> copied.diagnostics().clear());
    }
}
