package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.java.api.JavaTypes;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaInferenceRepairTest {
    private static final JavaSymbolIndex JDK = JavaJdkSymbolIndex.fromCurrentRuntime();
    @TempDir
    public Path directory;

    @Test
    public void diamondConstructorsInferValuesAndFunctionalResults() throws Exception {
        check("""
            import java.util.*;
            import java.util.concurrent.*;
            class Example {
                void run(List<String> source) throws Exception {
                    var copied = new ArrayList<>(source);
                    copied.sort(Comparator.comparingInt(value -> value.length()));
                    var task = new FutureTask<>(() -> { var value = "text"; return value; });
                    task.run();
                    task.get().strip();
                }
            }
            """, true);
    }

    @Test
    public void arrayArgumentsPreferArrayOverUnconstrainedGenericOverloads() throws Exception {
        check("""
            import java.util.*;
            import java.nio.file.*;
            class Example {
                void run(String[] values) {
                    var items = List.of(values);
                    items.stream().map(Path::of).sorted().toList();
                }
            }
            """, true);
    }

    @Test
    public void nestedLambdasUseReturnTargetsAndMapValueTargets() throws Exception {
        check("""
            import java.util.*;
            import java.util.function.*;
            class Example {
                static <T> Supplier<T> supply(Function<T, String> formatter) { return null; }
                static void install(Function<Integer, Supplier<String>> factory) {}
                static void installMap(Map<String, Function<String, String>> functions) {}
                void run() {
                    install(index -> supply(value -> value.strip()));
                    installMap(Map.of("first", value -> value.strip(), "second", value -> value.trim()));
                }
            }
            """, true);
    }

    @Test
    public void genericArgumentsFollowInheritedCollectionAndCompletionStageTypes() throws Exception {
        check("""
            import java.util.*;
            import java.util.concurrent.*;
            class Example {
                void run(List<String> values, CompletableFuture<List<String>> future) {
                    List.copyOf(values).stream().filter(value -> value.isEmpty()).toList();
                    future.thenCompose(result -> CompletableFuture.completedFuture(result))
                        .whenComplete((result, error) -> result.stream().filter(String::isEmpty).toList());
                }
            }
            """, true);
    }

    @Test
    public void blockLambdaResultsExcludeReturnsFromNestedScopes() throws Exception {
        check("""
            import java.util.concurrent.*;
            class Example {
                static <T> T onThread(Callable<T> action) throws Exception { return action.call(); }
                void run(boolean condition) throws Exception {
                    var text = onThread(() -> {
                        class Local { int value() { return 1; } }
                        Callable<Integer> nested = () -> { return 2; };
                        Object anonymous = new Object() { int value() { return 3; } };
                        if (condition) { return "first"; }
                        var fallback = "second";
                        return fallback;
                    });
                    text.strip().isEmpty();
                }
            }
            """, true);
    }

    @Test
    public void inferredGenericResultsStillRejectMissingMembers() throws Exception {
        check("""
            import java.util.*;
            class Example {
                void run(List<String> values) {
                    List.copyOf(values).forEach(value -> value.missing());
                }
            }
            """, false);
    }

    @Test
    public void conditionalStringMapper() throws Exception {
        check("import java.util.*; class Example { void f(List<String> xs) { "
            + "xs.stream().map(x -> x == null ? \"\" : x.strip()).filter(x -> !x.isBlank()).toList(); }}", true);
    }

    @Test
    public void literalTypeIgnoresShadowing() throws Exception {
        String source = "class String {} class Example { java.lang.String s = \"x\"; }";
        check(source, true);
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        var context = new JavaRuleContext(Path.of("Example.java"), source, model);
        var literal = context.nodesOfKind("JAVA_LITERAL_EXPRESSION").getFirst();
        assertEquals(new JavaTypes.DeclaredType("java.lang.String", List.of()),
            model.inferredType(literal).orElseThrow());
    }

    @Test
    public void literalCannotBeAssignedToShadowingClass() throws Exception {
        check("class String {} class Example { String s = \"x\"; }", false);
    }

    @Test
    public void textBlockCannotBeAssignedToShadowingClass() throws Exception {
        check("class String {} class Example { String s = \"\"\"\ntext\n\"\"\"; }", false);
    }

    private void check(String source, boolean valid) throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, source);
        var output = new ByteArrayOutputStream();
        int exit = ToolProvider.getSystemJavaCompiler().run(null, output, output,
            "--release", "25", "-proc:none", "-d", directory.toString(), file.toString());
        assertEquals(valid, exit == 0, () -> "javac: " + output);
        var diagnostics = JavaInspectionTestSupport.runProviders(
            List.of(new CoreAssignmentInspection(), new CoreCallResolutionInspection()),
            file, source, JDK);
        boolean errors = diagnostics.stream().anyMatch(d -> d.severity() == SemanticDiagnostic.Severity.ERROR);
        assertEquals(valid, !errors, () -> "SST: " + diagnostics);
    }
}
