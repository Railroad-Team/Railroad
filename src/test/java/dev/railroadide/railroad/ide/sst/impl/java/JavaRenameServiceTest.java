package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceChange;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.sst.provider.api.RenameConflict;
import dev.railroadide.railroad.ide.sst.provider.api.RenamePreparation;
import dev.railroadide.railroad.ide.sst.provider.api.RenameProposal;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class JavaRenameServiceTest {
    @TempDir
    public static Path directory;
    private static JavaSstServices services;
    private static final AtomicInteger WORKSPACES = new AtomicInteger();
    private final String root = "w" + WORKSPACES.incrementAndGet() + "/";
    private final Map<String, TextDocumentSnapshot> documents = new LinkedHashMap<>();
    private DefaultDocumentStore store;

    @BeforeAll
    public static void startServices() {
        services = new JavaSstServices();
    }

    @AfterAll
    public static void closeServices() {
        services.close();
    }

    @Test
    public void renamesLocalVariableFromDeclarationUsageAndIdentifierEnd() {
        source("p/A.java", """
            package p;
            class A {
                int sum(int[] values) {
                    int total = 0;
                    for (int value : values) { total += value; }
                    return total;
                }
            }
            """);
        String expected = """
            package p;
            class A {
                int sum(int[] values) {
                    int result = 0;
                    for (int value : values) { result += value; }
                    return result;
                }
            }
            """;
        assertEquals(expected, renamed("p/A.java", "return total", 7, "result").get("p/A.java"));
        assertEquals(expected, renamed("p/A.java", "total = 0", 0, "result").get("p/A.java"));
        int end = text("p/A.java").indexOf("total = 0") + "total".length();
        assertEquals(expected, applyAt("p/A.java", end, "result").get("p/A.java"));
    }

    @Test
    public void renamesParameterAndItsJavadocTagOnly() {
        source("p/A.java", """
            package p;
            class A {
                /**
                 * Doubles a number. Example syntax: {@code @param amount}.
                 *
                 * @param amount amount to double
                 */
                int twice(int amount) { return amount * 2; }
                int other(int amount) { return amount; }
            }
            """);
        assertEquals("""
            package p;
            class A {
                /**
                 * Doubles a number. Example syntax: {@code @param amount}.
                 *
                 * @param value amount to double
                 */
                int twice(int value) { return value * 2; }
                int other(int amount) { return amount; }
            }
            """, renamed("p/A.java", "amount * 2", 0, "value").get("p/A.java"));
    }

    @Test
    public void renamesFieldAcrossFilesIncludingStaticImports() {
        source("p/Config.java", """
            package p;
            public class Config {
                public static int LIMIT = 3;
                int read() { return LIMIT + Config.LIMIT; }
            }
            """);
        source("q/User.java", """
            package q;
            import static p.Config.LIMIT;
            import p.Config;
            class User {
                int use() { return LIMIT + Config.LIMIT; }
            }
            """);
        Map<String, String> result = renamed("q/User.java", "LIMIT +", 0, "MAX");
        assertEquals("""
            package p;
            public class Config {
                public static int MAX = 3;
                int read() { return MAX + Config.MAX; }
            }
            """, result.get("p/Config.java"));
        assertEquals("""
            package q;
            import static p.Config.MAX;
            import p.Config;
            class User {
                int use() { return MAX + Config.MAX; }
            }
            """, result.get("q/User.java"));
    }

    @Test
    public void renamesOnlyTheSelectedOverload() {
        source("p/Math.java", """
            package p;
            public class Math {
                public static int scale(int value) { return value * 2; }
                public static double scale(double value) { return value * 2; }
            }
            """);
        source("p/Use.java", """
            package p;
            class Use {
                double use() { return Math.scale(1) + Math.scale(1.5); }
            }
            """);
        Map<String, String> result = renamed("p/Use.java", "scale(1)", 0, "doubled");
        assertEquals("""
            package p;
            public class Math {
                public static int doubled(int value) { return value * 2; }
                public static double scale(double value) { return value * 2; }
            }
            """, result.get("p/Math.java"));
        assertEquals("""
            package p;
            class Use {
                double use() { return Math.doubled(1) + Math.scale(1.5); }
            }
            """, result.get("p/Use.java"));
    }

    @Test
    public void renamesWholeOverrideFamilyFromAnyMember() {
        source("p/Shape.java", """
            package p;
            public interface Shape {
                double area();
            }
            """);
        source("p/Square.java", """
            package p;
            public class Square implements Shape {
                @Override public double area() { return 4; }
            }
            """);
        source("q/Big.java", """
            package q;
            import p.Square;
            class Big extends Square {
                @Override public double area() { return super.area() * 2; }
                double use(p.Shape shape) { return shape.area() + area(); }
            }
            """);
        Map<String, String> result = renamed("p/Square.java", "area()", 0, "surface");
        assertTrue(result.get("p/Shape.java").contains("double surface();"));
        assertTrue(result.get("p/Square.java").contains("public double surface() { return 4; }"));
        assertTrue(result.get("q/Big.java").contains("public double surface() { return super.surface() * 2; }"));
        assertTrue(result.get("q/Big.java").contains("return shape.surface() + surface();"));
    }

    @Test
    public void rejectsMethodsOverridingLibraryOrObjectMethods() {
        source("p/Task.java", """
            package p;
            public class Task implements Runnable {
                @Override public void run() {}
                @Override public String toString() { return "task"; }
                public int hashCode() { return 1; }
            }
            """);
        assertRejected("p/Task.java", "run()", "declared in a library");
        assertRejected("p/Task.java", "toString()", "java.lang.Object");
        assertRejected("p/Task.java", "hashCode()", "java.lang.Object");
    }

    @Test
    public void renamesClassFromConstructorWithImportsQualifiedNamesAndFile() {
        source("p/Shape.java", """
            package p;
            /** Created by {@link Shape#Shape(int)}. */
            public class Shape {
                public static final int LIMIT = 3;
                public static class Inner {}
                public Shape(int size) {}
                public Shape() { this(1); }
                Shape copy(Shape other) { return new Shape(); }
            }
            """);
        source("q/User.java", """
            package q;
            import p.Shape;
            import p.Shape.Inner;
            import static p.Shape.LIMIT;
            class User extends Shape {
                p.Shape.Inner first;
                Inner second;
                java.util.List<Shape> all;
                User() { super(LIMIT + Shape.LIMIT); }
                Object create() { return new p.Shape(2) instanceof Shape s ? s : (Shape) null; }
            }
            """);
        RenameProposal proposal = proposal("p/Shape.java", "Shape(int size)", 0, "Figure");
        assertTrue(proposal.applicable(), () -> String.valueOf(proposal.conflicts()));
        Map<String, String> result = apply(proposal.edit());
        assertEquals("""
            package p;
            /** Created by {@link Figure#Shape(int)}. */
            public class Figure {
                public static final int LIMIT = 3;
                public static class Inner {}
                public Figure(int size) {}
                public Figure() { this(1); }
                Figure copy(Figure other) { return new Figure(); }
            }
            """, result.get("p/Shape.java"));
        assertEquals("""
            package q;
            import p.Figure;
            import p.Figure.Inner;
            import static p.Figure.LIMIT;
            class User extends Figure {
                p.Figure.Inner first;
                Inner second;
                java.util.List<Figure> all;
                User() { super(LIMIT + Figure.LIMIT); }
                Object create() { return new p.Figure(2) instanceof Figure s ? s : (Figure) null; }
            }
            """, result.get("q/User.java"));
        WorkspaceChange.RenameFile file = proposal.edit().changes().stream()
            .filter(WorkspaceChange.RenameFile.class::isInstance).map(WorkspaceChange.RenameFile.class::cast)
            .findFirst().orElseThrow();
        assertEquals(documents.get("p/Shape.java").id(), file.expected().id());
        assertEquals(DocumentUri.fromPath(path("p/Figure.java")), file.newUri());
    }

    @Test
    public void renamesNestedInterfaceRecordAndEnumWithoutRenamingFiles() {
        source("p/Outer.java", """
            package p;
            public class Outer {
                public interface Listener { void changed(); }
                public record Point(int x, int y) {}
                public enum Mode { ON, OFF }
                Point origin = new Point(0, 0);
                Mode mode = Mode.ON;
            }
            """);
        source("q/Use.java", """
            package q;
            import p.Outer.Listener;
            import p.Outer;
            class Use implements Listener {
                Outer.Point point;
                Outer.Mode mode;
                public void changed() {}
            }
            """);
        RenameProposal listener = proposal("q/Use.java", "Listener {", 0, "Observer");
        assertTrue(listener.applicable(), () -> String.valueOf(listener.conflicts()));
        assertTrue(listener.edit().changes().stream().noneMatch(WorkspaceChange.RenameFile.class::isInstance));
        Map<String, String> result = apply(listener.edit());
        assertTrue(result.get("p/Outer.java").contains("public interface Observer { void changed(); }"));
        assertTrue(result.get("q/Use.java").contains("import p.Outer.Observer;"));
        assertTrue(result.get("q/Use.java").contains("class Use implements Observer {"));

        Map<String, String> record = renamed("p/Outer.java", "Point(0", 0, "Location");
        assertTrue(record.get("p/Outer.java").contains("public record Location(int x, int y) {}"));
        assertTrue(record.get("p/Outer.java").contains("Location origin = new Location(0, 0);"));
        assertTrue(record.get("q/Use.java").contains("Outer.Location point;"));

        Map<String, String> enumeration = renamed("q/Use.java", "Mode mode", 0, "State");
        assertTrue(enumeration.get("p/Outer.java").contains("public enum State { ON, OFF }"));
        assertTrue(enumeration.get("p/Outer.java").contains("State mode = State.ON;"));
        assertTrue(enumeration.get("q/Use.java").contains("Outer.State mode;"));
    }

    @Test
    public void renamesEnumConstantIncludingSwitchLabels() {
        source("p/Mode.java", """
            package p;
            public enum Mode { ON, OFF }
            """);
        source("p/Use.java", """
            package p;
            class Use {
                int code(Mode mode) {
                    switch (mode) { case ON: return 1; default: return 0; }
                }
                int rule(Mode mode) { return switch (mode) { case ON -> 1; case OFF -> 0; }; }
                boolean on(Mode mode) { return mode == Mode.ON; }
            }
            """);
        Map<String, String> result = renamed("p/Use.java", "ON;", 0, "ENABLED");
        assertEquals("""
            package p;
            public enum Mode { ENABLED, OFF }
            """, result.get("p/Mode.java"));
        assertEquals("""
            package p;
            class Use {
                int code(Mode mode) {
                    switch (mode) { case ENABLED: return 1; default: return 0; }
                }
                int rule(Mode mode) { return switch (mode) { case ENABLED -> 1; case OFF -> 0; }; }
                boolean on(Mode mode) { return mode == Mode.ENABLED; }
            }
            """, result.get("p/Use.java"));
    }

    @Test
    public void reportsFieldShadowedByLocalVariable() {
        source("p/Counter.java", """
            package p;
            class Counter {
                int count;
                int next() {
                    int step = 1;
                    return count + step;
                }
            }
            """);
        assertConflict(proposal("p/Counter.java", "count;", 0, "step"), "would refer to local variable 'step'");
    }

    @Test
    public void reportsLocalRedeclarationButAllowsSiblingScopes() {
        source("p/Scopes.java", """
            package p;
            class Scopes {
                void run(int limit) {
                    int first = 1;
                    { int inner = first; }
                    { int other = limit; }
                    int later = 2;
                    Runnable task = () -> { int lambda = later; };
                }
            }
            """);
        assertConflict(proposal("p/Scopes.java", "first = 1", 0, "limit"), "already declared in this scope");
        assertConflict(proposal("p/Scopes.java", "inner =", 0, "first"), "already declared in this scope");
        assertConflict(proposal("p/Scopes.java", "lambda =", 0, "task"), "already declared in this scope");
        assertTrue(renamed("p/Scopes.java", "inner =", 0, "other").get("p/Scopes.java")
            .contains("{ int other = first; }"));
        assertTrue(renamed("p/Scopes.java", "inner =", 0, "later").get("p/Scopes.java")
            .contains("{ int later = first; }"));
    }

    @Test
    public void reportsNameCollisions() {
        source("p/Store.java", """
            package p;
            public class Store {
                int size;
                int capacity;
                int get(int index) { return index; }
                int fetch(int index) { return index; }
                int fetch(long index) { return 0; }
            }
            class Helper {}
            """);
        source("p/Item.java", """
            package p;
            class Item {
                int use(Store store) { return store.get(1) + store.fetch(2L); }
            }
            """);
        assertConflict(proposal("p/Store.java", "size;", 0, "capacity"), "already declared");
        assertConflict(proposal("p/Store.java", "get(int", 0, "fetch"), "already declared");
        assertConflict(proposal("p/Item.java", "Item {", 0, "Helper"), "already declared");
        assertConflict(proposal("p/Item.java", "Item {", 0, "Store"), "Store.java already exists");
    }

    @Test
    public void respectsFilesystemCaseSensitivityWhenCheckingRenameDestination() {
        source("p/Item.java", "package p; class Item {}");
        source("p/STORE.java", "package p; class Other {}");
        RenameProposal proposal = proposal("p/Item.java", "Item", 0, "Store");
        if (path("p/Store.java").equals(path("p/STORE.java"))) {
            assertConflict(proposal, "Store.java already exists");
        } else {
            assertTrue(proposal.applicable(), () -> String.valueOf(proposal.conflicts()));
        }
    }

    @Test
    public void reportsOverloadResolutionChanges() {
        source("p/Calls.java", """
            package p;
            class Calls {
                void accept(long value) {}
                void take(int value) {}
                void use() { accept(5); take(5); }
            }
            """);
        assertConflict(proposal("p/Calls.java", "take(int", 0, "accept"), "would refer to method 'accept'");
    }

    @Test
    public void reportsAccidentalOverridesAndHierarchyClashes() {
        source("p/Base.java", """
            package p;
            public class Base {
                public void start() {}
                public void stop() {}
            }
            """);
        source("p/Child.java", """
            package p;
            class Child extends Base {
                public void halt() {}
            }
            """);
        assertConflict(proposal("p/Child.java", "halt()", 0, "start"), "would clash with or override");
        assertConflict(proposal("p/Base.java", "stop()", 0, "halt"), "would clash with or override");
        assertConflict(proposal("p/Child.java", "halt()", 0, "toString"), "java.lang.Object");
    }

    @Test
    public void reportsNestedTypeNamedLikeItsEnclosingType() {
        source("p/Outer.java", """
            package p;
            public class Outer {
                static class Inner {}
            }
            """);
        assertConflict(proposal("p/Outer.java", "Inner {", 0, "Outer"), "enclosing type");
        assertConflict(proposal("p/Outer.java", "Outer {", 0, "Inner"), "nested type");
    }

    @Test
    public void rejectsInvalidAndUnchangedNames() {
        source("p/Names.java", """
            package p;
            class Names {
                int value;
            }
            """);
        for (String name : List.of("", "1value", "class", "true", "_", "has space", "a-b", "value")) {
            RenameProposal proposal = proposal("p/Names.java", "value;", 0, name);
            assertFalse(proposal.applicable(), name);
        }
        assertConflict(proposal("p/Names.java", "Names {", 0, "var"), "cannot be used as a type name");
        assertConflict(proposal("p/Names.java", "Names {", 0, "New\0Name"), "cannot be used as a file name");
        assertTrue(proposal("p/Names.java", "value;", 0, "élève").applicable());
    }

    @Test
    public void prepareRejectsUnsupportedUnresolvedAndAmbiguousPositions() {
        source("p/Prepare.java", """
            package p;
            class Prepare<T> {
                record Pair(int left, int right) {}
                String name = "x";
                int use(Pair pair) {
                    Object anonymous = new Object() {};
                    return pair.left() + missing + this.name.length();
                }
            }
            """);
        assertRejected("p/Prepare.java", "missing", "Cannot resolve the symbol 'missing'");
        assertRejected("p/Prepare.java", "String name", "library");
        assertRejected("p/Prepare.java", "length()", "library");
        assertRejected("p/Prepare.java", "left,", "record components");
        assertRejected("p/Prepare.java", "  return", "Place the caret");
        assertRejected("p/Prepare.java", "T> {", "not supported");

        RenamePreparation prepared = prepare("p/Prepare.java", "name =", 2);
        var renameable = assertInstanceOf(RenamePreparation.Renameable.class, prepared);
        assertEquals("name", renameable.name());
        assertEquals("field", renameable.kind());
        assertEquals(text("p/Prepare.java").indexOf("name ="), renameable.range().range().start);
    }

    @Test
    public void rejectsTypesDeclaredMoreThanOnce() {
        source("p/Dup.java", "package p; class Dup {}");
        source("p/Copy.java", "package p; class Dup {} class UseDup { Dup dup; }");
        assertRejected("p/Copy.java", "Dup dup", "more than once");
    }

    @Test
    public void renamesSymbolsInUnsavedDocuments() {
        source("p/Draft.java", """
            package p;
            class Draft {
                int saved;
            }
            """);
        source("p/Reader.java", """
            package p;
            class Reader {
                int read(Draft draft) { return 0; }
            }
            """);
        TextDocumentSnapshot unsaved = overlay("p/Draft.java", """
            package p;
            class Draft {
                int unsaved;
                int twice() { return unsaved * 2; }
            }
            """);
        overlay("p/Reader.java", """
            package p;
            class Reader {
                int read(Draft draft) { return draft.unsaved + draft.twice(); }
            }
            """);
        RenameProposal proposal = proposal("p/Draft.java", "unsaved;", 0, "pending");
        assertTrue(proposal.applicable(), () -> String.valueOf(proposal.conflicts()));
        WorkspaceChange.TextEdit edit = proposal.edit().changes().stream()
            .filter(WorkspaceChange.TextEdit.class::isInstance).map(WorkspaceChange.TextEdit.class::cast)
            .filter(change -> change.expected().id().equals(unsaved.id())).findFirst().orElseThrow();
        assertSame(unsaved, edit.expected());
        Map<String, String> result = apply(proposal.edit());
        assertTrue(result.get("p/Draft.java").contains("int pending;"));
        assertTrue(result.get("p/Draft.java").contains("return pending * 2;"));
        assertTrue(result.get("p/Reader.java").contains("return draft.pending + draft.twice();"));
    }

    @Test
    public void warnsAboutUnresolvedOccurrencesWithoutRenamingThem() {
        source("p/Box.java", """
            package p;
            class Box {
                int width;
                int area(Box box) { return box.width; }
                int guess(Object unknown) { return Missing.width; }
            }
            """);
        RenameProposal proposal = proposal("p/Box.java", "width;", 0, "breadth");
        assertTrue(proposal.applicable(), () -> String.valueOf(proposal.conflicts()));
        List<RenameConflict> warnings = proposal.conflicts(RenameConflict.Severity.WARNING);
        assertEquals(1, warnings.size(), String.valueOf(warnings));
        assertTrue(warnings.getFirst().message().contains("could not be resolved"));
        String result = apply(proposal.edit()).get("p/Box.java");
        assertTrue(result.contains("int breadth;"));
        assertTrue(result.contains("return box.breadth;"));
        assertTrue(result.contains("return Missing.width;"));
    }

    @Test
    public void requestsCaretSelectionOnTheRenamedIdentifier() {
        source("p/Select.java", """
            package p;
            class Select {
                int alpha;
                int read() { return alpha; }
            }
            """);
        RenameProposal proposal = proposal("p/Select.java", "alpha; }", 0, "beta");
        String result = apply(proposal.edit()).get("p/Select.java");
        int start = result.indexOf("beta; }");
        assertEquals(documents.get("p/Select.java").id(), proposal.edit().selection().document());
        assertEquals(start, proposal.edit().selection().selection().anchor());
        assertEquals(start + 4, proposal.edit().selection().selection().caret());
    }

    private void assertRejected(String file, String marker, String reason) {
        var rejected = assertInstanceOf(RenamePreparation.Rejected.class, prepare(file, marker, 0), marker);
        assertTrue(rejected.reason().contains(reason), rejected.reason());
        RenameProposal proposal = proposal(file, marker, 0, "renamed");
        assertFalse(proposal.applicable());
    }

    private static void assertConflict(RenameProposal proposal, String message) {
        assertFalse(proposal.applicable(), "Expected a conflict containing: " + message);
        assertTrue(proposal.conflicts(RenameConflict.Severity.ERROR).stream()
            .anyMatch(conflict -> conflict.message().contains(message)),
            () -> "Expected '" + message + "' in " + proposal.conflicts());
    }

    private Map<String, String> renamed(String file, String marker, int delta, String newName) {
        return applyAt(file, text(file).indexOf(marker) + delta, newName);
    }

    private Map<String, String> applyAt(String file, int offset, String newName) {
        RenameProposal proposal = services.rename(context(file), offset, newName).value();
        assertTrue(proposal.applicable(), () -> String.valueOf(proposal.conflicts()));
        assertTrue(proposal.conflicts().isEmpty(), () -> String.valueOf(proposal.conflicts()));
        return apply(proposal.edit());
    }

    private RenameProposal proposal(String file, String marker, int delta, String newName) {
        int offset = text(file).indexOf(marker);
        assertTrue(offset >= 0, marker);
        return services.rename(context(file), offset + delta, newName).value();
    }

    private RenamePreparation prepare(String file, String marker, int delta) {
        int offset = text(file).indexOf(marker);
        assertTrue(offset >= 0, marker);
        return services.prepareRename(context(file), offset + delta).value();
    }

    private Map<String, String> apply(WorkspaceEdit edit) {
        Map<String, String> result = new LinkedHashMap<>();
        documents.forEach((name, document) -> result.put(name, document.text()));
        for (WorkspaceChange change : edit.changes()) {
            if (!(change instanceof WorkspaceChange.TextEdit text))
                continue;
            String name = documents.entrySet().stream()
                .filter(entry -> entry.getValue().id().equals(text.expected().id())).findFirst().orElseThrow()
                .getKey();
            assertSame(documents.get(name), text.expected());
            var builder = new StringBuilder(text.expected().text());
            text.replacements().stream()
                .sorted(Comparator.comparingInt((TextReplacement replacement) -> replacement.range().start)
                    .reversed())
                .forEach(replacement -> builder.replace(replacement.range().start, replacement.range().end,
                    replacement.text()));
            result.put(name, builder.toString());
        }
        return result;
    }

    private FeatureRequestContext context(String file) {
        return new FeatureRequestContext(store().capture(), documents.get(file), CancellationToken.none(),
            Deadline.none());
    }

    private String text(String file) {
        return documents.get(file).text();
    }

    private Path path(String file) {
        return directory.resolve(root + file);
    }

    private void source(String file, String text) {
        var uri = DocumentUri.fromPath(path(file));
        var document = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
        documents.put(file, document);
        store().publishBacking(document);
    }

    private TextDocumentSnapshot overlay(String file, String text) {
        TextDocumentSnapshot saved = documents.get(file);
        var document = new TextDocumentSnapshot(saved.id(), saved.uri(), saved.version().next(), "java",
            saved.origin(), text, StandardCharsets.UTF_8);
        documents.put(file, document);
        store().publishOverlay(document);
        return document;
    }

    private DefaultDocumentStore store() {
        if (store == null) {
            var configuration = new WorkspaceConfigurationSnapshot(0, Set.of("main"),
                Map.of("module.main.sources", directory.toString()));
            store = new DefaultDocumentStore("rename", configuration);
        }
        return store;
    }
}
