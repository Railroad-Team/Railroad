package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaSyntaxParserTest {

    @Test
    void carriesCallerSuppliedDocumentIdentity() {
        DocumentId documentId = DocumentId.create();

        SyntaxTree tree = JavaSyntaxParser.parse(documentId, "class Identified {}");

        assertEquals(documentId, tree.documentId());
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
        JavaSyntaxParser.TextEdit edit = new JavaSyntaxParser.TextEdit(editStart, 1, "12");
        SyntaxTree previousTree = JavaSyntaxParser.parse(oldSource);

        JavaSyntaxParser.IncrementalParseResult result =
                JavaSyntaxParser.parseIncremental(previousTree, oldSource, newSource, edit);

        assertFalse(result.fullReparse());
        assertEquals(previousTree.documentId(), result.tree().documentId());
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
        JavaSyntaxParser.TextEdit edit = new JavaSyntaxParser.TextEdit(editStart, 4, "ArrayList");
        SyntaxTree previousTree = JavaSyntaxParser.parse(oldSource);

        JavaSyntaxParser.IncrementalParseResult result =
                JavaSyntaxParser.parseIncremental(previousTree, oldSource, newSource, edit);

        assertTrue(result.fullReparse());
        assertEquals(previousTree.documentId(), result.tree().documentId());
        assertEquals(newSource, JavaParserTestSupport.syntaxText(result.tree()));
    }

    private static long countKind(List<String> kinds, String kindId) {
        return kinds.stream().filter(kindId::equals).count();
    }
}
