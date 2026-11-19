package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysisProvider;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.provider.api.UsageQuery;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSink;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticNavigationResolver;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Field;
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
    public void definitionsResolveOnlyTheRequestingDocument() throws Exception {
        String declarations = "class Helper { static void run() {} }";
        var helper = document("Helper.java", declarations);
        var other = document("Other.java",
            "class Other { void unrelated() { int unrelatedLocal = 1; Helper.run(); } }");
        var source = document("Main.java", "class Main { void test() { int local = 1; Helper.run(); local++; } }");
        var store = store(source, helper, other);
        try (var services = new JavaSstServices()) {
            var context = context(store.capture(), source);
            var call = services.definitions(context, source.text().lastIndexOf("run")).value();
            var local = services.definitions(context, source.text().lastIndexOf("local")).value();

            var target = assertInstanceOf(Location.TextLocation.class, call.getFirst());
            assertEquals(helper.id(), target.documentId());
            assertEquals(declarations.indexOf("run"), target.range().start);
            var localTarget = assertInstanceOf(Location.TextLocation.class, local.getFirst());
            assertEquals(source.id(), localTarget.documentId());
            assertEquals(source.text().indexOf("local"), localTarget.range().start);
            var analysis = services.capture(context);
            var otherShard = analysis.sourceIndex().semanticShards().stream()
                .filter(shard -> shard.documentId().equals(other.id())).findFirst().orElseThrow();
            assertTrue(otherShard.declarations().stream().anyMatch(symbol -> symbol.simpleName().equals("unrelated")));
            assertFalse(otherShard.declarations().stream()
                .anyMatch(symbol -> symbol.simpleName().equals("unrelatedLocal")));
            Field resolved = JavaWorkspaceAnalysis.class.getDeclaredField("resolvedGraph");
            resolved.setAccessible(true);
            assertNull(resolved.get(analysis), "Go to definition must not resolve every document's body");
            assertTrue(analysis.resolvedGraph().shards().get(other.id()).declarations().stream()
                .anyMatch(symbol -> symbol.simpleName().equals("unrelatedLocal")));
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
    public void capturedOverlayResolvesForRequestsOutsideSourceRoots() {
        var source = document("Scratch.java", "class Scratch { int value; int read() { return value; } }");
        var store = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of()));
        store.publishBacking(source);
        var changed = new TextDocumentSnapshot(source.id(), source.uri(), source.version().next(), "java",
            source.origin(), "\n" + source.text(), StandardCharsets.UTF_8);
        store.publishOverlay(changed);
        try (var provider = new JavaWorkspaceAnalysisProvider()) {
            int offset = changed.text().lastIndexOf("value");
            var workspace = store.capture();
            var graph = provider.capture(workspace, source.id()).resolvedGraph();
            var id = SemanticNavigationResolver.symbolAt(graph, changed, offset).orElseThrow();
            var targets = SemanticNavigationResolver.definitionsOf(graph, workspace, id);
            assertEquals(1, targets.size());
            var target = targets.getFirst();
            assertSame(changed, target.snapshot());
            assertEquals(changed.text().indexOf("value"), target.location().range().start);
        }
    }

    @Test
    public void implementationsOfATypeListEveryTransitiveSubtypeInRankOrder() {
        var shape = document("Shape.java", "interface Shape { double area(); }");
        var circle = document("Circle.java", "class Circle implements Shape { public double area() { return 1; } }");
        var square = document("Square.java", "class Square extends Circle { public double area() { return 2; } }");
        var store = store(shape, circle, square);
        try (var services = new JavaSstServices()) {
            var context = context(store.capture(), shape);
            var types = services.implementations(context, shape.text().indexOf("Shape")).value();
            assertEquals(List.of(circle.id(), square.id()), types.stream().map(Location::documentId).toList());
            assertEquals(circle.text().indexOf("Circle"), types.getFirst().range().start);

            var methods = services.implementations(context, shape.text().indexOf("area")).value();
            assertEquals(List.of(circle.id(), square.id()), methods.stream().map(Location::documentId).toList());
            assertEquals(circle.text().indexOf("area"), methods.getFirst().range().start);
            assertTrue(services.implementations(context(store.capture(), square), square.text().indexOf("Square"))
                .value().isEmpty());
        }
    }

    @Test
    public void usagesStreamPerDocumentAndIncludeUnsavedOverlays() {
        var helper = document("Helper.java", "class Helper { static void run() {} }");
        var main = document("Main.java", "class Main { void a() { Helper.run(); Helper.run(); } }");
        var other = document("Other.java", "class Other { }");
        var store = store(helper, main, other);
        var edited = new TextDocumentSnapshot(other.id(), other.uri(), other.version().next(), "java",
            other.origin(), "class Other { void b() { Helper.run(); } }", StandardCharsets.UTF_8);
        store.publishOverlay(edited);
        var batches = new ArrayList<List<Location<?>>>();
        var progress = new ArrayList<Integer>();
        try (var services = new JavaSstServices()) {
            var context = context(store.capture(), helper);
            var result = services.usages(context, new UsageQuery.AtOffset(helper.text().indexOf("run")),
                (batch, searched, total) -> {
                    batches.add(batch);
                    progress.add(searched);
                    assertEquals(2, total);
                });
            assertTrue(result.belongsTo(context));
            var search = result.value();
            assertEquals(3, search.usages().size());
            assertEquals(List.of(main.id(), main.id(), other.id()),
                search.usages().stream().map(Location::documentId).toList());
            assertEquals(edited.version(), search.usages().getLast().documentVersion());
            assertEquals(List.of(0, 1, 2), progress);
            assertEquals(List.of(0, 2, 1), batches.stream().map(List::size).toList());
            assertTrue(search.displayName().contains("run"));

            var refreshed = services.usages(context(store.capture(), main), new UsageQuery.OfSymbol(search.symbol()),
                UsageSink.NONE).value();
            assertEquals(search.usages(), refreshed.usages());
        }
    }

    @Test
    public void usagesOfAnUnresolvedPositionOrUnknownSymbolAreUnsupported() {
        var source = document("Main.java", "class Main { void test() { missing(); } }");
        try (var services = new JavaSstServices()) {
            var context = context(store(source).capture(), source);
            assertNull(services.usages(context, new UsageQuery.AtOffset(source.text().indexOf("missing")),
                UsageSink.NONE).value());
            assertNull(services.usages(context, new UsageQuery.OfSymbol(new SymbolId("java:Unknown")),
                UsageSink.NONE).value());
        }
    }

    @Test
    public void cancelledUsageSearchesPublishNothing() {
        var source = document("Main.java", "class Main { int value; int read() { return value; } }");
        var cancellation = new CancellationTokenSource();
        cancellation.cancel();
        var cancelled = new FeatureRequestContext(store(source).capture(), source, cancellation.token(),
            Deadline.none());
        var batches = new ArrayList<List<Location<?>>>();
        try (var services = new JavaSstServices()) {
            assertThrows(CancellationException.class, () -> services.usages(cancelled,
                new UsageQuery.AtOffset(source.text().lastIndexOf("value")), (batch, _, _) -> batches.add(batch)));
            assertThrows(CancellationException.class,
                () -> services.implementations(cancelled, source.text().indexOf("Main")));
        }
        assertTrue(batches.isEmpty());
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
