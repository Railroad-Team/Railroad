package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.BackingKind;
import dev.railroadide.railroad.ide.sst.document.api.DocumentOrigin;
import dev.railroadide.railroad.ide.sst.document.api.OriginReference;
import dev.railroadide.railroad.ide.sst.document.api.ProvenanceKind;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

public class JavaSyntaxParserTest {

    @Test
    public void carriesCallerSuppliedDocumentIdentity() {
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
    public void parsesAndRetainsTheExactTextSnapshot() {
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
    public void roundTripsSourceTextFromSyntaxTree() {
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
    public void parsesSeparatorsBetweenDeclaredAndReferencedTryResources() {
        String source = """
            class Example {
                void run(Resource existing) throws Exception {
                    try (Resource first = open();
                         Resource second = open(); existing;) {}
                }
            }
            """;
        var result = JavaSyntaxParser.parseWithDiagnostics(source);
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics()::toString);
        assertEquals(source, JavaParserTestSupport.syntaxText(result.tree()));
        assertFalse(JavaSyntaxParser.parseWithDiagnostics(source.replace("open();", "open()"))
            .diagnostics().isEmpty());
    }

    @Test
    public void recognizesNonSealedOnlyInTypeDeclarationPrefixes() {
        String source = """
            sealed interface Root permits Root.Child, Branch {
                @Deprecated n\\u006fn\\u002ds\\u0065aled interface Child extends Root {}
            }
            non-sealed class Branch implements Root {
                int difference(int non, int sealed) { return non-sealed; }
            }
            """;
        var result = JavaSyntaxParser.parseWithDiagnostics(source);
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics()::toString);
        assertEquals(source, JavaParserTestSupport.syntaxText(result.tree()));
        List<SyntaxToken> tokens = JavaParserTestSupport.collectSyntaxTokens(result.tree());
        assertEquals(2, tokens.stream()
            .filter(token -> token.kind().equals(JavaSyntaxKinds.tokenKind(JavaTokenType.NON_SEALED_KEYWORD))).count());
        assertEquals(1, tokens.stream()
            .filter(token -> token.kind().equals(JavaSyntaxKinds.tokenKind(JavaTokenType.MINUS))).count());

        for (String spelling : List.of("non -sealed", "non- sealed", "non/*gap*/-sealed", "non-sealedExtra")) {
            assertFalse(JavaSyntaxParser.parseWithDiagnostics(source.replace("non-sealed class", spelling + " class"))
                .diagnostics().isEmpty(), spelling);
        }
    }

    @Test
    public void parsesAnnotatedReturnTypesAfterAdjacentGenericClosers() {
        String source = """
            class Example {
                <T extends Box<X>> @Nullable T get() { return null; }
                <T extends Box<Box<X>>> @Nullable T nested() { return null; }
            }
            """;
        var checkpoints = new AtomicInteger();
        SyntaxTree tree = JavaSyntaxParser.parse(source,
            () -> assertTrue(checkpoints.incrementAndGet() < 1000, "Parser stalled at a return-type annotation"));

        assertEquals(source, JavaParserTestSupport.syntaxText(tree));
        assertTrue(JavaSyntaxParser.parseWithDiagnostics(source).diagnostics().isEmpty());
    }

    @Test
    public void typeLookaheadPreservesShiftOperatorsBeforeAnnotatedCreation() {
        String source = """
            class Example {
                boolean compare(int left, int right) {
                    return left < right >> new @A Box().value;
                }

                boolean compareUnsigned(int left, int right) {
                    return (left < right >>> new @A Box().value);
                }
            }
            """;
        var checkpoints = new AtomicInteger();
        SyntaxTree tree = JavaSyntaxParser.parse(source,
            () -> assertTrue(checkpoints.incrementAndGet() < 1000, "Parser stalled after type lookahead"));

        assertEquals(source, JavaParserTestSupport.syntaxText(tree));
        var result = JavaSyntaxParser.parseWithDiagnostics(source);
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics()::toString);
        List<SyntaxKind> tokenKinds = JavaParserTestSupport.collectSyntaxTokens(tree).stream()
            .map(token -> token.kind()).toList();
        assertTrue(tokenKinds.contains(JavaSyntaxKinds.tokenKind(JavaTokenType.RIGHT_SHIFT)));
        assertTrue(tokenKinds.contains(JavaSyntaxKinds.tokenKind(JavaTokenType.UNSIGNED_RIGHT_SHIFT)));
    }

    @Test
    public void parsesTypeAnnotationsOnArrayCreationDimensions() {
        String source = """
            class Example {
                int[][] sized = new @A int @B [1] @C [2];
                int[][] mixed = new int[1] @A [];
                int[] initialized = new int @A [] {1};

                int[] make() { return new int @A(value = 1) [1]; }
            }
            """;

        JavaSyntaxAssertions.assertParsesWithoutRecovery(source);
    }

    @Test
    public void parsesReceiverParametersSeparatelyFromFormalParameters() {
        String source = """
            class Example<T> {
                void plain(Example<T> this) {}
                void annotated(@A Example<T> this, int value) {}
                class Inner {
                    Inner(final Example<T> Example.this) {}
                    void nested(Example<T>.Inner this) {}
                }
            }
            """;

        JavaSyntaxAssertions.assertParsesWithoutRecovery(source);
        List<String> kinds = new ArrayList<String>();
        Deque<SyntaxNode> pending = new ArrayDeque<SyntaxNode>(List.of(JavaSyntaxParser.parse(source).root()));
        while (!pending.isEmpty()) {
            SyntaxNode node = pending.pop();
            kinds.add(node.kind().id());
            node.children().forEach(pending::push);
        }

        assertEquals(4, countKind(kinds, JavaSyntaxKinds.RECEIVER_PARAMETER.id()));
        assertEquals(1, countKind(kinds, JavaSyntaxKinds.PARAMETER.id()));

        var misplaced = JavaSyntaxParser.parseWithDiagnostics("class Example { void later(int a, Example this) {} }");
        assertFalse(misplaced.diagnostics().isEmpty(), "Only the first parameter can be a receiver");
    }

    @Test
    public void parsesParenthesizedConditionalCaseConstantsWithoutLooping() {
        String source = """
            class Example {
                static final int one = 0;
                void run(int value) {
                    switch (value) {
                        case (true ? 0 : 1):
                        case (one == 5 ? 1 : 2):
                        case ((3)):
                        case (4):
                    }
                }
            }
            """;

        assertTimeoutPreemptively(Duration.ofSeconds(10),
            () -> JavaSyntaxAssertions.assertParsesWithoutRecovery(source));
    }

    @Test
    public void parsesConcatenationsDeeperThanTheThreadStack() {
        String source = "class Example { String text = " + "\"a\" + ".repeat(32_000) + "\"a\"; }\n";

        var result = JavaSyntaxParser.parseWithDiagnostics(source);

        assertTrue(result.diagnostics().isEmpty(), result.diagnostics()::toString);
        assertEquals(source, JavaParserTestSupport.syntaxText(result.tree()));
    }

    @Test
    public void includesExpectedTopLevelStructureKinds() {
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
    public void parseWithDiagnosticsReportsRecoveryArtifacts() {
        String source = "class Broken { void run( { int x = ; }";
        JavaSyntaxParser.ParseResult result = JavaSyntaxParser.parseWithDiagnostics(source);

        assertFalse(result.diagnostics().isEmpty());
        assertEquals(source, JavaParserTestSupport.syntaxText(result.tree()));
    }

    @Test
    public void nestedGenericClosersDoNotConsumeFollowingImplementedType() {
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
    public void incrementalParseReusesTailForInTypeEdit() {
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
    public void incrementalParseFallsBackForImportEdit() {
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
    public void incrementalParseAcceptsOnlyALaterCallerSuppliedVersion() {
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
    public void incrementalParseUsesSnapshotContentIdentityAndVersionAtomically() {
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

    @Test
    public void incrementalCompatibilityOverloadsPreserveOrigin() {
        String oldSource = "class A { int value = 1; }";
        String newSource = "class A { int value = 20; }";
        var origin = new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.DECOMPILED,
            List.of(new OriginReference(DocumentUri.parse("jar:file:///library.jar!/A.class"))));
        var snapshot = new TextDocumentSnapshot(DocumentId.create(), DocumentUri.virtual("decompiled", "A.java"),
            new DocumentVersion(5), "java", origin, oldSource, StandardCharsets.UTF_16);
        SyntaxTree tree = JavaSyntaxParser.parse(snapshot);
        var edit = new JavaSyntaxParser.TextEdit(oldSource.indexOf("1"), 1, "20");

        TextDocumentSnapshot next = JavaSyntaxParser.parseIncremental(tree, oldSource, newSource, edit).tree()
            .documentSnapshot();
        TextDocumentSnapshot versioned = JavaSyntaxParser
            .parseIncremental(tree, new DocumentVersion(9), oldSource, newSource, edit)
            .tree().documentSnapshot();

        assertSame(origin, next.origin());
        assertSame(origin, versioned.origin());
        assertEquals(snapshot.encoding(), next.encoding());
        assertEquals(snapshot.version().next(), next.version());
        assertEquals(new DocumentVersion(9), versioned.version());
    }

    private static long countKind(List<String> kinds, String kindId) {
        return kinds.stream().filter(kindId::equals).count();
    }
}
