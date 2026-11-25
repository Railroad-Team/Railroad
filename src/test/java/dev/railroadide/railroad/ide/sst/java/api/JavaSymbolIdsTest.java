package dev.railroadide.railroad.ide.sst.java.api;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaSymbolIdsTest {
    @Test
    public void overloadIdentityUsesNormalizedErasedParameters() {
        var owner = JavaSymbolIds.type("sample.Box");
        assertEquals(JavaSymbolIds.method(owner, "put", List.of("java.util.List<java.lang.String>", "int...")),
            JavaSymbolIds.method(owner, "put", List.of("java.util.List", "int []")));
        assertNotEquals(JavaSymbolIds.method(owner, "put", List.of("int")),
            JavaSymbolIds.method(owner, "put", List.of("long")));
        assertNotEquals(JavaSymbolIds.method(owner, "Box", List.of()), JavaSymbolIds.constructor(owner, List.of()));
        assertEquals(JavaSymbolIds.type("sample.Box$Nested"), JavaSymbolIds.type("sample/Box$Nested"));
        assertNotEquals(JavaSymbolIds.type("module.one", "sample.Box"), JavaSymbolIds.type("module.two", "sample.Box"));
    }

    @Test
    public void everyDeclarationCategoryHasDistinctIdentity() {
        SymbolId owner = JavaSymbolIds.type("sample.Box");
        SymbolId method = JavaSymbolIds.method(owner, "run", List.of("int"));
        List<SymbolId> ids = List.of(owner, JavaSymbolIds.type("sample.Box$Nested"),
            JavaSymbolIds.type("sample.Box.Nested"), method, JavaSymbolIds.method(owner, "run", List.of("long")),
            JavaSymbolIds.constructor(owner, List.of("int")), JavaSymbolIds.constructor(owner, List.of()),
            JavaSymbolIds.field(owner, "run"), JavaSymbolIds.parameter(method, 0), JavaSymbolIds.parameter(method, 1),
            JavaSymbolIds.typeParameter(method, 0), JavaSymbolIds.typeParameter(owner, 0),
            JavaSymbolIds.local(method, "0.1", "x"), JavaSymbolIds.local(method, "0.2", "x"),
            JavaSymbolIds.localType(method, "0.1", "x"), JavaSymbolIds.anonymous(method, "0.1"),
            JavaSymbolIds.anonymous(method, "0.2"), JavaSymbolIds.packageId("", "sample"),
            JavaSymbolIds.module("sample"));
        assertEquals(ids.size(), new HashSet<>(ids).size());
    }

    @Test
    public void owningTypeNamesPreserveNestedBinaryNamesAcrossMemberSignatures() {
        String name = "sample.Outer$Nested";
        var type = JavaSymbolIds.type("sample.module", name);
        var method = JavaSymbolIds.binaryMethod(type, "read", List.of("other.Argument"), "other.Result");
        for (SymbolId symbol : List.of(type, method, JavaSymbolIds.parameter(method, 0),
            JavaSymbolIds.typeParameter(type, 0),
            JavaSymbolIds.field(type, "field"), JavaSymbolIds.constructor(type, List.of()))) {
            assertEquals(name, JavaSymbolIds.owningTypeName(symbol));
        }

        for (SymbolId symbol : List.of(JavaSymbolIds.packageId("", "sample"), JavaSymbolIds.module("sample"),
            new SymbolId("other:type"), new SymbolId("java:module//type/!"))) {
            assertNull(JavaSymbolIds.owningTypeName(symbol));
        }
    }

    @Test
    public void structuralLocalAnchorsSurviveWhitespaceAndCommentEdits() {
        String original = "class Example { void run() { int first=1; { int first=2; } class Local {} new Object() {}; } }";
        String edited = """
            /** Header */
            class Example {
                // Method comment
                void run() {
                    int /* variable */ first = 1;
                    { int first = 2; }
                    /* local type */ class Local { }
                    new Object() { /* body */ };
                }
            }
            """;
        List<String> originalAnchors = anchors(original);
        assertFalse(originalAnchors.isEmpty());
        assertEquals(originalAnchors, anchors(edited));
        assertEquals(originalAnchors.size(), new HashSet<>(originalAnchors).size());
    }

    @Test
    public void invalidInputsFailWithoutInventingIdentities() {
        assertThrows(IllegalArgumentException.class, () -> JavaSymbolIds.type(" "));
        assertThrows(IllegalArgumentException.class, () -> JavaSymbolIds.parameter(JavaSymbolIds.type("Box"), -1));
        assertThrows(IllegalArgumentException.class, () -> JavaSymbolIds.normalizeParameterType("List<String"));
        assertThrows(IllegalArgumentException.class, () -> JavaSymbolIds.normalizeParameterType("void"));
        assertThrows(IllegalArgumentException.class, () -> JavaSymbolIds.normalizeParameterType("? extends Number"));
    }

    private static List<String> anchors(String source) {
        SyntaxNode root = JavaSyntaxParser.parse(source).root();
        List<String> anchors = new ArrayList<>();
        collectAnchors(root, root, anchors);
        return anchors;
    }

    private static void collectAnchors(SyntaxNode node, SyntaxNode root, List<String> anchors) {
        if (node.kind().id().equals("JAVA_VARIABLE_DECLARATOR") || node.kind().id().equals("JAVA_CLASS_DECLARATION")) {
            anchors.add(JavaSymbolIds.lexicalPath(node, root));
        }

        for (SyntaxNode child : node.children()) {
            collectAnchors(child, root, anchors);
        }
    }
}
