package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.*;

public class JavaSemanticCorrectnessRegressionTest {
    @TempDir
    public Path tempDir;

    @Test
    public void inheritedBinaryMemberTypeUsesNearestNestedSubclass() {
        String source = """
            import javax.tools.SimpleJavaFileObject;
            import java.net.URI;
            class Outer {
                static class SourceFile extends SimpleJavaFileObject {
                    SourceFile() { super(URI.create("string:///Example.java"), Kind.SOURCE); }
                }
            }
            """;
        assertClean(source, new CoreNameResolutionInspection(), new CoreMemberResolutionInspection(),
            new CoreTypeResolutionInspection(), new CoreCallResolutionInspection());
    }

    @Test
    public void inheritedSourceMemberTypePrefersInnerHierarchyAndRetainsOuterFallback() {
        String source = """
            class OuterBase { static class Shared { static String label; } }
            class InnerBase { static class Shared { static int code; } }
            class Outer extends OuterBase {
                class Inner extends InnerBase { int result = Shared.code; }
                class Other { String result = Shared.label; }
            }
            """;
        assertClean(source, new CoreTypeResolutionInspection(), new CoreMemberResolutionInspection(),
            new CoreAssignmentInspection());
    }

    @Test
    public void repeatedPatternNamesResolveToTheirOwnBranchTypes() {
        assertClean("""
            class Example {
                void run(Object input) {
                    if (input instanceof String text) { text.strip(); }
                    if (input instanceof StringBuilder text) { text.append("value"); }
                }
            }
            """, new CoreNameResolutionInspection(), new CoreCallResolutionInspection());
    }

    @Test
    public void patternBindingsFollowNegationShortCircuitAndAbruptGuards() {
        assertClean("""
            class Example {
                void run(Object input) {
                    if (input instanceof String text && !text.isEmpty()) { text.strip(); }
                    if (!(input instanceof String other) || other.isEmpty()) {} else { other.strip(); }
                    if (!(input instanceof String guard)) { return; }
                    guard.strip();
                }
            }
            """, new CoreNameResolutionInspection(), new CoreCallResolutionInspection());
    }

    @Test
    public void patternBindingsAreNotVisibleOnUnmatchedPathsOrAfterNormalBranches() {
        var errors = errors("""
            class Example {
                void run(Object input) {
                    if (input instanceof String text) {} else { consume(text); }
                    consume(text);
                    if (!(input instanceof String negative)) { consume(negative); }
                    if (input instanceof String left || check(left)) {}
                }
                void consume(Object value) {}
                boolean check(Object value) { return true; }
            }
            """, null, new CoreNameResolutionInspection());
        assertEquals(4, errors.size(), () -> errors.toString());
    }

    @Test
    public void whilePatternBindingsStayInsideTheirMatchingLoop() {
        assertClean("""
            class Example {
                void run(Object input) {
                    while (input instanceof String text && !text.isEmpty()) { text.strip(); break; }
                    if (input instanceof StringBuilder text) { text.append("value"); }
                }
            }
            """, new CoreNameResolutionInspection(), new CoreCallResolutionInspection());
    }

    @Test
    public void boundedTypeVariablesAssignToAllUpperBoundsAndTransitiveBounds() {
        assertClean("""
            class Example<T extends Number> {
                void member(T value) { Number number = value; Object object = value; }
                <T extends CharSequence> void method(T value) { CharSequence text = value; }
                <U extends V, V extends Number> void chain(U value) { Number number = value; }
                <X extends Number & Runnable> void intersection(X value) {
                    Number number = value; Runnable action = value;
                }
                <U> void unbounded(U value) { Object object = value; }
            }
            """, new CoreAssignmentInspection());
    }

    @Test
    public void boundedAssignmentsRejectUnrelatedTypesReverseAssignmentsAndShadowedBounds() {
        var errors = errors("""
            class Example<T extends Number> {
                <T extends CharSequence> void run(T value, Number number) {
                    Number wrong = value;
                    T reverse = number;
                }
                <T extends Number> void other(T value) { String wrong = value; }
            }
            """, null, new CoreAssignmentInspection());
        assertEquals(3, errors.size(), () -> errors.toString());
    }

    @Test
    public void staticImportedBinaryVarargsAcceptZeroOneAndMultipleLambdas() throws Exception {
        var junit = Path
            .of(Assertions.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var index = new CompositeJavaSymbolIndex(List.of(JavaLibrarySymbolIndex.build(List.of(junit)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        for (String imported : List.of("assertAll", "*")) {
            String source = "import static org.junit.jupiter.api.Assertions." + imported + "; class Example {"
                + "void run() { assertAll(); assertAll(() -> {}); assertAll(() -> {}, () -> {}); }}";
            assertTrue(errors(source, index, new CoreCallResolutionInspection()).isEmpty());
        }
    }

    @Test
    public void sourceStaticImportsPreserveVarargsWithoutExpandingPlainArrays() throws Exception {
        Path file = tempDir.resolve("Calls.java");
        Files.writeString(file, """
            package sample;
            public class Calls {
                public static void many(String... values) {}
                public static void array(String[] values) {}
            }
            """);
        var index = new CompositeJavaSymbolIndex(List.of(new JavaProjectSemanticIndexer().build(List.of(file)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        var errors = errors("""
            import static sample.Calls.*;
            class Example {
                void run() {
                    many(); many("one", "two"); many(new String[] {"one"});
                    array(new String[] {"one"});
                    many(1); array(); array("one", "two");
                }
            }
            """, index, new CoreCallResolutionInspection());
        assertEquals(3, errors.size(), () -> errors.toString());
    }

    @Test
    public void localArraysAreNotVarargsAndExpandedVarargsStillCheckElementTypes() {
        var errors = errors("""
            class Example {
                void array(String[] values) {}
                void many(String... values) {}
                void run() {
                    array(new String[] {"one"}); many(); many("one", "two");
                    array(); array("one", "two"); many(1);
                }
            }
            """, null, new CoreCallResolutionInspection());
        assertEquals(3, errors.size(), () -> errors.toString());
    }

    @Test
    public void fixedArityOverloadsPrecedeVarargsExpansionButExplicitArraysRemainFixedArity() {
        assertClean("""
            class Example {
                static Integer choose(String... values) { return 1; }
                static String choose(Object value) { return "fixed"; }
                void run() {
                    String fixed = choose("one");
                    Integer array = choose(new String[] {"one"});
                    Integer expanded = choose("one", "two");
                }
            }
            """, new CoreAssignmentInspection(), new CoreCallResolutionInspection());
    }

    @Test
    public void processBuilderNoArgumentCallSelectsTheGetter() {
        String source = """
            import java.util.List;
            class Example {
                void run(ProcessBuilder builder) {
                    List<String> command = builder.command();
                    ProcessBuilder changed = builder.command(new String[] {"java"});
                }
            }
            """;
        assertClean(source, new CoreAssignmentInspection(), new CoreCallResolutionInspection());
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        var context = new JavaRuleContext(Path.of("Example.java"), source, model);
        // Assignment validation above also verifies the selected return type, without relying on declaration order.
        var returnTypes = context.nodesOfKind("JAVA_METHOD_INVOCATION_EXPRESSION").stream()
            .map(node -> context.inferredType(node).orElseThrow().displayName()).toList();
        assertTrue(returnTypes.contains("java.util.List"), () -> returnTypes.toString());
    }

    @Test
    public void basicForAndConditionalExpressionsUseOnlyMatchedPatternBindings() {
        assertClean("""
            class Example {
                void run(Object input) {
                    for (int i = 0; input instanceof String text && text.length() > i; i += text.length()) {
                        text.strip(); break;
                    }
                    for (; input instanceof StringBuilder text; text.append("value")) { text.length(); break; }
                    String selected = input instanceof String text ? text.strip() : "";
                }
            }
            """, new CoreNameResolutionInspection(), new CoreCallResolutionInspection());
    }

    @Test
    public void abruptNestedBranchesRetainGuardBindingsAndUnmatchedLoopsDoNotLeak() {
        assertClean("""
            class Example {
                void run(Object input, boolean flag) {
                    if (!(input instanceof String text)) {
                        if (flag) return; else throw new IllegalArgumentException();
                    }
                    text.strip();
                }
            }
            """, new CoreNameResolutionInspection(), new CoreCallResolutionInspection());
        var errors = errors("""
            class Example {
                void run(Object input) {
                    while (input instanceof String text) { break; }
                    Object invalid = text;
                    boolean matched = input instanceof String assigned;
                    Object alsoInvalid = assigned;
                }
            }
            """, null, new CoreNameResolutionInspection());
        assertEquals(2, errors.size(), () -> errors.toString());
    }

    @Test
    public void projectSourceVarargsConstructorsKeepTheirArityMetadata() throws Exception {
        Path file = tempDir.resolve("Constructed.java");
        Files.writeString(file, """
            package sample;
            public class Constructed {
                public Constructed(int value) {}
                public Constructed(String... values) {}
            }
            """);
        var index = new CompositeJavaSymbolIndex(List.of(new JavaProjectSemanticIndexer().build(List.of(file)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
        String source = "class Example { Object value = new sample.Constructed(\"one\", \"two\"); }";
        var model = JavaSemanticAnalyzer.analyzeFacts(source, index);
        var context = new JavaRuleContext(Path.of("Example.java"), source, model, index);
        var creation = context.nodesOfKind("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION").getFirst();
        assertTrue(context.resolvedSymbol(creation).isPresent());
    }

    @Test
    public void yieldGuardsRetainNestedRecordPatternBindings() {
        assertClean("""
            class Example {
                record Box(Object value) {}
                int run(Object input) {
                    return switch (0) {
                        default -> {
                            if (!(input instanceof Box(String text))) yield 0;
                            yield text.length();
                        }
                    };
                }
            }
            """, new CoreNameResolutionInspection(), new CoreCallResolutionInspection());
    }

    @Test
    public void switchRulesDoNotSharePatternBindings() {
        assertClean("""
            class Example {
                String run(Object input) {
                    return switch (input) {
                        case String text -> text.strip();
                        case StringBuilder text -> text.append("value").toString();
                        default -> "";
                    };
                }
            }
            """, new CoreNameResolutionInspection(), new CoreCallResolutionInspection());
    }

    private static void assertClean(String source, JavaInspectionRuleProvider... providers) {
        var errors = errors(source, null, providers);
        assertTrue(errors.isEmpty(), () -> errors.toString());
    }

    private static List<SemanticDiagnostic> errors(
        String source,
        JavaSymbolIndex index,
        JavaInspectionRuleProvider... providers
    ) {
        return JavaInspectionTestSupport
            .runProviders(List.of(providers), Path.of("Example.java"), source,
                index == null ? JavaJdkSymbolIndex.fromCurrentRuntime() : index)
            .stream().filter(diagnostic -> diagnostic.severity() == SemanticDiagnostic.Severity.ERROR).toList();
    }
}
