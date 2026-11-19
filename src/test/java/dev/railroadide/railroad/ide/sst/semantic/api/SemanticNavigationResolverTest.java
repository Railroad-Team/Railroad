package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenNode;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenToken;
import dev.railroadide.railroad.ide.sst.syntax.internal.SyntaxInternalFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.IntStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SemanticNavigationResolverTest {
    @Test
    public void deepestResolvedNodeTakesPrecedenceOverItsAncestors() {
        SyntaxTree tree = nestedTree();
        SyntaxNode branch = tree.root().children().get(1);
        SyntaxNode nested = branch.children().getFirst();
        SyntaxNode leaf = nested.children().getFirst();
        Symbol expected = symbol("leaf");
        SemanticModel model = builder(tree).resolve(tree.root(), symbol("root"))
            .resolve(branch, symbol("branch")).resolve(nested, symbol("nested"))
            .resolve(leaf, expected).build();

        assertSame(expected, SemanticNavigationResolver.symbolAt(model, 3).orElseThrow());
    }

    @Test
    public void unresolvedLeafDoesNotInheritAResolvedAncestor() {
        SyntaxTree tree = nestedTree();
        SyntaxNode branch = tree.root().children().get(1);
        Symbol expected = symbol("branch");
        SemanticModel model = builder(tree).resolve(tree.root(), symbol("root"))
            .resolve(branch, expected).build();

        assertTrue(SemanticNavigationResolver.symbolAt(model, 3).isEmpty());
    }

    @Test
    public void adjacentChildrenUseInclusiveStartsAndExclusiveEnds() {
        SyntaxTree tree = nestedTree();
        List<SyntaxNode> children = tree.root().children();
        Symbol first = symbol("first");
        Symbol middle = symbol("middle");
        Symbol last = symbol("last");
        SemanticModel model = builder(tree).resolve(children.get(0), first)
            .resolve(children.get(1).children().getFirst().children().getFirst(), middle)
            .resolve(children.get(2), last).build();

        assertSame(first, SemanticNavigationResolver.symbolAt(model, 0).orElseThrow());
        assertSame(first, SemanticNavigationResolver.symbolAt(model, 1).orElseThrow());
        assertSame(middle, SemanticNavigationResolver.symbolAt(model, 2).orElseThrow());
        assertSame(middle, SemanticNavigationResolver.symbolAt(model, 4).orElseThrow());
        assertSame(last, SemanticNavigationResolver.symbolAt(model, 5).orElseThrow());
        assertSame(last, SemanticNavigationResolver.symbolAt(model, 6).orElseThrow());
    }

    @Test
    public void offsetsOutsideRootReturnEmptyEvenWhenRootHasASymbol() {
        SyntaxTree tree = nestedTree();
        SemanticModel model = builder(tree).resolve(tree.root(), symbol("root")).build();

        for (int offset : new int[]{Integer.MIN_VALUE, -1, tree.root().end(), Integer.MAX_VALUE}) {
            assertTrue(SemanticNavigationResolver.symbolAt(model, offset).isEmpty(), "Offset: " + offset);
        }
    }

    @Test
    public void unresolvedPathDoesNotUseSymbolsFromSiblings() {
        SyntaxTree tree = nestedTree();
        SemanticModel model = builder(tree).resolve(tree.root().children().getFirst(), symbol("sibling")).build();

        assertTrue(SemanticNavigationResolver.symbolAt(model, 3).isEmpty());
    }

    @Test
    public void declarationAssociationsAreNotTreatedAsResolvedReferences() {
        SyntaxTree tree = nestedTree();
        SyntaxNode branch = tree.root().children().get(1);
        SyntaxNode leaf = branch.children().getFirst().children().getFirst();
        SemanticModel model = builder(tree).declare(leaf, symbol("leaf"))
            .declare(branch, symbol("branch")).declare(tree.root(), symbol("root")).build();

        assertTrue(SemanticNavigationResolver.symbolAt(model, 3).isEmpty());
    }

    @Test
    public void emptyTreeHasNoNavigableOffset() {
        SyntaxTree tree = SyntaxInternalFactory.treeFromRootChildren(List.of());
        SemanticModel model = builder(tree).resolve(tree.root(), symbol("root")).build();

        assertTrue(SemanticNavigationResolver.symbolAt(model, -1).isEmpty());
        assertTrue(SemanticNavigationResolver.symbolAt(model, 0).isEmpty());
        assertTrue(SemanticNavigationResolver.symbolAt(model, 1).isEmpty());
    }

    @Test
    public void zeroWidthChildDoesNotCaptureFollowingToken() {
        SyntaxTree tree = SyntaxInternalFactory.treeFromRootChildren(List.of(token(""), token("name")));
        Symbol expected = symbol("name");
        SemanticModel model = builder(tree).resolve(tree.root().children().get(0), symbol("missing"))
            .resolve(tree.root().children().get(1), expected).build();

        assertSame(expected, SemanticNavigationResolver.symbolAt(model, 0).orElseThrow());
    }

    @Test
    public void repeatedGreenTokensRetainDistinctNavigationTargets() {
        GreenToken shared = token("same");
        SyntaxTree tree = SyntaxInternalFactory.treeFromRootChildren(List.of(shared, shared));
        Symbol first = symbol("first");
        Symbol second = symbol("second");
        SemanticModel model = builder(tree).resolve(tree.root().children().get(0), first)
            .resolve(tree.root().children().get(1), second).build();

        for (int offset = 0; offset < 8; offset++) {
            assertSame(offset < 4 ? first : second, SemanticNavigationResolver.symbolAt(model, offset).orElseThrow());
        }
    }

    @Test
    public void navigationUsesUtf16OffsetsIncludingSurrogatePairsAndCrLf() {
        String source = "// \uD83D\uDE80\r\nclass Example { int value; int run() { return value; } }";
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);

        assertReference(model, source.lastIndexOf("value"), "value", JavaSymbolKind.FIELD);
        assertNoNavigation(model, 0, source.indexOf("class"));
        assertTrue(SemanticNavigationResolver.symbolAt(model, source.length()).isEmpty());
    }

    @Test
    public void builderChangesDoNotLeakBetweenModelSnapshots() {
        SyntaxTree tree = nestedTree();
        SyntaxNode leaf = tree.root().children().get(1).children().getFirst().children().getFirst();
        SemanticModel.Builder builder = builder(tree);
        SemanticModel unresolved = builder.build();
        Symbol first = symbol("first");
        SemanticModel original = builder.resolve(leaf, first).build();
        Symbol second = symbol("second");
        SemanticModel updated = builder.resolve(leaf, second).build();

        for (int iteration = 0; iteration < 3; iteration++) {
            assertSame(second, SemanticNavigationResolver.symbolAt(updated, 3).orElseThrow());
            assertSame(first, SemanticNavigationResolver.symbolAt(original, 3).orElseThrow());
            assertTrue(SemanticNavigationResolver.symbolAt(unresolved, 3).isEmpty());
        }
    }

    @Test
    public void symbolsFromAnotherTreeWithIdenticalTextAreNotUsed() {
        SyntaxTree first = nestedTree();
        SyntaxTree second = nestedTree();
        SemanticModel model = builder(second).resolve(first.root(), symbol("foreign"))
            .resolve(first.root().children().get(1), symbol("foreignBranch")).build();

        for (int offset = 0; offset < second.root().end(); offset++) {
            assertTrue(SemanticNavigationResolver.symbolAt(model, offset).isEmpty());
        }
    }

    @Test
    public void sharedResolverSupportsConcurrentLookupsAcrossDifferentModels() throws Exception {
        SyntaxTree tree = nestedTree();
        Symbol first = symbol("first");
        Symbol second = symbol("second");
        SyntaxNode leaf = tree.root().children().get(1).children().getFirst().children().getFirst();
        List<SemanticModel> models = List.of(builder(tree).resolve(leaf, first).build(),
            builder(tree).resolve(leaf, second).build(), builder(tree).build());
        List<Optional<Symbol>> expected = List.of(Optional.of(first), Optional.of(second), Optional.empty());
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (int iteration = 0; iteration < 100; iteration++) {
                        for (int index = 0; index < models.size(); index++) {
                            for (int offset = 0; offset < tree.root().end(); offset++) {
                                assertEquals(offset >= 2 && offset < 5 ? expected.get(index) : Optional.empty(),
                                    SemanticNavigationResolver.symbolAt(models.get(index), offset));
                            }
                        }
                    }
                    return null;
                }));
            }
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            for (Future<?> task : tasks) {
                task.get(20, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    public void javaFieldParameterAndLocalReferencesNavigateToTheirDeclarations() {
        String source = "class Example { int field; int run(int param) { int local = param; return field + local; } }";
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);

        assertReference(model, source.lastIndexOf("field"), "field", JavaSymbolKind.FIELD);
        assertReference(model, source.lastIndexOf("param"), "param", JavaSymbolKind.PARAMETER);
        assertReference(model, source.lastIndexOf("local"), "local", JavaSymbolKind.LOCAL_VARIABLE);
    }

    @Test
    public void javaShadowedNamesNavigateToTheCorrectDeclaration() {
        String source = "class Example { int value; int run(int value) { return this.value + value; } }";
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);

        assertReference(model, source.indexOf("this.value") + 5, "value", JavaSymbolKind.FIELD);
        assertReference(model, source.lastIndexOf("value"), "value", JavaSymbolKind.PARAMETER);
    }

    @Test
    public void javaInvocationNameAndArgumentHaveDifferentNavigationTargets() {
        String source = "class Example { int echo(int input) { return input; } int run(int arg) { return echo(arg); } }";
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);

        assertReference(model, source.lastIndexOf("echo"), "echo", JavaSymbolKind.METHOD);
        assertReference(model, source.lastIndexOf("arg"), "arg", JavaSymbolKind.PARAMETER);
    }

    @Test
    public void unresolvedJavaNameDoesNotNavigateToANearbyResolvedReference() {
        String source = "class Example { int known; int run() { return known + missing; } }";
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);

        assertReference(model, source.lastIndexOf("known"), "known", JavaSymbolKind.FIELD);
        for (int offset = source.indexOf("missing"); offset < source.indexOf("missing") + 7; offset++) {
            assertTrue(SemanticNavigationResolver.symbolAt(model, offset).isEmpty(), "Offset: " + offset);
        }
    }

    @Test
    public void qualifiedInvocationReceiverAndMethodNameNavigateToDifferentDeclarations() {
        String source = userInvocationSource("user.getName()");
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);

        assertReference(model, source.lastIndexOf("user"), "user", JavaSymbolKind.PARAMETER);
        assertReference(model, source.lastIndexOf("getName"), "getName", JavaSymbolKind.METHOD);
    }

    @Test
    public void invocationSymbolDoesNotApplyToTokensWithoutTheirOwnAssociations() {
        String source = userInvocationSource("user.getName()");
        SyntaxTree tree = JavaSyntaxParser.parse(source);
        SyntaxNode invocation = descendants(tree.root())
            .filter(node -> node.kind().equals(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION))
            .findFirst().orElseThrow();
        Symbol method = new SimpleSymbol(JavaSymbolKind.METHOD, "getName", null, null);
        SemanticModel model = builder(tree).resolve(invocation, method).build();

        assertNoNavigation(model, invocation.start(), invocation.end());
    }

    @Test
    public void qualifiedInvocationDotHasNoNavigationTarget() {
        String source = userInvocationSource("user.getName()");
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);
        int dot = source.lastIndexOf("user.") + "user".length();

        assertNoNavigation(model, dot, dot + 1);
    }

    @Test
    public void invocationParenthesesHaveNoNavigationTarget() {
        String source = userInvocationSource("user.getName()");
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);
        int openingParenthesis = source.lastIndexOf("getName()") + "getName".length();

        assertNoNavigation(model, openingParenthesis, openingParenthesis + 2);
    }

    @Test
    public void whitespaceWithinInvocationHasNoNavigationTarget() {
        String expression = "user \t. \n getName \t( \n )";
        String source = userInvocationSource(expression);
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);
        int expressionStart = source.indexOf(expression);
        assertReference(model, source.lastIndexOf("getName"), "getName", JavaSymbolKind.METHOD);

        assertAll(
            IntStream.range(0, expression.length()).filter(index -> Character.isWhitespace(expression.charAt(index)))
                .mapToObj(index -> (Executable) () -> assertNoNavigation(model, expressionStart + index,
                    expressionStart + index + 1)));
    }

    @Test
    public void commentsWithinInvocationHaveNoNavigationTarget() {
        String expression = "user/* receiver */./* selector */getName/* arguments */(/* inside */)";
        String source = userInvocationSource(expression);
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);
        assertReference(model, source.lastIndexOf("getName"), "getName", JavaSymbolKind.METHOD);
        List<Executable> checks = new ArrayList<>();
        for (String comment : List.of("/* receiver */", "/* selector */", "/* arguments */", "/* inside */")) {
            int start = source.indexOf(comment);
            checks.add(() -> assertNoNavigation(model, start, start + comment.length()));
        }

        assertAll(checks);
    }

    @Test
    public void literalArgumentDoesNotInheritTheInvokedMethodSymbol() {
        String source = "class Example { void echo(int input) {} void run() { echo(123); } }";
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);
        assertReference(model, source.lastIndexOf("echo"), "echo", JavaSymbolKind.METHOD);
        int literal = source.indexOf("123");

        assertNoNavigation(model, literal, literal + 3);
    }

    @Test
    public void unresolvedArgumentDoesNotInheritTheInvokedMethodSymbol() {
        String source = "class Example { void echo(int input) {} void run() { echo(missing); } }";
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);
        assertReference(model, source.lastIndexOf("echo"), "echo", JavaSymbolKind.METHOD);
        int argument = source.indexOf("missing");

        assertNoNavigation(model, argument, argument + "missing".length());
    }

    @Test
    public void qualifiedFieldDotHasNoNavigationTarget() {
        String source = "class User { int value; int run(User user) { return user.value; } }";
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);
        assertReference(model, source.lastIndexOf("user"), "user", JavaSymbolKind.PARAMETER);
        assertReference(model, source.lastIndexOf("value"), "value", JavaSymbolKind.FIELD);
        int dot = source.lastIndexOf("user.") + "user".length();

        assertNoNavigation(model, dot, dot + 1);
    }

    @Test
    public void declarationLocationReturnsExactRangeForANestedDeclaration() {
        SyntaxTree tree = nestedTree();
        SyntaxNode declaration = tree.root().children().get(1).children().getFirst();
        SemanticModel model = builder(tree).build();
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "nested", null, declaration);

        Location.TextLocation location = SemanticNavigationResolver.declarationLocation(model, symbol).orElseThrow();

        assertLocation(location, tree.documentSnapshot(), 2, 5, 0, 2);
        assertEquals("bbb", location.range().content(tree.documentSnapshot()).toString());
    }

    @Test
    public void declarationLocationAcceptsTheRootItself() {
        SyntaxTree tree = nestedTree();
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "root", null, tree.root());

        Location.TextLocation location = SemanticNavigationResolver.declarationLocation(builder(tree).build(), symbol)
            .orElseThrow();

        assertLocation(location, tree.documentSnapshot(), 0, 7, 0, 0);
    }

    @Test
    public void declarationLocationPreservesSnapshotIdentityVersionAndUtf16Coordinates() {
        String prefix = "header\r\n\uD83D\uDE80 ";
        TextDocumentSnapshot snapshot = snapshot(prefix + "name", new DocumentVersion(17));
        SyntaxTree tree = SyntaxInternalFactory.treeFromRootChildren(snapshot, List.of(token(prefix), token("name")));
        SyntaxNode declaration = tree.root().children().get(1);
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "name", null, declaration);

        Location.TextLocation location = SemanticNavigationResolver.declarationLocation(builder(tree).build(), symbol)
            .orElseThrow();

        assertLocation(location, snapshot, prefix.length(), snapshot.text().length(), 1, 3);
    }

    @Test
    public void declarationLocationIsEmptyForASymbolWithoutSourceSyntax() {
        SemanticModel model = builder(nestedTree()).build();

        assertTrue(SemanticNavigationResolver.declarationLocation(model, symbol("binary")).isEmpty());
    }

    @Test
    public void declarationLocationRejectsADeclarationFromAnotherDocument() {
        SyntaxTree local = nestedTree();
        SyntaxTree foreign = nestedTree();
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "foreign", null,
            foreign.root().children().get(1).children().getFirst());

        assertTrue(SemanticNavigationResolver.declarationLocation(builder(local).build(), symbol).isEmpty());
    }

    @Test
    public void declarationLocationRequiresTreeIdentityEvenWithTheSameSnapshotAndGreenNodes() {
        SyntaxTree original = nestedTree();
        SyntaxTree rewrapped = SyntaxInternalFactory.treeFromGreenRoot(original.documentSnapshot(),
            SyntaxInternalFactory.greenRoot(original));
        SemanticModel model = builder(rewrapped).build();

        for (SyntaxNode declaration : List.of(original.root(), original.root().children().get(1))) {
            Symbol symbol = new SimpleSymbol(() -> "test:symbol", "old", null, declaration);
            assertTrue(SemanticNavigationResolver.declarationLocation(model, symbol).isEmpty());
        }
    }

    @Test
    public void declarationLocationRejectsNodesRetainedFromAnEarlierDocumentVersion() {
        SyntaxTree original = nestedTree();
        TextDocumentSnapshot old = original.documentSnapshot();
        var updated = new TextDocumentSnapshot(old.id(), old.uri(), old.version().next(), old.languageId(),
            old.text(), StandardCharsets.UTF_8);
        SyntaxTree reparsed = SyntaxInternalFactory.treeFromGreenRoot(updated,
            SyntaxInternalFactory.greenRoot(original));
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "old", null, original.root().children().getFirst());

        assertTrue(SemanticNavigationResolver.declarationLocation(builder(reparsed).build(), symbol).isEmpty());
    }

    @Test
    public void declarationLocationAllowsAnEmptyDeclarationAtEndOfFile() {
        SyntaxTree tree = SyntaxInternalFactory.treeFromRootChildren(List.of(token("name"), token("")));
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "missing", null, tree.root().children().get(1));

        Location.TextLocation location = SemanticNavigationResolver.declarationLocation(builder(tree).build(), symbol)
            .orElseThrow();

        assertLocation(location, tree.documentSnapshot(), 4, 4, 0, 4);
    }

    @Test
    public void declarationLocationAllowsAnEmptyTreeRoot() {
        SyntaxTree tree = SyntaxInternalFactory.treeFromRootChildren(List.of());
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "empty", null, tree.root());

        Location.TextLocation location = SemanticNavigationResolver.declarationLocation(builder(tree).build(), symbol)
            .orElseThrow();

        assertLocation(location, tree.documentSnapshot(), 0, 0, 0, 0);
    }

    @Test
    public void declarationLocationRejectsNullInputs() {
        SyntaxTree tree = nestedTree();
        SemanticModel model = builder(tree).build();
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "root", null, tree.root());

        assertThrows(NullPointerException.class, () -> SemanticNavigationResolver.declarationLocation(model, null));
        assertThrows(NullPointerException.class, () -> SemanticNavigationResolver.declarationLocation(null, symbol));
    }

    @Test
    public void definitionAtReturnsTheDeclarationRangeRatherThanTheReferenceRange() {
        SyntaxTree tree = nestedTree();
        SyntaxNode declaration = tree.root().children().getFirst();
        SyntaxNode reference = tree.root().children().get(1).children().getFirst().children().getFirst();
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "target", null, declaration);
        SemanticModel model = builder(tree).resolve(reference, symbol).build();

        for (int offset = reference.start(); offset < reference.end(); offset++) {
            Location.TextLocation location = SemanticNavigationResolver.definitionAt(model, offset).orElseThrow();
            assertLocation(location, tree.documentSnapshot(), 0, 2, 0, 0);
        }
    }

    @Test
    public void definitionAtIsEmptyWhenNoSymbolIsResolved() {
        SyntaxTree tree = nestedTree();
        SemanticModel model = builder(tree).build();

        assertTrue(SemanticNavigationResolver.definitionAt(model, 3).isEmpty());
    }

    @Test
    public void definitionAtIsEmptyForAResolvedSymbolWithoutSourceSyntax() {
        SyntaxTree tree = nestedTree();
        SemanticModel model = builder(tree).resolve(tree.root().children().getFirst(), symbol("binary")).build();

        assertTrue(SemanticNavigationResolver.definitionAt(model, 0).isEmpty());
    }

    @Test
    public void definitionAtDoesNotFallBackToAnOuterDeclarationWhenTheNearestSymbolHasNone() {
        SyntaxTree tree = nestedTree();
        SyntaxNode branch = tree.root().children().get(1);
        SyntaxNode leaf = branch.children().getFirst().children().getFirst();
        Symbol outer = new SimpleSymbol(() -> "test:symbol", "outer", null, tree.root().children().getFirst());
        SemanticModel model = builder(tree).resolve(branch, outer).resolve(leaf, symbol("binary")).build();

        assertTrue(SemanticNavigationResolver.definitionAt(model, 3).isEmpty());
    }

    @Test
    public void definitionAtNavigatesAJavaReferenceToItsAnalyzedDeclaration() {
        String source = "class Example { int value; int run() { return value; } }";
        SemanticModel model = JavaSemanticAnalyzer.analyzeFacts(source);
        Symbol field = descendants(model.syntaxTree().root()).map(model::declaredSymbol).flatMap(Optional::stream)
            .filter(symbol -> symbol.kind() == JavaSymbolKind.FIELD && "value".equals(symbol.simpleName()))
            .findFirst().orElseThrow();
        SyntaxNode declaration = field.declaration().orElseThrow();

        Location.TextLocation location = SemanticNavigationResolver.definitionAt(model, source.lastIndexOf("value"))
            .orElseThrow();

        assertLocation(location, model.syntaxTree().documentSnapshot(), declaration.start(), declaration.end(),
            0, declaration.start());
        assertTrue(location.range().end <= source.lastIndexOf("value"));
    }

    @Test
    public void definitionAtIsEmptyWhenTheResolvedDeclarationBelongsToAnotherTree() {
        SyntaxTree tree = nestedTree();
        SyntaxTree foreign = nestedTree();
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "external", null, foreign.root());
        SemanticModel model = builder(tree).resolve(tree.root().children().getFirst(), symbol).build();

        assertTrue(SemanticNavigationResolver.definitionAt(model, 0).isEmpty());
    }

    @Test
    public void definitionAtIsEmptyOutsideTheDocumentAndAtEndOfFile() {
        SyntaxTree tree = nestedTree();
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "root", null, tree.root());
        SemanticModel model = builder(tree).resolve(tree.root(), symbol).build();

        for (int offset : new int[]{Integer.MIN_VALUE, -1, tree.root().end(), Integer.MAX_VALUE}) {
            assertTrue(SemanticNavigationResolver.definitionAt(model, offset).isEmpty(), "Offset: " + offset);
        }
    }

    @Test
    public void definitionAtUsesTheSnapshotVersionAndDeclarationCoordinates() {
        String prefix = "first\r\n  ";
        TextDocumentSnapshot snapshot = snapshot(prefix + "decl ref", new DocumentVersion(23));
        SyntaxTree tree = SyntaxInternalFactory.treeFromRootChildren(snapshot,
            List.of(token(prefix), token("decl"), token(" "), token("ref")));
        Symbol symbol = new SimpleSymbol(() -> "test:symbol", "target", null, tree.root().children().get(1));
        SemanticModel model = builder(tree).resolve(tree.root().children().get(3), symbol).build();

        Location.TextLocation location = SemanticNavigationResolver.definitionAt(model, prefix.length() + 5)
            .orElseThrow();

        assertLocation(location, snapshot, prefix.length(), prefix.length() + 4, 1, 2);
    }

    private static TextDocumentSnapshot snapshot(String text, DocumentVersion version) {
        DocumentId id = DocumentId.create();
        return new TextDocumentSnapshot(id, DocumentUri.inMemory(id), version, "test:language", text,
            StandardCharsets.UTF_8);
    }

    private static void assertLocation(
        Location.TextLocation location,
        TextDocumentSnapshot snapshot,
        int start,
        int end,
        int line,
        int column
    ) {
        assertAll(
            () -> assertEquals(snapshot.id(), location.documentId()),
            () -> assertEquals(snapshot.version(), location.documentVersion()),
            () -> assertEquals(start, location.range().start),
            () -> assertEquals(end, location.range().end),
            () -> assertEquals(line, location.line()),
            () -> assertEquals(column, location.column()),
            () -> assertDoesNotThrow(() -> location.validateAgainst(snapshot)));
    }

    private void assertNoNavigation(SemanticModel model, int start, int end) {
        assertAll(IntStream.range(start, end).mapToObj(offset -> (Executable) () -> {
            Optional<Symbol> result = SemanticNavigationResolver.symbolAt(model, offset);
            assertTrue(result.isEmpty(), () -> "Expected no target at offset " + offset + ", got "
                + result.map(Symbol::simpleName).orElse("none"));
        }));
    }

    private static String userInvocationSource(String expression) {
        return "class User { int getName() { return 0; } void run(User user) { " + expression + "; } }";
    }

    private void assertReference(SemanticModel model, int offset, String name, SymbolKind kind) {
        List<Symbol> declarations = descendants(model.syntaxTree().root()).map(model::declaredSymbol)
            .flatMap(Optional::stream).filter(symbol -> name.equals(symbol.simpleName()) && kind.equals(symbol.kind()))
            .toList();
        assertEquals(1, declarations.size(), "Expected a unique declaration for " + name + " of kind " + kind);
        Symbol expected = declarations.getFirst();
        for (int character = 0; character < name.length(); character++) {
            assertSame(expected, SemanticNavigationResolver.symbolAt(model, offset + character).orElseThrow(),
                "Character " + character + " of " + name);
        }
    }

    private static Stream<SyntaxNode> descendants(SyntaxNode node) {
        return Stream.concat(Stream.of(node),
            node.children().stream().flatMap(SemanticNavigationResolverTest::descendants));
    }

    private static SyntaxTree nestedTree() {
        var nested = new GreenNode(SyntaxKind.of("test:nested"), List.of(token("bbb")));
        var branch = new GreenNode(SyntaxKind.of("test:branch"), List.of(nested));
        return SyntaxInternalFactory.treeFromRootChildren(List.of(token("aa"), branch, token("cc")));
    }

    private static GreenToken token(String text) {
        return new GreenToken(SyntaxKind.of("test:token"), text);
    }

    private static SemanticModel.Builder builder(SyntaxTree tree) {
        return SemanticModel.builder(tree, Scope.root());
    }

    private static Symbol symbol(String name) {
        return new SimpleSymbol(() -> "test:symbol", name, null, null);
    }
}
