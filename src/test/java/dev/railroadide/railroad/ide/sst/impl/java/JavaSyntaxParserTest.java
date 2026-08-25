package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaSyntaxParserTest {

    @Test
    void carriesCallerSuppliedDocumentIdentity() {
        DocumentId documentId = DocumentId.create();
        DocumentUri documentUri = DocumentUri.virtual("memory", "tests/Identified.java");
        var documentVersion = new DocumentVersion(7);

        SyntaxTree tree = JavaSyntaxParser.parse(
            documentId,
            documentUri,
            documentVersion,
            "class Identified {}");

        assertEquals(documentId, tree.documentId());
        assertEquals(documentUri, tree.documentUri());
        assertEquals(documentVersion, tree.documentVersion());
    }

    @Test
    void parsesAndRetainsTheExactTextSnapshot() {
        var snapshot = new TextDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("memory", "tests/Snapshot.java"),
            new DocumentVersion(4),
            "java",
            "class Snapshot {}",
            StandardCharsets.UTF_16LE);

        SyntaxTree tree = JavaSyntaxParser.parse(snapshot);

        assertSame(snapshot, tree.documentSnapshot());
        assertEquals(snapshot.text(), JavaParserTestSupport.syntaxText(tree));
        assertThrows(IllegalArgumentException.class, () -> JavaSyntaxParser.parse(
            new TextDocumentSnapshot(
                snapshot.id(),
                snapshot.uri(),
                snapshot.version(),
                "kotlin",
                "class Snapshot",
                StandardCharsets.UTF_8)));
    }

    @Test
    void roundTripsSourceTextFromSyntaxTree() {
        String source = """
            package demo;
            import java.util.List;
            // keep this comment
            class A {
                int x = 1;
            }
            """;

        SyntaxTree tree = JavaSyntaxParser.parse(source);
        assertEquals(source, JavaParserTestSupport.syntaxText(tree));
    }

    @Test
    void includesExpectedTopLevelStructureKinds() {
        String source = """
            package demo;
            import java.util.List;
            class A {}
            record R(int x) {}
            """;

        SyntaxTree tree = JavaSyntaxParser.parse(source);
        List<String> topLevelKinds = tree.root().children().stream().map(node -> node.kind().id()).toList();

        assertEquals(1, countKind(topLevelKinds, JavaSyntaxKinds.PACKAGE_DECLARATION.id()));
        assertEquals(1, countKind(topLevelKinds, JavaSyntaxKinds.IMPORT_DECLARATION.id()));
        assertEquals(1, countKind(topLevelKinds, JavaSyntaxKinds.CLASS_DECLARATION.id()));
        assertEquals(1, countKind(topLevelKinds, JavaSyntaxKinds.RECORD_DECLARATION.id()));
        assertEquals(1, countKind(topLevelKinds, JavaSyntaxKinds.tokenKind(JavaTokenType.EOF).id()));
    }

    @Test
    void parseWithDiagnosticsReportsRecoveryArtifacts() {
        String source = "class Broken { void run( { int x = ; }";
        JavaSyntaxParser.ParseResult result = JavaSyntaxParser.parseWithDiagnostics(source);

        assertFalse(result.diagnostics().isEmpty());
        assertEquals(source, JavaParserTestSupport.syntaxText(result.tree()));
    }

    @Test
    void nestedGenericClosersDoNotConsumeFollowingImplementedType() {
        String source = """
            import java.io.Serializable;
            import java.util.Comparator;
            import java.util.List;
            class Example implements Comparator<List<String>>, Serializable {}
            """;

        JavaSyntaxParser.ParseResult result = JavaSyntaxParser.parseWithDiagnostics(source);

        assertTrue(result.diagnostics().isEmpty(), result.diagnostics()::toString);
        assertEquals(source, JavaParserTestSupport.syntaxText(result.tree()));
    }

    @Test
    void incrementalParseReusesTailForInTypeEdit() {
        String oldSource = """
            package demo;
            import java.util.List;
            class A {
                int x = 1;
            }
            class B {}
            """;
        String newSource = """
            package demo;
            import java.util.List;
            class A {
                int x = 12;
            }
            class B {}
            """;

        int editStart = oldSource.indexOf("1;");
        var edit = new JavaSyntaxParser.TextEdit(editStart, 1, "12");
        SyntaxTree previousTree = JavaSyntaxParser.parse(oldSource);

        JavaSyntaxParser.IncrementalParseResult result = JavaSyntaxParser.parseIncremental(previousTree, oldSource,
            newSource, edit);

        assertFalse(result.fullReparse());
        assertEquals(previousTree.documentId(), result.tree().documentId());
        assertEquals(previousTree.documentUri(), result.tree().documentUri());
        assertEquals(previousTree.documentVersion().next(), result.tree().documentVersion());
        assertTrue(result.reusePlan().candidates().size() > 0);
        assertEquals(newSource, JavaParserTestSupport.syntaxText(result.tree()));
    }

    @Test
    void incrementalParseFallsBackForImportEdit() {
        String oldSource = """
            package demo;
            import java.util.List;
            class A {}
            """;
        String newSource = """
            package demo;
            import java.util.ArrayList;
            class A {}
            """;

        int editStart = oldSource.indexOf("List");
        var edit = new JavaSyntaxParser.TextEdit(editStart, 4, "ArrayList");
        SyntaxTree previousTree = JavaSyntaxParser.parse(oldSource);

        JavaSyntaxParser.IncrementalParseResult result = JavaSyntaxParser.parseIncremental(previousTree, oldSource,
            newSource, edit);

        assertTrue(result.fullReparse());
        assertEquals(previousTree.documentId(), result.tree().documentId());
        assertEquals(previousTree.documentUri(), result.tree().documentUri());
        assertEquals(previousTree.documentVersion().next(), result.tree().documentVersion());
        assertEquals(newSource, JavaParserTestSupport.syntaxText(result.tree()));
    }

    @Test
    void incrementalParseAcceptsOnlyALaterCallerSuppliedVersion() {
        String oldSource = "class A { int value = 1; }";
        String newSource = "class A { int value = 2; }";
        var previousVersion = new DocumentVersion(10);
        SyntaxTree previousTree = JavaSyntaxParser.parse(
            DocumentId.create(),
            DocumentUri.virtual("memory", "tests/A.java"),
            previousVersion,
            oldSource);
        var edit = new JavaSyntaxParser.TextEdit(oldSource.indexOf("1"), 1, "2");

        JavaSyntaxParser.IncrementalParseResult result = JavaSyntaxParser.parseIncremental(
            previousTree,
            new DocumentVersion(15),
            oldSource,
            newSource,
            edit);

        assertEquals(new DocumentVersion(15), result.tree().documentVersion());
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaSyntaxParser.parseIncremental(
                previousTree,
                previousVersion,
                oldSource,
                newSource,
                edit));
    }

    @Test
    void incrementalParseUsesSnapshotContentIdentityAndVersionAtomically() {
        String oldSource = "class A { int value = 1; }";
        String newSource = "class A { int value = 20; }";
        var previousSnapshot = new TextDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("memory", "tests/A.java"),
            new DocumentVersion(5),
            "java",
            oldSource,
            StandardCharsets.UTF_8);
        SyntaxTree previousTree = JavaSyntaxParser.parse(previousSnapshot);
        var newSnapshot = new TextDocumentSnapshot(
            previousSnapshot.id(),
            DocumentUri.virtual("memory", "renamed/A.java"),
            new DocumentVersion(9),
            "java",
            newSource,
            StandardCharsets.UTF_16);
        var edit = new JavaSyntaxParser.TextEdit(oldSource.indexOf("1"), 1, "20");

        JavaSyntaxParser.IncrementalParseResult result = JavaSyntaxParser.parseIncremental(previousTree, newSnapshot,
            edit);

        assertSame(newSnapshot, result.tree().documentSnapshot());
        assertEquals(newSource, JavaParserTestSupport.syntaxText(result.tree()));

        var wrongIdentity = new TextDocumentSnapshot(
            DocumentId.create(),
            newSnapshot.uri(),
            newSnapshot.version(),
            "java",
            newSource,
            StandardCharsets.UTF_8);
        var staleVersion = new TextDocumentSnapshot(
            previousSnapshot.id(),
            newSnapshot.uri(),
            previousSnapshot.version(),
            "java",
            newSource,
            StandardCharsets.UTF_8);
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaSyntaxParser.parseIncremental(previousTree, wrongIdentity, edit));
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaSyntaxParser.parseIncremental(previousTree, staleVersion, edit));
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaSyntaxParser.parseIncremental(previousTree, "not the old source", newSource, edit));
    }

    private static long countKind(List<String> kinds, String kindId) {
        return kinds.stream().filter(kindId::equals).count();
    }
}
