package dev.railroadide.railroad.ide.diagnostics.inspections;

import com.sun.source.util.JavacTask;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Class hierarchies must be checked as javac checks them. Each case is compiled by javac first, and SST must report
 * one matching error for each javac error.
 */
public class JavaInheritanceComplianceTest {
    private static final Map<String, String> JAVAC_TO_SST = Map.of(
        "compiler.err.cyclic.inheritance", "SEM_INVALID_INHERITANCE",
        "compiler.err.does.not.override.abstract", "SEM_MISSING_IMPLEMENTATION");

    private static final Map<String, String> CASES = Map.ofEntries(
        Map.entry("class extending itself", """
            class C1 extends C1 {}
            interface I1 extends I1 {}
            """),
        Map.entry("cycle reported once", """
            class C11 extends C12 {}
            class C12 extends C11 {}
            interface I11 extends I12 {}
            interface I12 extends I11 {}
            """),
        Map.entry("class extending its own member", """
            class C211 implements C211.I { interface I {} }
            class C212 extends C212.C { static class C {} }
            """),
        Map.entry("cycle through supertype qualifiers", """
            class T {
                static class A { static interface B {} }
                static class C extends A implements Z.Y {}
                static class X { static interface Y {} }
                static class Z extends X implements C.B {}
            }
            """),
        Map.entry("interface cycle through several supertypes", """
            interface Cycle extends I1, I2 {}
            interface I1 extends Cycle {}
            interface I2 extends Cycle {}
            """),
        Map.entry("acyclic nesting and inheritance", """
            class Outer {
                class A { class B extends A {} }
                static class Base {}
                static class Impl extends Base {}
                static class Self extends Outer {}
            }
            interface Shape { class Circle implements Shape {} }
            class Node implements Comparable<Node> { public int compareTo(Node other) { return 0; } }
            """),
        Map.entry("anonymous class missing an abstract method", """
            class A {
                interface Task { void run(); void stop(); }
                abstract static class Base { abstract int value(); }
                Object task = new Task() { public void run() {} };
                Object base = new Base() {};
            }
            """),
        Map.entry("anonymous classes implementing every abstract method", """
            class A {
                interface Task { void run(); default void stop() {} }
                abstract static class Base { abstract int value(); public String toString() { return ""; } }
                Object task = new Task() { public void run() {} };
                Object base = new Base() { int value() { return 1; } };
                Runnable runnable = new Runnable() { public void run() {} };
                Comparable<String> comparable = new Comparable<String>() {
                    public int compareTo(String other) { return 0; }
                };
                java.util.function.Consumer<? super Void> consumer = new java.util.function.Consumer<>() {
                    public void accept(Void value) {}
                };
            }
            """));

    @TestFactory
    public List<DynamicTest> sstReportsTheErrorsJavacReports() {
        return CASES.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
                List<String> expected = javacErrors(entry.getValue()).stream()
                    .map(code -> JAVAC_TO_SST.getOrDefault(code, code))
                    .sorted()
                    .toList();
                assertEquals(expected, sstErrors(entry.getValue()));
            }))
            .toList();
    }

    private static List<String> sstErrors(String source) {
        return JavaInspectionTestSupport.runProviders(JavaInspectionRegistries.coreRuleProviders(),
            Path.of("Case.java"), source, JavaJdkSymbolIndex.fromCurrentRuntime()).stream()
            .filter(diagnostic -> diagnostic.severity() == SemanticDiagnostic.Severity.ERROR)
            .map(SemanticDiagnostic::code)
            .sorted()
            .toList();
    }

    private static List<String> javacErrors(String source) throws IOException {
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var file = new SimpleJavaFileObject(URI.create("string:///Case.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };

        JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler()
            .getTask(null, null, diagnostics, List.of("-proc:none", "-XDrawDiagnostics"), null, List.of(file));
        task.analyze();
        return diagnostics.getDiagnostics().stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(Diagnostic::getCode)
            .toList();
    }
}
