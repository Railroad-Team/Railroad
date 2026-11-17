package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysisProvider;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticNavigationResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

public class JavaNavigationServiceTest {
    @TempDir
    public Path directory;

    @TestFactory
    public Stream<DynamicTest> resolvesCrossFileMethodsFieldsAndOverloadedConstructors() {
        return Stream.of("Helper.run()", "Helper.VALUE", "new Helper(42)", "new Helper()")
            .map(value -> DynamicTest.dynamicTest(String.valueOf(value),
                () -> resolvesCrossFileMethodsFieldsAndOverloadedConstructors(value)));
    }

    private void resolvesCrossFileMethodsFieldsAndOverloadedConstructors(String expression) {
        String declarations = "class Helper { static int VALUE; static void run() {} Helper() {} Helper(int n) {} }";
        var helper = document("Helper.java", declarations);
        var source = document("Main.java", "class Main { void test() { " + expression + "; } }");
        var store = store(source, helper);
        String name = expression.contains("run") ? "run" : expression.contains("VALUE") ? "VALUE" : "Helper";
        int offset = source.text().lastIndexOf(name);
        String expected = expression.equals("new Helper(42)")
            ? "Helper(int"
            : expression.equals("new Helper()") ? "Helper()" : name;
        try (var services = new JavaSstServices()) {
            var context = context(store.capture(), source);
            var result = services.definitions(context, offset);
            assertTrue(result.belongsTo(context));
            assertEquals(1, result.value().size());
            var target = assertInstanceOf(Location.TextLocation.class, result.value().getFirst());
            assertEquals(helper.id(), target.documentId());
            assertEquals(declarations.indexOf(expected), target.range().start);
            assertEquals(name, declarations.substring(target.range().start, target.range().end));
            assertDoesNotThrow(() -> target.validateAgainst(helper));
        }
    }

    @TestFactory
    public Stream<DynamicTest> preservesSameFileFieldParameterAndLocalNavigation() {
        return Stream.of("value", "argument", "local")
            .map(value -> DynamicTest.dynamicTest(String.valueOf(value),
                () -> preservesSameFileFieldParameterAndLocalNavigation(value)));
    }

    private void preservesSameFileFieldParameterAndLocalNavigation(String name) {
        String text = "class Example { int value; int read(int argument) { int local = argument; return value + local; } }";
        var source = document("Example.java", text);
        try (var services = new JavaSstServices()) {
            var result = services.definitions(context(store(source).capture(), source), text.lastIndexOf(name));
            assertEquals(1, result.value().size());
            var target = result.value().getFirst();
            assertEquals(source.id(), target.documentId());
            assertEquals(text.indexOf(name), target.range().start);
        }
    }

    @Test
    public void whitespacePunctuationUnknownCallsAndOutOfBoundsReturnNoTargets() {
        var source = document("Main.java", "class Main { void test() { missing(); } }");
        try (var services = new JavaSstServices()) {
            var context = context(store(source).capture(), source);
            for (int offset : List.of(-1, 5, source.text().indexOf("missing"), source.text().indexOf(';'),
                source.text().length(), source.text().length() + 1)) {
                assertTrue(services.definitions(context, offset).value().isEmpty(), "Offset: " + offset);
            }
        }
    }

    @Test
    public void oldRequestKeepsItsDestinationRevisionAfterAnOverlayIsPublished() {
        var helper = document("Helper.java", "class Helper { static int VALUE; }");
        var source = document("Main.java", "class Main { int x = Helper.VALUE; }");
        var store = store(source, helper);
        var old = context(store.capture(), source);
        var changed = new TextDocumentSnapshot(helper.id(), helper.uri(), helper.version().next(), "java",
            helper.origin(), "\n\n" + helper.text(), StandardCharsets.UTF_8);
        store.publishOverlay(changed);
        try (var services = new JavaSstServices()) {
            int offset = source.text().indexOf("VALUE");
            var before = services.definitions(old, offset).value().getFirst();
            var after = services.definitions(context(store.capture(), source), offset).value().getFirst();
            assertEquals(helper.version(), before.documentVersion());
            assertEquals(changed.version(), after.documentVersion());
            assertEquals(before.range().start + 2, after.range().start);
        }
    }

    @Test
    public void cancelledRequestsAndClosedServicesRejectNavigation() {
        var source = document("Main.java", "class Main {}");
        var workspace = store(source).capture();
        var cancellation = new CancellationTokenSource();
        cancellation.cancel();
        var cancelled = new FeatureRequestContext(workspace, source, cancellation.token(), Deadline.none());
        var services = new JavaSstServices();
        try {
            assertThrows(CancellationException.class, () -> services.definitions(cancelled, 6));
        } finally {
            services.close();
        }
        assertThrows(CancellationException.class, () -> services.definitions(context(workspace, source), 6));
    }

    @Test
    public void storeConvenienceOverloadUsesCurrentOverlayAndRequestOutsideSourceRoots() {
        var source = document("Scratch.java", "class Scratch { int value; int read() { return value; } }");
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        store.publishBacking(source);
        var changed = new TextDocumentSnapshot(source.id(), source.uri(), source.version().next(), "java",
            source.origin(), "\n" + source.text(), StandardCharsets.UTF_8);
        store.publishOverlay(changed);
        try (var provider = new JavaWorkspaceAnalysisProvider()) {
            int offset = changed.text().lastIndexOf("value");
            var id = SemanticNavigationResolver.symbolAt(store, provider, source.id(), offset).orElseThrow();
            var workspace = store.capture();
            var graph = provider.capture(workspace, source.id()).resolvedGraph();
            var target = SemanticNavigationResolver.definitionOf(graph, workspace, id).orElseThrow();
            assertSame(changed, target.snapshot());
            assertEquals(changed.text().indexOf("value"), target.location().range().start);
        }
    }

    private TextDocumentSnapshot document(String name, String text) {
        var uri = DocumentUri.fromPath(directory.resolve(name));
        return new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
    }

    @TestFactory
    public Stream<DynamicTest> explicitConstructorInvocationsOnlyNavigateOnTheirKeyword() {
        return Stream.of("this", "super")
            .map(keyword -> DynamicTest.dynamicTest(keyword, () -> {
                String text = "class Base { Base(int n) {} } class Example extends Base { "
                    + "Example(int n) { super(n); } Example() { " + keyword + "(1); } }";
                var source = document("Example.java", text);
                int start = text.lastIndexOf(keyword + "(1)");
                try (var services = new JavaSstServices()) {
                    var context = context(store(source).capture(), source);
                    var result = services.definitions(context, start).value();
                    assertEquals(1, result.size());
                    assertEquals(text.indexOf(keyword.equals("this") ? "Example(int" : "Base(int"),
                        result.getFirst().range().start);
                    for (int offset = start + keyword.length(); offset < start + keyword.length() + 4; offset++) {
                        assertTrue(services.definitions(context, offset).value().isEmpty(),
                            "Constructor punctuation or literal must not navigate at offset " + offset);
                    }
                }
            }));
    }

    private DefaultDocumentStore store(TextDocumentSnapshot... documents) {
        var configuration = new WorkspaceConfigurationSnapshot(0, Set.of("main"),
            Map.of("module.main.sources", directory.toString()));
        var store = new DefaultDocumentStore("test", configuration);
        for (var document : documents) {
            store.publishBacking(document);
        }
        return store;
    }

    private static FeatureRequestContext context(WorkspaceSnapshot workspace, TextDocumentSnapshot source) {
        return new FeatureRequestContext(workspace, source, CancellationToken.none(), Deadline.none());
    }
}
