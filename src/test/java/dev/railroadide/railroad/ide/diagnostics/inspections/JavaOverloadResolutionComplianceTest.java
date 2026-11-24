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
 * Overload resolution must agree with javac (JLS 15.12.2). Each case is compiled by javac first, with the raw
 * diagnostics the OpenJDK tests use so javac does not rewrite inapplicable calls, and SST must report
 * one matching error for each javac error.
 */
public class JavaOverloadResolutionComplianceTest {
    private static final Map<String, String> JAVAC_TO_SST = Map.of(
        "compiler.err.cant.apply.symbol", "SEM_UNRESOLVED_CALL",
        "compiler.err.cant.apply.symbols", "SEM_UNRESOLVED_CALL",
        "compiler.err.ref.ambiguous", "SEM_AMBIGUOUS_CALL",
        "compiler.err.cant.resolve.location", "SEM_UNRESOLVED_NAME");

    private static final Map<String, String> CASES = Map.ofEntries(
        Map.entry("inner method hides an applicable outer method", """
            class Outer {
                void m1(String s) {}
                class Inner { void m1(Integer i) {} void test() { m1(""); } }
            }
            """),
        Map.entry("several inapplicable overloads", """
            class Outer {
                void m1(String s) {}
                class Inner {
                    private void m1(Integer i) {}
                    private void m1(Double d) {}
                    void test() { m1(""); }
                }
            }
            """),
        Map.entry("fixed arity ambiguity", """
            class A {
                void m(Object o, int i) {}
                void m(int i, Object o) {}
                void test() { m(1, 1); }
            }
            """),
        Map.entry("variable arity ambiguity", """
            class A {
                void m(Long l) {}
                void m(Exception... exceptions) {}
                void m(Long l, Exception... exceptions) {}
                void test() { m(null, new Exception()); }
            }
            """),
        Map.entry("boxed and primitive variable arity are ambiguous", """
            class A {
                void m(int... values) {}
                void m(Integer... values) {}
                void test() { m(1); }
            }
            """),
        Map.entry("null is ambiguous between unrelated types", """
            class A {
                void m(String s) {}
                void m(Integer i) {}
                void test() { m(null); }
            }
            """),
        Map.entry("null to a primitive parameter", """
            class A { void m(int i) {} void test() { m(null); } }
            """),
        Map.entry("binary method overloads", """
            class A {
                void test() {
                    "text".substring("x");
                    System.out.println(null);
                }
            }
            """),
        Map.entry("outer method used when the inner type has no member", """
            class Outer {
                void m1(String s) {}
                class Inner { void test() { m1(""); } }
            }
            """),
        Map.entry("private methods are not inherited", """
            class Outer {
                void m1(String s) {}
                void m2(int a, int b) {}
                class Base {
                    private void m1(Integer i) {}
                    private void m2(Object o, int i) {}
                    private void m2(int i, Object o) {}
                }
                class Derived extends Base { void test() { m1(""); m2(1, 1); } }
            }
            """),
        Map.entry("strict invocation wins before boxing", """
            class A {
                void m(long l) {}
                void m(Integer i) {}
                void test() { m(1); }
            }
            """),
        Map.entry("most specific reference and variable arity methods", """
            class A {
                void m(Object o) {}
                void m(String s) {}
                void v(int... values) {}
                void v(long... values) {}
                void test() { m(null); v(1); v(); String.valueOf((char[]) null); Math.max(1, 2L); }
            }
            """),
        Map.entry("overridden and inherited methods", """
            class Base { void m(String s) {} void m(Object o) {} }
            class Derived extends Base {
                @Override void m(String s) {}
                void test(Runnable runnable) { m("x"); runnable.equals(runnable); }
            }
            """),
        Map.entry("explicit constructor invocations are not method calls", """
            class A {
                int value;
                A(int value) { this.value = value; }
                A() { this(seed()); }
                static int seed() { return 1; }
            }
            class B extends A { B() { super(seed()); } }
            """),
        Map.entry("C-style array parameters",
            """
                class A { static void run(String args[], int counts[][]) {} void test(String[] a) { run(a, new int[0][]); } }
                """),
        Map.entry("arguments with unresolved names report only the names", """
            class A { void m(int i, String s) {} void test() { m(i < len, "x"); } }
            """),
        Map.entry("implicitly typed lambda parameters are not judged", """
            import java.util.function.Function;
            class A {
                static String m(String s, Object o) { return s; }
                void test() { Function<Integer, String> f = value -> m("x", value); }
            }
            """),
        Map.entry("generic parameters shadowing library types are left to inference", """
            interface List<E> {}
            class Records {
                static <T> int search(List<T> list, T key) { return 0; }
                static <T> int search(List<T> list, T key, Object comparator) { return 0; }
                void test() { List records = null; search(records, "x"); }
            }
            """),
        Map.entry("method type parameters shadow classes of the same name", """
            class T {}
            class Search {
                static <T> int find(T key) { return 0; }
                static <T> int find(T key, Object other) { return 0; }
                void test() { find("x"); }
            }
            """),
        Map.entry("generic overloads are left to inference", """
            class A {
                <T> void m(T value) {}
                void m(String value) {}
                void test() { m("x"); }
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
        var task = (JavacTask) ToolProvider.getSystemJavaCompiler()
            .getTask(null, null, diagnostics, List.of("-proc:none", "-XDrawDiagnostics"), null, List.of(file));
        task.analyze();
        return diagnostics.getDiagnostics().stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(Diagnostic::getCode)
            .toList();
    }
}
