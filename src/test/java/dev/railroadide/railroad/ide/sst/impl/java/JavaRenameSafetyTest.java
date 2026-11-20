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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renames real programs and checks the results with the Java compiler: an accepted rename must compile and behave
 * exactly as before, and a rejected one must be one that would otherwise break or change the program.
 */
public class JavaRenameSafetyTest {
    private static final String MAIN = """
        package p;
        public class Main {
            public static String run() { return %s; }
        }
        """;

    @TempDir
    public static Path directory;
    private static JavaSstServices services;
    private static final AtomicInteger WORKSPACES = new AtomicInteger();
    private final String root = "safety" + WORKSPACES.incrementAndGet() + "/";
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

    // region Overload resolution

    @Test
    public void rejectsAnOverloadThatWouldMakeAnExistingCallAmbiguous() {
        source("p/Text.java", """
            package p;
            public class Text {
                static String describe(CharSequence value) { return "sequence"; }
                static String compare(Comparable<String> value) { return "comparable"; }
            }
            """);
        main("Text.describe(\"x\")");
        assertEquals("sequence", run());

        assertConflict(rename("p/Text.java", "compare(", "describe"), "would overload describe(");
        assertFalse(CompiledJava.errors(directory, naive("p/Text.java", "compare(", "describe(")).isEmpty(),
            "Applying the rename regardless must not compile, or the test proves nothing");
    }

    @Test
    public void rejectsAnOverloadThatWouldChangeRuntimeDispatch() {
        source("p/Base.java", """
            package p;
            public class Base {
                String accept(Object value) { return "object"; }
            }
            """);
        source("p/Child.java", """
            package p;
            public class Child extends Base {
                String take(String value) { return "string"; }
            }
            """);
        main("new Child().accept(\"x\")");
        assertEquals("object", run());

        assertConflict(rename("p/Child.java", "take(", "accept"), "would overload accept(java.lang.Object)");
        assertEquals("string", CompiledJava.run(directory, naive("p/Child.java", "take(", "accept("), "p.Main"),
            "Applying the rename regardless must silently change which method runs");
    }

    @Test
    public void rejectsAnOverloadCompetingForALambdaArgument() {
        source("p/Tasks.java", """
            package p;
            import java.util.function.Supplier;
            public class Tasks {
                static String submit(Supplier<String> task) { return "supplier " + task.get(); }
                static String execute(Runnable task) { task.run(); return "runnable"; }
            }
            """);
        main("Tasks.submit(() -> String.valueOf(1))");
        assertEquals("supplier 1", run());

        assertConflict(rename("p/Tasks.java", "execute(", "submit"), "would overload submit(");
    }

    @Test
    public void allowsAnOverloadThatNoArgumentCouldSelect() {
        source("p/Sink.java", """
            package p;
            public class Sink {
                static String accept(String value) { return "string " + value; }
                static String take(int value) { return "int " + value; }
            }
            """);
        main("Sink.accept(\"x\") + \", \" + Sink.take(5)");
        assertEquals("string x, int 5", run());

        Map<String, String> renamed = applied(rename("p/Sink.java", "take(", "accept"));
        assertTrue(renamed.get("p/Main.java").contains("Sink.accept(5)"));
        assertEquals("string x, int 5", run(renamed));
    }

    @Test
    public void rejectsAnyOverloadOfAMethodReferencedName() {
        source("p/Refs.java", """
            package p;
            import java.util.function.Consumer;
            import java.util.function.IntConsumer;
            public class Refs {
                static String apply(IntConsumer consumer) { return "int"; }
                static String apply(Consumer<String> consumer) { return "string"; }
                static void take(int value) {}
                static void pair(boolean flag) {}
                static void both(int first, int second) {}
                public static String run() { return apply(Refs::take); }
            }
            """);
        main("Refs.run()");
        assertEquals("int", run());

        assertConflict(rename("p/Refs.java", "pair(", "take"), "would overload take(int)");
        assertConflict(rename("p/Refs.java", "both(", "take"), "would overload take(int)");
        assertFalse(CompiledJava.errors(directory, naive("p/Refs.java", "both(", "take(")).isEmpty());
    }

    @Test
    public void rejectsAnOverloadOfAMethodReferencePassedToAnExecutor() {
        source("p/Jobs.java", """
            package p;
            import java.util.concurrent.Callable;
            public class Jobs {
                static String submit(Runnable task) { return "runnable"; }
                static <T> String submit(Callable<T> task) { return "callable"; }
                static String compute() { return "x"; }
                static String other(int value) { return "y"; }
                public static String run() { return submit(Jobs::compute); }
            }
            """);
        main("Jobs.run()");
        assertEquals("callable", run());

        assertConflict(rename("p/Jobs.java", "other(", "compute"), "would overload compute()");
        assertFalse(CompiledJava.errors(directory, naive("p/Jobs.java", "other(", "compute(")).isEmpty());
    }

    // endregion

    // region Inherited implementations

    @Test
    public void rejectsRenamingAnInterfaceMethodAnEnumImplementsThroughEnum() {
        source("p/Named.java", """
            package p;
            public interface Named { String name(); }
            """);
        source("p/Color.java", """
            package p;
            public enum Color implements Named { RED }
            """);
        main("((Named) Color.RED).name()");
        assertEquals("RED", run());

        assertConflict(rename("p/Named.java", "name()", "label"), "would no longer implement it");
    }

    @Test
    public void rejectsRenamingAnInterfaceMethodARecordImplementsWithItsAccessor() {
        source("p/HasX.java", """
            package p;
            public interface HasX { int x(); }
            """);
        source("p/Point.java", """
            package p;
            public record Point(int x) implements HasX {}
            """);
        main("\"\" + ((HasX) new Point(3)).x()");
        assertEquals("3", run());

        assertConflict(rename("p/HasX.java", "x()", "getX"), "would change its accessor");
        assertConflict(rename("p/Main.java", "x()", "getX"), "would change its accessor");
    }

    @Test
    public void rejectsRenamingAnImplicitRecordAccessorAtItsCallSite() {
        source("p/Point.java", """
            package p;
            public record Point(int x) {}
            """);
        main("\"\" + new Point(3).x()");
        assertEquals("3", run());

        assertConflict(rename("p/Main.java", "x()", "getX"), "would change its accessor");
    }

    @Test
    public void rejectsGivingARecordMethodTheNameOfAComponent() {
        source("p/Pair.java", """
            package p;
            public record Pair(int left) {
                public int other() { return 42; }
            }
            """);
        main("\"\" + new Pair(1).left() + new Pair(1).other()");
        assertEquals("142", run());

        assertConflict(rename("p/Pair.java", "other()", "left"), "is a component of record");
        Map<String, String> naive = naive("p/Pair.java", "other()", "left()");
        naive.put("p/Main.java", naive.get("p/Main.java").replace("other()", "left()"));
        assertEquals("4242", CompiledJava.run(directory, naive, "p.Main"),
            "Applying the rename regardless must silently replace the accessor");
    }

    @Test
    public void doesNotRenameAPackagePrivateMethodThatIsNotInherited() {
        source("a/Base.java", """
            package a;
            public class Base {
                String draw() { return "package"; }
                public String own() { return draw(); }
            }
            """);
        source("p/Shape.java", """
            package p;
            public interface Shape { String draw(); }
            """);
        source("p/Circle.java", """
            package p;
            public class Circle extends a.Base implements Shape {
                public String draw() { return "circle"; }
            }
            """);
        main("((Shape) new Circle()).draw() + new Circle().own()");
        assertEquals("circlepackage", run());

        Map<String, String> renamed = applied(rename("p/Shape.java", "draw()", "paint"));
        assertEquals(texts().get("a/Base.java"), renamed.get("a/Base.java"));
        assertEquals("circlepackage", run(renamed));
    }

    @Test
    public void renamingAnInterfaceMethodRenamesTheImplementationASubclassInherits() {
        inheritedImplementation();
        Map<String, String> renamed = applied(rename("p/Shape.java", "draw()", "paint"));
        assertTrue(renamed.get("p/Base.java").contains("public String paint()"), renamed::toString);
        assertEquals("base base", run(renamed));
    }

    @Test
    public void renamingAnInheritedImplementationRenamesTheInterfaceMethod() {
        inheritedImplementation();
        Map<String, String> renamed = applied(rename("p/Base.java", "draw()", "paint"));
        assertTrue(renamed.get("p/Shape.java").contains("String paint();"), renamed::toString);
        assertEquals("base base", run(renamed));
    }

    @Test
    public void renamesAnInheritedImplementationOfAGenericInterface() {
        source("p/Sink.java", """
            package p;
            public interface Sink<T> { String put(T value); }
            """);
        source("p/Base.java", """
            package p;
            public class Base { public String put(String value) { return "put " + value; } }
            """);
        source("p/Store.java", """
            package p;
            public class Store extends Base implements Sink<String> {}
            """);
        main("((Sink<String>) new Store()).put(\"x\")");
        assertEquals("put x", run());

        Map<String, String> renamed = applied(rename("p/Sink.java", "put(", "store"));
        assertTrue(renamed.get("p/Base.java").contains("public String store(String value)"), renamed::toString);
        assertEquals("put x", run(renamed));
    }

    @Test
    public void rejectsRenamingAnInheritedImplementationOfALibraryInterface() {
        source("p/Base.java", """
            package p;
            public class Base { public void run() {} }
            """);
        source("p/Task.java", """
            package p;
            public class Task extends Base implements Runnable {}
            """);
        main("\"\"");
        run();

        var rejected = assertInstanceOf(RenamePreparation.Rejected.class,
            services.prepareRename(context("p/Base.java"), offset("p/Base.java", "run()")).value());
        assertTrue(rejected.reason().contains("declared in a library"), rejected.reason());
    }

    @Test
    public void rejectsARenameThatMakesAnInheritedMethodImplementAnInterface() {
        source("p/Shape.java", """
            package p;
            public interface Shape { String draw(); }
            """);
        source("p/Base.java", """
            package p;
            public abstract class Base { public String paint() { return "paint"; } }
            """);
        source("p/Circle.java", """
            package p;
            public abstract class Circle extends Base implements Shape {}
            """);
        main("\"\"");
        run();

        assertConflict(rename("p/Base.java", "paint()", "draw"), "would start implementing");
    }

    private void inheritedImplementation() {
        source("p/Shape.java", """
            package p;
            public interface Shape { String draw(); }
            """);
        source("p/Base.java", """
            package p;
            public class Base { public String draw() { return "base"; } }
            """);
        source("p/Circle.java", """
            package p;
            public class Circle extends Base implements Shape {}
            """);
        source("p/Square.java", """
            package p;
            public class Square extends Base {}
            """);
        main("((Shape) new Circle()).draw() + \" \" + new Square().draw()");
        assertEquals("base base", run());
    }

    // endregion

    // region Annotation elements

    @Test
    public void renamingTheValueElementNamesItInShorthandUsages() {
        source("p/Tag.java", """
            package p;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Tag {
                String[] value();
                int weight() default 0;
            }
            """);
        source("p/Use.java", """
            package p;
            @Tag("type")
            public class Use {
                @Tag({ "first", "second" }) public int array;
                @Tag(value = "pair", weight = 2) public int pair;
                @Tag(/* inline */ "commented") public int commented;
                public static String read() throws Exception {
                    return String.join(",", Use.class.getAnnotation(Tag.class).value()) + " "
                        + String.join(",", Use.class.getField("array").getAnnotation(Tag.class).value()) + " "
                        + Use.class.getField("pair").getAnnotation(Tag.class).value()[0] + " "
                        + Use.class.getField("commented").getAnnotation(Tag.class).value()[0];
                }
            }
            """);
        mainThrowing("Use.read()");
        String expected = "type first,second pair commented";
        assertEquals(expected, run());

        Map<String, String> renamed = applied(rename("p/Tag.java", "value();", "names"));
        String use = renamed.get("p/Use.java");
        assertTrue(use.contains("@Tag(names = \"type\")"), use);
        assertTrue(use.contains("@Tag(names = { \"first\", \"second\" })"), use);
        assertTrue(use.contains("@Tag(names = \"pair\", weight = 2)"), use);
        assertTrue(use.contains("@Tag(/* inline */ names = \"commented\")"), use);
        assertEquals(expected, run(renamed));
    }

    @Test
    public void rejectsRenamingTheValueOfARepeatableContainer() {
        source("p/Tag.java", """
            package p;
            import java.lang.annotation.Repeatable;
            @Repeatable(Tags.class)
            public @interface Tag { String value(); }
            """);
        source("p/Tags.java", """
            package p;
            public @interface Tags { Tag[] value(); }
            """);
        source("p/Use.java", """
            package p;
            @Tag("a") @Tag("b")
            public class Use {}
            """);
        main("\"\"");
        run();

        assertConflict(rename("p/Tags.java", "value()", "items"), "container of a @Repeatable");
        assertFalse(CompiledJava.errors(directory, naive("p/Tags.java", "value()", "items()")).isEmpty());
    }

    @Test
    public void rejectsNamingAnElementAfterAnAnnotationMethod() {
        source("p/Kind.java", """
            package p;
            public @interface Kind { Class<?> type(); }
            """);
        source("p/Use.java", """
            package p;
            @Kind(type = String.class)
            public class Use {}
            """);
        main("\"\"");
        run();

        assertConflict(rename("p/Kind.java", "type()", "annotationType"), "java.lang.annotation.Annotation");
    }

    @Test
    public void rejectsRenamingAnElementUsedInAnAnnotationThatIsNotResolved() {
        source("p/Tag.java", """
            package p;
            public @interface Tag { String value(); }
            """);
        source("p/Holder.java", """
            package p;
            public @interface Holder { Tag value(); }
            """);
        source("p/Use.java", """
            package p;
            @Tag("type")
            public class Use {
                @Holder(@Tag("nested")) public int nested;
            }
            """);
        main("\"\"");
        run();

        assertConflict(rename("p/Tag.java", "value()", "names"), "could not be resolved");
        Map<String, String> holder = applied(rename("p/Holder.java", "value()", "tag"));
        assertTrue(holder.get("p/Use.java").contains("@Holder(tag = @Tag(\"nested\"))"), holder::toString);
        assertEquals("", run(holder));
    }

    @Test
    public void renamingAnElementToValueKeepsItsUsagesValid() {
        source("p/Label.java", """
            package p;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Label { String text(); }
            """);
        source("p/Use.java", """
            package p;
            @Label(text = "named")
            public class Use {
                public static String read() { return Use.class.getAnnotation(Label.class).text(); }
            }
            """);
        main("Use.read()");
        assertEquals("named", run());

        Map<String, String> renamed = applied(rename("p/Label.java", "text()", "value"));
        assertTrue(renamed.get("p/Use.java").contains("@Label(value = \"named\")"), renamed::toString);
        assertEquals("named", run(renamed));
    }

    // endregion

    // region Unqualified calls

    @Test
    public void rejectsRenamingAMethodToYieldWhenItIsCalledUnqualified() {
        source("p/Steps.java", """
            package p;
            public class Steps {
                static String next() { return "next"; }
                static String twice() { return next() + next(); }
            }
            """);
        main("Steps.twice()");
        assertEquals("nextnext", run());

        assertConflict(rename("p/Steps.java", "next()", "yield"), "unqualified call to 'yield'");
        assertFalse(CompiledJava.errors(directory, naiveAll("p/Steps.java", "next()", "yield()")).isEmpty());
    }

    @Test
    public void allowsRenamingAMethodToYieldWhenEveryCallIsQualified() {
        source("p/Steps.java", """
            package p;
            public class Steps {
                static String next() { return "next"; }
            }
            """);
        main("Steps.next() + new Steps().toString().isEmpty()");
        String expected = run();

        Map<String, String> renamed = applied(rename("p/Steps.java", "next()", "yield"));
        assertTrue(renamed.get("p/Main.java").contains("Steps.yield()"));
        assertEquals(expected, run(renamed));
    }

    @Test
    public void rejectsARenameThatShadowsAnOuterMethodForUnqualifiedCalls() {
        source("p/Outer.java", """
            package p;
            public class Outer {
                String bar() { return "outer"; }
                public class Inner {
                    String foo(int value) { return "inner"; }
                    public String run() { return bar(); }
                }
            }
            """);
        main("new Outer().new Inner().run()");
        assertEquals("outer", run());

        assertConflict(rename("p/Outer.java", "foo(", "bar"), "would be resolved among the 'bar' methods");
        assertFalse(CompiledJava.errors(directory, naive("p/Outer.java", "foo(", "bar(")).isEmpty());
    }

    @Test
    public void rejectsARenameWhoseUnqualifiedCallsAnInnerMethodWouldCapture() {
        source("p/Outer.java", """
            package p;
            public class Outer {
                String go() { return "outer"; }
                public class Inner {
                    String bar(int value) { return "inner"; }
                    public String run() { return go(); }
                }
            }
            """);
        main("new Outer().new Inner().run()");
        assertEquals("outer", run());

        assertConflict(rename("p/Outer.java", "go()", "bar"), "would be resolved among the 'bar' methods");
        assertFalse(CompiledJava.errors(directory, naiveAll("p/Outer.java", "go()", "bar()")).isEmpty());
    }

    @Test
    public void rejectsARenameThatShadowsAStaticImport() {
        source("p/Util.java", """
            package p;
            public class Util { public static String bar() { return "util"; } }
            """);
        source("p/Use.java", """
            package p;
            import static p.Util.bar;
            public class Use {
                String foo(int value) { return "use"; }
                public String call() { return bar(); }
            }
            """);
        main("new Use().call()");
        assertEquals("util", run());

        assertConflict(rename("p/Use.java", "foo(", "bar"), "would be resolved among the 'bar' methods");
        assertFalse(CompiledJava.errors(directory, naive("p/Use.java", "foo(", "bar(")).isEmpty());
    }

    @Test
    public void rejectsARenameWhoseCallsAnAnonymousLibrarySubclassWouldCapture() {
        source("p/Report.java", """
            package p;
            import java.util.ArrayList;
            public class Report {
                String label() { return "report"; }
                public String run() {
                    return new ArrayList<String>() {
                        String describe() { return label(); }
                    }.describe();
                }
            }
            """);
        main("new Report().run()");
        assertEquals("report", run());

        assertConflict(rename("p/Report.java", "label()", "size"), "would be resolved among the 'size' methods");
        assertFalse(CompiledJava.errors(directory, naiveAll("p/Report.java", "label()", "size()")).isEmpty());
    }

    @Test
    public void resolvesAnonymousClassConstructorArgumentsInTheEnclosingType() {
        source("p/Base.java", """
            package p;
            public class Base {
                public Base(String value) {}
                String bar(int value) { return "base"; }
            }
            """);
        source("p/Outer.java", """
            package p;
            public class Outer {
                String go() { return "outer"; }
                public String run() {
                    return new Base(go()) { String call() { return "anonymous"; } }.call() + go();
                }
            }
            """);
        main("new Outer().run()");
        assertEquals("anonymousouter", run());

        Map<String, String> renamed = applied(rename("p/Outer.java", "go()", "bar"));
        assertEquals("anonymousouter", run(renamed));
    }

    @Test
    public void allowsRenamingAnOuterMethodCalledFromAnInnerClassWithoutThatName() {
        source("p/Outer.java", """
            package p;
            public class Outer {
                String go() { return "outer"; }
                public class Inner {
                    String other(int value) { return "inner"; }
                    public String run() { return go() + new Object() { String call() { return go(); } }.call(); }
                }
            }
            """);
        main("new Outer().new Inner().run()");
        assertEquals("outerouter", run());

        Map<String, String> renamed = applied(rename("p/Outer.java", "go()", "travel"));
        assertEquals("outerouter", run(renamed));
    }

    // endregion

    // region Workspace changes

    @Test
    public void rejectsTheRenameWhenAnExternalChangeMovesTheCaretToAnotherOverload() {
        overloadWorkspace();
        RenamePreparation.Renameable prepared = prepared("p/Use.java", "calc(5)");
        external("p/Calc.java", """
            package p;
            public class Calc {
                static String calc(long value) { return "long"; }
                static String calc(int value) { return "int"; }
            }
            """);

        RenameProposal proposal = renameAs("p/Use.java", "calc(5)", prepared, "compute");
        assertConflict(proposal, "refers to a different symbol");
    }

    @Test
    public void rejectsTheRenameWhenAnUnsavedChangeMovesTheCaretToAnotherOverload() {
        overloadWorkspace();
        RenamePreparation.Renameable prepared = prepared("p/Use.java", "calc(5)");
        overlay("p/Calc.java", """
            package p;
            public class Calc {
                static String calc(long value) { return "long"; }
                static String calc(int value) { return "int"; }
            }
            """);

        assertConflict(renameAs("p/Use.java", "calc(5)", prepared, "compute"), "refers to a different symbol");
    }

    @Test
    public void keepsThePreparedSymbolAcrossUnrelatedExternalAndUnsavedChanges() {
        overloadWorkspace();
        RenamePreparation.Renameable prepared = prepared("p/Use.java", "calc(5)");
        external("p/Other.java", """
            package p;
            public class Other { public static String twice() { return Calc.calc(1L) + Calc.calc(2L); } }
            """);
        overlay("p/Main.java", MAIN.formatted("Use.call() + Other.twice()"));
        assertEquals("longlonglong", run());

        RenameProposal proposal = renameAs("p/Use.java", "calc(5)", prepared, "compute");
        Map<String, String> renamed = applied(proposal);
        assertTrue(renamed.get("p/Other.java").contains("Calc.compute(1L) + Calc.compute(2L)"), renamed::toString);
        assertEquals("longlonglong", run(renamed));
        WorkspaceChange.TextEdit main = proposal.edit().changes().stream()
            .filter(WorkspaceChange.TextEdit.class::isInstance).map(WorkspaceChange.TextEdit.class::cast)
            .filter(change -> change.expected().id().equals(documents.get("p/Other.java").id())).findFirst()
            .orElseThrow();
        assertSame(documents.get("p/Other.java"), main.expected());
    }

    private void overloadWorkspace() {
        source("p/Calc.java", """
            package p;
            public class Calc {
                static String calc(long value) { return "long"; }
            }
            """);
        source("p/Use.java", """
            package p;
            public class Use {
                public static String call() { return Calc.calc(5); }
            }
            """);
        main("Use.call()");
        assertEquals("long", run());
    }

    // endregion

    // region Helpers

    private void main(String expression) {
        source("p/Main.java", MAIN.formatted(expression));
    }

    private void mainThrowing(String expression) {
        source("p/Main.java", """
            package p;
            public class Main {
                public static String run() {
                    try {
                        return %s;
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
            """.formatted(expression));
    }

    private String run() {
        return run(texts());
    }

    private String run(Map<String, String> sources) {
        return CompiledJava.run(directory, sources, "p.Main");
    }

    private Map<String, String> texts() {
        Map<String, String> texts = new LinkedHashMap<>();
        documents.forEach((name, document) -> texts.put(name, document.text()));
        return texts;
    }

    /** Applies a textual replacement of one occurrence, as a rename without semantic checks would. */
    private Map<String, String> naive(String file, String from, String to) {
        Map<String, String> texts = texts();
        texts.put(file, texts.get(file).replaceFirst(Pattern.quote(from),
            Matcher.quoteReplacement(to)));
        return texts;
    }

    private Map<String, String> naiveAll(String file, String from, String to) {
        Map<String, String> texts = texts();
        texts.put(file, texts.get(file).replace(from, to));
        return texts;
    }

    private static void assertConflict(RenameProposal proposal, String message) {
        assertFalse(proposal.applicable(), () -> "Expected a conflict containing: " + message + ", got "
            + proposal.conflicts());
        assertTrue(proposal.conflicts(RenameConflict.Severity.ERROR).stream()
            .anyMatch(conflict -> conflict.message().contains(message)),
            () -> "Expected '" + message + "' in " + proposal.conflicts());
    }

    private Map<String, String> applied(RenameProposal proposal) {
        assertTrue(proposal.applicable(), () -> String.valueOf(proposal.conflicts()));
        assertTrue(proposal.conflicts().isEmpty(), () -> String.valueOf(proposal.conflicts()));
        return apply(proposal.edit());
    }

    private RenamePreparation.Renameable prepared(String file, String marker) {
        int offset = offset(file, marker);
        return assertInstanceOf(RenamePreparation.Renameable.class,
            services.prepareRename(context(file), offset).value());
    }

    private RenameProposal rename(String file, String marker, String newName) {
        return renameAs(file, marker, prepared(file, marker), newName);
    }

    private RenameProposal renameAs(
        String file,
        String marker,
        RenamePreparation.Renameable prepared,
        String newName
    ) {
        return services.rename(context(file), offset(file, marker), prepared.symbol(), newName).value();
    }

    private int offset(String file, String marker) {
        int offset = documents.get(file).text().indexOf(marker);
        assertTrue(offset >= 0, marker);
        return offset;
    }

    private Map<String, String> apply(WorkspaceEdit edit) {
        Map<String, String> result = texts();
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

    private void source(String file, String text) {
        var uri = DocumentUri.fromPath(directory.resolve(root + file));
        var document = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
        documents.put(file, document);
        store().publishBacking(document);
    }

    /** Publishes a new saved revision, as a change made outside the editor does. */
    private void external(String file, String text) {
        TextDocumentSnapshot saved = documents.get(file);
        if (saved == null) {
            source(file, text);
            return;
        }
        var document = new TextDocumentSnapshot(saved.id(), saved.uri(), saved.version().next(), "java",
            saved.origin(), text, StandardCharsets.UTF_8);
        documents.put(file, document);
        store().publishBacking(document);
    }

    private void overlay(String file, String text) {
        TextDocumentSnapshot saved = documents.get(file);
        var document = new TextDocumentSnapshot(saved.id(), saved.uri(), saved.version().next(), "java",
            saved.origin(), text, StandardCharsets.UTF_8);
        documents.put(file, document);
        store().publishOverlay(document);
    }

    private DefaultDocumentStore store() {
        if (store == null) {
            var configuration = new WorkspaceConfigurationSnapshot(0, Set.of("main"),
                Map.of("module.main.sources", directory.resolve(root).toString()));
            store = new DefaultDocumentStore("rename-safety", configuration);
        }
        return store;
    }

    // endregion
}
