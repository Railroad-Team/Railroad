package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SemanticPublicationTest {
    @Test
    void publishingFreezesRetainedScopesAndPreservesLookupOrdering() {
        Scope root = Scope.root();
        Scope child = root.child();
        var nested = new Scope(child);
        Symbol first = symbol("run");
        Symbol second = symbol("run");
        Symbol local = symbol("run");
        root.declare(first);
        root.declare(symbol("other"));
        root.declare(second);
        child.declare(local);

        var model = SemanticModel.builder(JavaSyntaxParser.parse(""), root).build();

        assertSame(root, model.rootScope());
        assertSame(root, child.parent().orElseThrow());
        assertSame(child, nested.parent().orElseThrow());
        assertEquals(List.of(child), root.children());
        assertEquals(List.of(first, second), root.lookupLocal("run"));
        assertEquals(List.of(local), nested.lookupNearest("run"));
        assertEquals(List.of(local, first, second), nested.lookupAll("run"));
        assertEquals(List.of("run", "other"), new ArrayList<>(root.snapshotDeclarations().keySet()));
        assertTrue(nested.lookupNearest("missing").isEmpty());
        for (Scope scope : List.of(root, child, nested)) {
            assertThrows(IllegalStateException.class, () -> scope.declare(symbol("late")));
            assertThrows(IllegalStateException.class, scope::child);
            assertThrows(IllegalStateException.class, () -> new Scope(scope));
            assertThrows(UnsupportedOperationException.class, () -> scope.children().clear());
            assertThrows(UnsupportedOperationException.class, () -> scope.snapshotDeclarations().clear());
            assertSame(scope, scope.freeze());
        }
        assertThrows(UnsupportedOperationException.class, () -> root.lookupLocal("run").clear());
        assertThrows(UnsupportedOperationException.class,
            () -> root.snapshotDeclarations().get("run").add(local));
    }

    @Test
    void publishingAChildAlsoFreezesItsAncestorsAndSiblings() {
        Scope root = Scope.root();
        Scope child = root.child();
        Scope sibling = root.child();
        SemanticModel.builder(JavaSyntaxParser.parse(""), child).build();
        for (Scope scope : List.of(root, child, sibling)) {
            assertThrows(IllegalStateException.class, () -> scope.declare(symbol("late")));
        }
    }

    @Test
    void builderReuseAndAdditionalDiagnosticInputsCannotChangePublishedModels() {
        SyntaxTree tree = JavaSyntaxParser.parse("class Example {}");
        var builder = SemanticModel.builder(tree, Scope.root());
        var first = builder.build();
        var diagnostic = new SemanticDiagnostic(SemanticDiagnostic.Severity.INFO, "test", "message", 0, 1, tree.root());
        Symbol declaration = symbol("Example");
        builder.declare(tree.root(), declaration).resolve(tree.root(), declaration)
            .type(tree.root(), new Type.PrimitiveType("int")).diagnostic(diagnostic);
        var second = builder.build();
        assertTrue(first.declaredSymbol(tree.root()).isEmpty());
        assertTrue(first.resolvedSymbol(tree.root()).isEmpty());
        assertTrue(first.inferredType(tree.root()).isEmpty());
        assertTrue(first.diagnostics().isEmpty());
        assertSame(declaration, second.declaredSymbol(tree.root()).orElseThrow());
        var additional = new ArrayList<>(List.of(diagnostic));
        var augmented = first.withAdditionalDiagnostics(additional);
        additional.clear();
        assertEquals(List.of(diagnostic), augmented.diagnostics());
        assertThrows(UnsupportedOperationException.class, () -> augmented.diagnostics().clear());
        assertSame(first.rootScope(), augmented.rootScope());
        var arguments = new ArrayList<Type>(List.of(new Type.PrimitiveType("int")));
        var type = new Type.DeclaredType("Box", arguments);
        arguments.clear();
        assertEquals(1, type.typeArguments().size());
        assertThrows(UnsupportedOperationException.class, () -> type.typeArguments().clear());
    }

    @Test
    void modelAssociationsKeepIdentityEvenForValueEqualProviderNodes() {
        SyntaxNode first = new EqualNode(SyntaxKind.ROOT);
        SyntaxNode second = new EqualNode(SyntaxKind.ROOT);
        assertEquals(first, second);
        assertNotSame(first, second);
        Symbol a = symbol("a");
        Symbol b = symbol("b");
        var model = SemanticModel.builder(new SyntaxTree(first), Scope.root())
            .declare(first, a).declare(second, b)
            .resolve(first, b).resolve(second, a)
            .type(first, new Type.PrimitiveType("int"))
            .type(second, new Type.PrimitiveType("long"))
            .build();
        assertSame(a, model.declaredSymbol(first).orElseThrow());
        assertSame(b, model.declaredSymbol(second).orElseThrow());
        assertSame(b, model.resolvedSymbol(first).orElseThrow());
        assertSame(a, model.resolvedSymbol(second).orElseThrow());
        assertEquals("int", model.inferredType(first).orElseThrow().displayName());
        assertEquals("long", model.inferredType(second).orElseThrow().displayName());
        assertTrue(model.declaredSymbol(new EqualNode(SyntaxKind.ROOT)).isEmpty());
    }

    @Test
    void javaAnalysisPublishesFrozenScopesAndConsistentConcurrentResults() throws Exception {
        var model = JavaSemanticAnalyzer.analyzeFacts("class Example { int x; int run(int p) { return p + x; } }");
        var scopes = new ArrayList<Scope>();
        var pending = new ArrayDeque<Scope>();
        pending.add(model.rootScope());
        while (!pending.isEmpty()) {
            Scope scope = pending.removeFirst();
            scopes.add(scope);
            pending.addAll(scope.children());
        }
        assertTrue(scopes.size() >= 3, "Expected nested type/method scopes");
        for (Scope scope : scopes) {
            assertThrows(IllegalStateException.class, () -> scope.declare(symbol("late")));
        }
        var declarations = scopes.stream().map(Scope::snapshotDeclarations).toList();
        var nodes = new ArrayList<SyntaxNode>();
        collect(model.syntaxTree().root(), nodes);
        var symbols = nodes.stream().map(model::declaredSymbol).toList();
        var resolved = nodes.stream().map(model::resolvedSymbol).toList();
        var types = nodes.stream().map(model::inferredType).toList();
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (int read = 0; read < 20; read++) {
                        assertEquals(declarations, scopes.stream().map(Scope::snapshotDeclarations).toList());
                        assertEquals(symbols, nodes.stream().map(model::declaredSymbol).toList());
                        assertEquals(resolved, nodes.stream().map(model::resolvedSymbol).toList());
                        assertEquals(types, nodes.stream().map(model::inferredType).toList());
                        assertEquals("class Example { int x; int run(int p) { return p + x; } }",
                            model.syntaxTree().documentSnapshot().text());
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

    private static void collect(SyntaxNode node, List<SyntaxNode> nodes) {
        nodes.add(node);
        for (SyntaxNode child : node.children()) {
            collect(child, nodes);
        }
    }

    private static Symbol symbol(String name) {
        return new SimpleSymbol(SymbolKind.METHOD, name, null, null);
    }

    private record EqualNode(SyntaxKind kind) implements SyntaxNode {
        @Override
        public int start() {
            return 0;
        }

        @Override
        public int end() {
            return 0;
        }

        @Override
        public List<SyntaxNode> children() {
            return List.of();
        }

        @Override
        public Optional<SyntaxNode> parent() {
            return Optional.empty();
        }
    }
}
