package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySources;
import dev.railroadide.railroad.ide.sst.provider.api.UsageQuery;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSink;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.semantic.api.DefinitionTarget;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class JavaLibraryNavigationTest {
    private static final String LIBRARY = """
        package lib;

        import java.util.List;

        /** Greets people. */
        public class Greeter<T extends Comparable<T>> {
            public static int VALUE;

            public Greeter() {
            }

            public Greeter(String name) {
            }

            public static String greet(String name) {
                return "Hi " + name;
            }

            public static String greet(String name, int times) {
                return name.repeat(times);
            }

            public <E> E pick(List<? extends E> items, T key) {
                return items.getFirst();
            }

            public static class Inner {
                public void run() {
                }
            }

            public enum Mood {
                HAPPY, SAD
            }
        }
        """;
    private static final String SOURCE = """
        import lib.Greeter;
        import java.util.List;

        class Main {
            void test(Greeter<String> greeter) {
                Greeter.greet("a");
                Greeter.greet("a", 2);
                int value = Greeter.VALUE;
                new Greeter<String>("x");
                greeter.pick(List.of(1), "k");
                new Greeter.Inner().run();
                Greeter.Mood mood = Greeter.Mood.SAD;
            }
        }
        """;

    @TempDir
    public Path directory;

    @TestFactory
    public Stream<DynamicTest> libraryMembersNavigateIntoAReadOnlyViewOfTheirClass() {
        return Stream.of(Layout.values()).map(layout -> DynamicTest.dynamicTest(layout.name(), () -> {
            Path jar = library(layout);
            var source = document(directory.resolve("src/Main.java"), SOURCE);
            var store = store(jar);
            store.publishBacking(source);
            var workspace = store.capture();
            try (var services = new JavaSstServices(new JavaLibrarySources(directory.resolve("views")))) {
                assertMember(services, workspace, source, "greet(\"a\")", "greet", "(", layout);
                assertMember(services, workspace, source, "greet(\"a\", 2)", "greet", "int", layout);
                assertMember(services, workspace, source, "VALUE", "VALUE", null, layout);
                assertMember(services, workspace, source, "Greeter<String>(\"x\")", "Greeter", "String", layout);
                assertMember(services, workspace, source, "pick(", "pick", "List", layout);
                assertMember(services, workspace, source, "run()", "run", null, layout);
                assertMember(services, workspace, source, "SAD", "SAD", null, layout);
                assertMember(services, workspace, source, "Inner()", "Inner", null, layout);
                assertMember(services, workspace, source, "Mood mood", "Mood", null, layout);
                assertMember(services, workspace, source, "Greeter.Mood mood", "Greeter", null, layout);
            }
        }));
    }

    @TestFactory
    public Stream<DynamicTest> jdkMembersNavigateIntoTheirClass() {
        String text = "class Main { int size(String value) { return value.length() + new java.util.ArrayList<String>()"
            + ".size(); } }";
        return Stream.of(new String[]{"length", "String.java", "length"},
            new String[]{"ArrayList<", "ArrayList.java", "ArrayList"},
            new String[]{"size()", "ArrayList.java", "size"})
            .map(test -> DynamicTest.dynamicTest(test[0], () -> {
                var source = document(directory.resolve("src/Main.java"), text);
                var store = store();
                store.publishBacking(source);
                try (var services = new JavaSstServices(new JavaLibrarySources(directory.resolve("views")))) {
                    var targets = services.definitions(context(store.capture(), source), text.indexOf(test[0]))
                        .value();

                    assertEquals(1, targets.size());
                    var target = targets.getFirst();
                    assertEquals(test[1], target.snapshot().uri().fileName().orElseThrow());
                    assertEquals(test[2], text(target));
                    assertTrue(Set.of(ProvenanceKind.ATTACHED_SOURCE, ProvenanceKind.DECOMPILED)
                        .contains(target.snapshot().origin().provenance()));
                }
            }));
    }

    @Test
    public void usagesFindTheExactLibraryMethodAcrossCandidateFilesAndRefreshByIdentity() throws Exception {
        var store = store(library(Layout.NONE));
        var main = document(directory.resolve("src/Main.java"), SOURCE);
        var other = document(directory.resolve("src/Other.java"),
            "import lib.Greeter; class Other { String s = Greeter.gr\\u0065et(\"b\"); }");
        var unrelated = document(directory.resolve("src/Unrelated.java"), "class Unrelated {}");
        store.publishBacking(main);
        store.publishBacking(other);
        store.publishBacking(unrelated);
        var request = context(store.capture(), main);
        int offset = SOURCE.indexOf("greet(");
        try (var services = new JavaSstServices(new JavaLibrarySources(directory.resolve("views")))) {
            var result = services
                .usages(request, new UsageQuery.AtOffset(offset), (batch, searched, total) -> assertEquals(2, total))
                .value();

            assertNotNull(result);
            assertTrue(result.displayName().contains("greet"));
            assertEquals(List.of(main.id(), other.id()), result.usages().stream().map(Location::documentId).toList());
            assertEquals(offset, result.usages().getFirst().range().start);
            assertEquals(other.text().indexOf("gr\\u0065et"), result.usages().getLast().range().start);
            assertEquals(result,
                services.usages(request, new UsageQuery.OfSymbol(result.symbol()), UsageSink.NONE).value());
            int endOfName = offset + "greet".length();
            assertEquals(result, services.usages(request, new UsageQuery.AtOffset(endOfName), UsageSink.NONE).value());
            assertNull(services.usages(request, new UsageQuery.AtOffset(endOfName + 1), UsageSink.NONE).value());

            var target = services.definitions(request, offset).value().getFirst();
            store.publishBacking(target.snapshot());
            var fromDeclaration = services.usages(context(store.capture(), target.snapshot()),
                new UsageQuery.AtOffset(target.location().range().start), UsageSink.NONE).value();
            assertEquals(result.symbol(), fromDeclaration.symbol());
            assertEquals(result.usages(), fromDeclaration.usages());
        }
    }

    @Test
    public void aViewThatIsAlreadyOpenIsTargetedThroughTheWorkspace() throws Exception {
        Path jar = library(Layout.MAVEN);
        var source = document(directory.resolve("src/Main.java"), SOURCE);
        var store = store(jar);
        store.publishBacking(source);
        try (var services = new JavaSstServices(new JavaLibrarySources(directory.resolve("views")))) {
            int offset = SOURCE.indexOf("VALUE");
            var first = services.definitions(context(store.capture(), source), offset).value().getFirst();
            var open = new TextDocumentSnapshot(DocumentId.create(), first.snapshot().uri(), DocumentVersion.initial(),
                "java", first.snapshot().origin(), first.snapshot().text(), StandardCharsets.UTF_8);
            store.publishBacking(open);

            var reopened = services.definitions(context(store.capture(), source), offset).value().getFirst();

            assertEquals(open.id(), reopened.location().documentId());
            assertSame(open, reopened.snapshot());
            assertEquals(first.location().range(), reopened.location().range());
        }
    }

    private void assertMember(
        JavaSstServices services,
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot source,
        String usage,
        String name,
        String following,
        Layout layout
    ) {
        int offset = SOURCE.indexOf(usage) + usage.indexOf(name);
        var targets = services.definitions(context(workspace, source), offset).value();

        assertEquals(1, targets.size(), usage);
        DefinitionTarget target = targets.getFirst();
        TextDocumentSnapshot view = target.snapshot();
        assertDoesNotThrow(() -> target.location().validateAgainst(view));
        assertEquals(name, text(target), usage);
        assertEquals("Greeter.java", view.uri().fileName().orElseThrow());
        assertTrue(workspace.document(view.id()).isEmpty());
        assertEquals(layout == Layout.NONE ? ProvenanceKind.DECOMPILED : ProvenanceKind.ATTACHED_SOURCE,
            view.origin().provenance());
        assertEquals(layout != Layout.NONE, view.text().contains("return \"Hi \" + name;"));
        Path file = view.uri().filePath().orElseThrow();
        assertTrue(file.startsWith(directory.resolve("views")));
        assertFalse(Files.isWritable(file));
        String written = assertDoesNotThrow(() -> Files.readString(file));
        assertEquals(view.text(), written);
        if (following != null) {
            int end = view.text().indexOf(')', target.location().range().end);
            String signature = view.text().substring(target.location().range().end, end + 1);
            assertTrue(signature.contains(following), usage + " selected " + name + signature);
            if (following.equals("(")) {
                assertFalse(signature.contains("int"), usage + " selected " + name + signature);
            }
        }
    }

    private static String text(DefinitionTarget target) {
        return target.snapshot().text().substring(target.location().range().start, target.location().range().end);
    }

    private Path library(Layout layout) throws IOException {
        Path root = directory.resolve(layout.name().toLowerCase());
        Path sourceFile = root.resolve("src/lib/Greeter.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, LIBRARY);
        Path classes = root.resolve("classes");
        var output = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, output, output, "--release", "25",
            "-proc:none", "-d", classes.toString(), sourceFile.toString()), output::toString);

        Path jar = switch (layout) {
            case GRADLE -> root.resolve("files-2.1/example/greeter/1.0/aaaa/greeter-1.0.jar");
            case MAVEN, NONE -> root.resolve("repository/example/greeter/1.0/greeter-1.0.jar");
        };
        Map<String, byte[]> entries = new TreeMap<>();
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                entries.put(classes.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        zip(jar, entries);
        if (layout != Layout.NONE) {
            Path sources = layout == Layout.GRADLE
                ? root.resolve("files-2.1/example/greeter/1.0/bbbb/greeter-1.0-sources.jar")
                : jar.resolveSibling("greeter-1.0-sources.jar");
            zip(sources, Map.of("lib/Greeter.java", LIBRARY.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8)));
        }
        return jar;
    }

    private static void zip(Path archive, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(archive.getParent());
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private DefaultDocumentStore store(Path... classpath) {
        var configuration = new WorkspaceConfigurationSnapshot(0, Set.of("main"), Map.of(
            "module.main.sources", directory.resolve("src").toString(),
            "module.main.classpath", String.join("\n", Stream.of(classpath).map(Path::toString).toList())));
        return new DefaultDocumentStore("test", configuration);
    }

    private static TextDocumentSnapshot document(Path path, String text) {
        var uri = DocumentUri.fromPath(path);
        return new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
    }

    private static FeatureRequestContext context(WorkspaceSnapshot workspace, TextDocumentSnapshot source) {
        return new FeatureRequestContext(workspace, source, CancellationToken.none(), Deadline.none());
    }

    private enum Layout {
        GRADLE,
        MAVEN,
        NONE
    }
}
