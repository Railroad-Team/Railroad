package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class JavaCapturedMemberTypeTest {
    @Test
    public void compactRecordConstructorQualifiesLambdaParametersUsingItsOwnImports() {
        var store = new DefaultDocumentStore("record-members", new WorkspaceConfigurationSnapshot(0, Set.of()));
        TextDocumentSnapshot event = source("Event.java",
            "package eventtypes; public class Event { public void consume() {} }");
        TextDocumentSnapshot column = source("Column.java", """
            package api;
            import java.util.function.BiConsumer;
            import eventtypes.Event;
            public record Column(BiConsumer<Integer, Event> onClick) {
                public Column {}
                public Column(int ignored) { this(null); }
            }
            """);
        TextDocumentSnapshot use = source("Use.java", """
            import api.Column;
            class Use { void run() { new Column((line, event) -> event.consume()); } }
            """);
        for (TextDocumentSnapshot document : List.of(event, column, use)) {
            store.publishBacking(document);
        }

        var jdk = JavaJdkSymbolIndex.fromCurrentRuntime();
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> jdk)) {
            JavaWorkspaceAnalysis analysis = provider.capture(store.capture());
            var model = JavaSemanticAnalyzer.analyzeFacts(JavaSyntaxParser.parse(use), analysis, () -> {
            });

            List<SyntaxNode> calls = new ArrayList<SyntaxNode>();
            collectCalls(model.syntaxTree().root(), calls);
            SyntaxNode consume = calls.stream().filter(node -> model.resolvedSymbol(node)
                .map(symbol -> symbol.simpleName().equals("consume")).orElse(false)).findFirst().orElseThrow();
            assertEquals(JavaSymbolIds.method(JavaSymbolIds.type("eventtypes.Event"), "consume", List.of()),
                model.resolvedSymbol(consume).orElseThrow().id().orElseThrow());
            assertEquals("void", model.inferredType(consume).orElseThrow().displayName());
        }
    }

    @Test
    public void wildcardCallableIdentityKeepsCapturedReturnTypeForChainedMemberResolution() {
        var store = new DefaultDocumentStore("members", new WorkspaceConfigurationSnapshot(0, Set.of()));
        TextDocumentSnapshot widget = source("Widget.java", "package dependency; public class Widget {}");
        TextDocumentSnapshot result = source("Result.java",
            "package dependency; public class Result { public int value() { return 1; } }");
        TextDocumentSnapshot library = source("Library.java", """
            package api;
            import dependency.*;
            public class Library { public Result find(Widget widget) { return null; } }
            """);
        TextDocumentSnapshot use = source("Use.java", """
            import dependency.*;
            import api.Library;
            class Use { int read(Library library, Widget widget) { return library.find(widget).value(); } }
            """);
        for (TextDocumentSnapshot document : List.of(widget, result, library, use)) {
            store.publishBacking(document);
        }

        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> JavaProjectSemanticIndex.empty())) {
            JavaWorkspaceAnalysis analysis = provider.capture(store.capture());
            store.publishOverlay(new TextDocumentSnapshot(library.id(), library.uri(), library.version().next(),
                "java", "package api; public class Library {}", StandardCharsets.UTF_8));
            var model = JavaSemanticAnalyzer.analyzeFacts(JavaSyntaxParser.parse(use), analysis, () -> {
            });

            assertTrue(
                model.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("SEM_UNRESOLVED_CALL")),
                () -> model.diagnostics().toString());
            List<SyntaxNode> calls = new ArrayList<SyntaxNode>();
            collectCalls(model.syntaxTree().root(), calls);
            SyntaxNode find = calls.stream().filter(node -> model.resolvedSymbol(node)
                .map(symbol -> symbol.simpleName().equals("find")).orElse(false)).findFirst().orElseThrow();
            assertEquals(JavaSymbolIds.method(JavaSymbolIds.type("api.Library"), "find", List.of("dependency.Widget")),
                model.resolvedSymbol(find).orElseThrow().id().orElseThrow());
            assertEquals("dependency.Result", model.inferredType(find).orElseThrow().displayName());
            SyntaxNode value = calls.stream().filter(node -> model.resolvedSymbol(node)
                .map(symbol -> symbol.simpleName().equals("value")).orElse(false)).findFirst().orElseThrow();
            assertEquals("int", model.inferredType(value).orElseThrow().displayName());
        }
    }

    private static void collectCalls(SyntaxNode node, List<SyntaxNode> calls) {
        if (node.kind().equals(JavaSyntaxKinds.METHOD_INVOCATION_EXPRESSION)) {
            calls.add(node);
        }

        node.children().forEach(child -> collectCalls(child, calls));
    }

    private static TextDocumentSnapshot source(String name, String text) {
        return new TextDocumentSnapshot(DocumentId.create(), DocumentUri.virtual("memory", name),
            DocumentVersion.initial().next(), "java", text, StandardCharsets.UTF_8);
    }
}
