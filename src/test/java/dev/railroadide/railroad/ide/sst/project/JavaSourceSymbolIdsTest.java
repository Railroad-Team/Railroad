package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticModel;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaSourceSymbolIdsTest {
    @Test
    public void declarationIdsSurviveReanalysisAndTriviaEdits() {
        String source = """
            package sample;
            class Example<T> {
                int value;
                Example() {}
                Example(int value) {}
                void run(int value) { int local = value; { int other = 2; } class Local {} }
                void run(String value) { int local = 1; class Local {} }
                class Nested { int value; }
            }
            """;
        List<Symbol> original = declarations(source);
        List<SymbolId> expected = ids(original);
        String triviaEdited = "/** Header */\n" + source.replace("class ", "/* before type */ class ")
            .replace(";", "; // end\n").replace("{", "{\n    ");
        assertEquals(expected, ids(declarations(triviaEdited)));
        assertEquals(expected, ids(declarations(source)));
        assertEquals(expected.size(), new HashSet<>(expected).size());
        assertTrue(expected.contains(JavaSymbolIds.type("sample.Example$Nested")));
        assertTrue(expected.contains(JavaSymbolIds.constructor(JavaSymbolIds.type("sample.Example"), List.of())));
        assertTrue(
            expected.contains(JavaSymbolIds.method(JavaSymbolIds.type("sample.Example"), "run", List.of("int"))));
        assertNotSame(original.getFirst(), declarations(source).getFirst());
    }

    @Test
    public void cachedImportedTypesStillRespectNestedTypeShadowing() {
        String source = """
            package sample;
            import java.util.List;
            class Example {
                void outer(List value) { List copy = value; }
                class Nested {
                    class List {}
                    void inner(List value) { List copy = value; }
                }
                void again(List value) { List copy = value; }
            }
            """;
        var symbols = ids(declarations(source));
        var outerType = JavaSymbolIds.type("sample.Example");
        var nestedType = JavaSymbolIds.type("sample.Example$Nested");
        var outer = JavaSymbolIds.method(outerType, "outer", List.of("java.util.List"));
        var inner = JavaSymbolIds.method(nestedType, "inner", List.of("sample.Example$Nested$List"));
        var again = JavaSymbolIds.method(outerType, "again", List.of("java.util.List"));

        assertTrue(symbols.containsAll(List.of(outer, inner, again, JavaSymbolIds.parameter(outer, 0),
            JavaSymbolIds.parameter(inner, 0), JavaSymbolIds.parameter(again, 0))));
    }

    @Test
    public void anonymousDeclarationsDoNotShareIdentity() {
        String source = """
            class Example {
                void run() { Object first = new Object() {}; Object second = new Object() {}; }
            }
            """;
        var model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
        List<SyntaxNode> anonymous = new ArrayList<>();
        collectAnonymous(model.syntaxTree().root(), anonymous);
        assertEquals(2, anonymous.size());
        SymbolId first = JavaSourceSymbolIds.declarationId(anonymous.getFirst());
        SymbolId second = JavaSourceSymbolIds.declarationId(anonymous.getLast());
        assertNotEquals(first, second);
        var edited = JavaSemanticAnalyzer
            .analyzeDeclarationsFacts(source.replace("new Object()", "new /*x*/ Object ( )"));
        List<SyntaxNode> editedAnonymous = new ArrayList<>();
        collectAnonymous(edited.syntaxTree().root(), editedAnonymous);
        assertEquals(first, JavaSourceSymbolIds.declarationId(editedAnonymous.getFirst()));
        assertEquals(second, JavaSourceSymbolIds.declarationId(editedAnonymous.getLast()));
    }

    @Test
    public void nestedTypesRetainDistinctLocalAndAnonymousOwnerIdentities() {
        String source = """
            class Example {
                void first() { class Local { class Nested {} } }
                void second() { class Local { class Nested {} } }
                void anonymous() {
                    Object first = new Object() { class Nested {} };
                    Object second = new Object() { class Nested {} };
                }
            }
            """;
        List<Symbol> nested = declarations(source).stream().filter(symbol -> symbol.simpleName().equals("Nested"))
            .toList();
        assertEquals(4, nested.size());
        List<SymbolId> original = ids(nested);
        assertEquals(4, new HashSet<>(original).size());
        assertTrue(original.stream().allMatch(id -> id.value().contains("/localType/")));
        var edited = declarations(source.replace("{", "{ /* nested comment */\n"))
            .stream().filter(symbol -> symbol.simpleName().equals("Nested")).toList();
        assertEquals(original, ids(edited));
    }

    private static List<SymbolId> ids(List<Symbol> symbols) {
        return symbols.stream().map(symbol -> symbol.id().orElseThrow()).toList();
    }

    private static List<Symbol> declarations(String source) {
        var model = JavaSemanticAnalyzer.analyzeDeclarationsFacts(source);
        List<Symbol> symbols = new ArrayList<>();
        collect(model, model.syntaxTree().root(), symbols);
        return symbols.stream().distinct().filter(symbol -> !symbol.kind().id().equals("java:import")).toList();
    }

    private static void collect(SemanticModel model, SyntaxNode node, List<Symbol> symbols) {
        model.declaredSymbol(node).ifPresent(symbols::add);
        for (SyntaxNode child : node.children()) {
            collect(model, child, symbols);
        }
    }

    private static void collectAnonymous(SyntaxNode node, List<SyntaxNode> output) {
        if (node.kind().id().equals("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION")
            && JavaSourceSymbolIds.child(node, "JAVA_ANONYMOUS_CLASS_BODY") != null) {
            output.add(node);
        }
        for (SyntaxNode child : node.children()) {
            collectAnonymous(child, output);
        }
    }
}
