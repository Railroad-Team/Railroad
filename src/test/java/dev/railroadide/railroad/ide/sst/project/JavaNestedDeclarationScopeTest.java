package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticModel;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.diagnostics.inspections.CoreCallResolutionInspection;
import dev.railroadide.railroad.ide.diagnostics.inspections.CoreAccessibilityInspection;
import dev.railroadide.railroad.ide.diagnostics.inspections.CoreNameResolutionInspection;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleEngine;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class JavaNestedDeclarationScopeTest {
    @Test
    public void protectedOuterMembersRemainAccessibleOnlyThroughPermittedReceivers() {
        String source = """
            class Example extends java.util.ArrayList<String> {
                Object value = new Object() {
                    void run() { removeRange(0, 0); int inherited = modCount; }
                };
                void unrelatedReceiver(java.util.ArrayList<String> other) {
                    other.removeRange(0, 0);
                    int value = other.modCount;
                }
            }
            """;
        var index = JavaJdkSymbolIndex.fromCurrentRuntime();
        var model = JavaSemanticAnalyzer.analyzeFacts(source, index);
        assertResolvedCalls(model, model.syntaxTree().root());
        var context = new JavaRuleContext(Path.of("Example.java"), source, model, index);
        var diagnostics = JavaInspectionRuleEngine.collectDiagnostics(new CoreAccessibilityInspection(), context);
        assertEquals(2, diagnostics.size(), diagnostics.toString());
        assertTrue(diagnostics.stream()
            .allMatch(diagnostic -> diagnostic.startOffset() > source.indexOf("void unrelatedReceiver")));
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.code().equals("SEM_INACCESSIBLE_CALL")));
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.code().equals("SEM_INACCESSIBLE_MEMBER")));
    }

    @Test
    public void anonymousTypesResolveOwnAndLexicallyEnclosingInheritedFields() {
        String source = """
            import java.util.concurrent.*;
            class Parent { protected final CountDownLatch outerSignal = new CountDownLatch(1); }
            class Example extends Parent {
                private static class Base {
                    protected final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
                }
                void create() {
                    var instance = new Base() {
                        void run() { entered.countDown(); release.countDown(); outerSignal.countDown(); }
                    };
                }
            }
            """;
        Path path = Path.of("Example.java").toAbsolutePath().normalize();
        var declarations = new JavaProjectSemanticExtractor().extract(path, source);
        var sources = JavaProjectSemanticIndex.builder().putFile(declarations).build();
        var symbols = new CompositeJavaSymbolIndex(List.of(sources, JavaJdkSymbolIndex.fromCurrentRuntime()));
        var index = new JavaWorkspaceAnalysis(sources, symbols, Map.of(path, source), List.of(),
            Path.of(System.getProperty("java.home")));
        var model = JavaSemanticAnalyzer.analyzeFacts(source, index);
        assertResolvedCalls(model, model.syntaxTree().root());
        var context = new JavaRuleContext(Path.of("Example.java"), source, model, index);
        assertTrue(JavaInspectionRuleEngine.collectDiagnostics(new CoreNameResolutionInspection(), context).isEmpty());
        assertTrue(JavaInspectionRuleEngine.collectDiagnostics(new CoreCallResolutionInspection(), context).isEmpty());
        assertTrue(JavaInspectionRuleEngine.collectDiagnostics(new CoreAccessibilityInspection(), context).isEmpty());
    }

    @Test
    public void anonymousTypesResolveInheritedMethodsOfLexicallyEnclosingTypes() {
        String source = """
            class Parent { protected void inherited(String message) {} }
            class Example extends Parent {
                Object value = new Object() {
                    void run() { inherited("message"); }
                };
            }
            """;
        var index = JavaJdkSymbolIndex.fromCurrentRuntime();
        var model = JavaSemanticAnalyzer.analyzeFacts(source, index);
        assertResolvedCalls(model, model.syntaxTree().root());
        var context = new JavaRuleContext(Path.of("Example.java"), source, model, index);
        assertTrue(JavaInspectionRuleEngine.collectDiagnostics(new CoreCallResolutionInspection(), context).isEmpty());
        assertTrue(JavaInspectionRuleEngine.collectDiagnostics(new CoreAccessibilityInspection(), context).isEmpty());
    }

    @Test
    public void anonymousTypesResolveInheritedAndOuterPrivateCalls() {
        String source = """
            class Parent { protected void inherited() {} }
            class Example {
                private void outer() {}
                Object value = new Parent() {
                    void run() { super.inherited(); inherited(); outer(); }
                };
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeFacts(source, JavaProjectSemanticIndex.empty());
        assertResolvedCalls(model, model.syntaxTree().root());
        var context = new JavaRuleContext(Path.of("Example.java"), source, model, JavaProjectSemanticIndex.empty());
        assertTrue(JavaInspectionRuleEngine.collectDiagnostics(new CoreCallResolutionInspection(), context).isEmpty());
        assertTrue(JavaInspectionRuleEngine.collectDiagnostics(new CoreAccessibilityInspection(), context).isEmpty());
    }

    @Test
    public void anonymousInitializerResolvesGenericInheritedMethods() {
        String source = """
            import java.util.Map;
            import java.util.HashMap;
            class Example {
                Map<String, Integer> values = new HashMap<>() {{ put("key", 1); }};
            }
            """;
        var index = JavaJdkSymbolIndex.fromCurrentRuntime();
        var model = JavaSemanticAnalyzer.analyzeFacts(source, index);
        assertResolvedCalls(model, model.syntaxTree().root());
        var context = new JavaRuleContext(Path.of("Example.java"), source, model, index);
        assertTrue(JavaInspectionRuleEngine.collectDiagnostics(new CoreCallResolutionInspection(), context).isEmpty());
    }

    @Test
    public void anonymousMembersStayInTheirOwnScopesAndProjectMemberOwners() {
        String source = """
            class Example {
                Object first = new Object() { int hidden; void onlyInside() {} };
                Object second = new Object() { int hidden; };
                int outer;
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
        var outerScope = model.rootScope().children().getFirst();
        assertTrue(outerScope.lookupLocal("hidden").isEmpty());
        assertTrue(outerScope.lookupLocal("onlyInside").isEmpty());
        assertEquals(1, outerScope.lookupLocal("outer").size());
        var file = new JavaProjectSemanticExtractor().extract(Path.of("Example.java"), model);
        var index = JavaProjectSemanticIndex.builder().putFile(file).build();
        assertTrue(index.lookupMember("Example", "hidden").isEmpty());
        assertTrue(index.lookupMember("Example", "onlyInside").isEmpty());
        assertTrue(index.lookupSimpleName("hidden").isEmpty());
        var owners = file.semanticShard().declarations().stream().filter(symbol -> symbol.simpleName().equals("hidden"))
            .map(symbol -> symbol.ownerId()).toList();
        assertEquals(2, owners.size());
        assertEquals(2, new HashSet<>(owners).size());
    }

    @Test
    public void sameNamedLocalTypesHaveDistinctInternalQualifiedNames() {
        String source = "class Example { void first() { class Local { int value; } } void second() { class Local { int value; } } }";
        var file = new JavaProjectSemanticExtractor().extract(Path.of("Example.java"), source);
        var index = JavaProjectSemanticIndex.builder().putFile(file).build();
        assertTrue(index.lookupSimpleName("Local").isEmpty());
        var types = file.semanticShard().declarations().stream().filter(symbol -> symbol.simpleName().equals("Local"))
            .toList();
        assertEquals(2, types.size());
        assertNotEquals(types.getFirst().qualifiedName(), types.getLast().qualifiedName());
        assertNotEquals(types.getFirst().id(), types.getLast().id());
    }

    private static void assertResolvedCalls(SemanticModel model, SyntaxNode node) {
        if (node.kind().id().equals("JAVA_METHOD_INVOCATION_EXPRESSION")) {
            assertTrue(model.resolvedSymbol(node).isPresent(), "Unresolved call at " + node.start());
        }
        for (SyntaxNode child : node.children()) {
            assertResolvedCalls(model, child);
        }
    }
}
