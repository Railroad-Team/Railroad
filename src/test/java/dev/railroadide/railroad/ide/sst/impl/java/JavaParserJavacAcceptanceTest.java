package dev.railroadide.railroad.ide.sst.impl.java;

import com.sun.source.util.JavacTask;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Sources that javac's parser accepts must parse without recovery. javac rejects some of them later, during
 * attribution, so their errors belong to semantic analysis rather than to the parser. Each case comes from a javac
 * test that SST rejected.
 */
public class JavaParserJavacAcceptanceTest {
    private static final Map<String, String> CASES = Map.ofEntries(
        Map.entry("empty for initializer", """
            class A { void m(int n) { for ( ; n > 0; n--) {} } }
            """),
        Map.entry("pattern in for condition", """
            class A { void m(Object o) { for ( ; !(o instanceof String s); ) { o = ""; } } }
            """),
        Map.entry("var as a variable name", """
            class A { int var; void m(A var) { var = null; var.var *= 2; this.var = 12; } }
            """),
        Map.entry("record as a method and variable name", """
            class A { int record; void record(int x) {} void m() { record(1); record |= 0b01; } }
            """),
        Map.entry("explicit type arguments on constructor invocations", """
            class A {
                <T> A() {}
                A(int x) { <Object>this(); }
                class Inner { <T> Inner() {} }
                static class Sub extends Inner { Sub(A outer) { outer.<Object>super(); } }
            }
            class B extends A { <T, E> B() { <T, E>super(); } }
            """),
        Map.entry("method references on parameterized and array types", """
            class A<T> {
                void m() {
                    Object a = A<String>::m;
                    Object b = A<?>::new;
                    Object c = int[]::clone;
                    Object d = String[]::clone;
                    Object e = A<String>::<Object>new;
                    Object f = java.util.ArrayList<String>::new;
                }
            }
            """),
        Map.entry("annotated method reference qualifiers", """
            class A {
                class Inner<T> {}
                Object m() { return @AA A.Inner<@AB String>::<@AC Integer>new; }
            }
            """),
        Map.entry("parenthesized increment operands", """
            class A { int x; void m(int i, A a) { i = (i)++; i = (i)--; i = ++(i); i = (a.x)++; } }
            """),
        Map.entry("guarded case pattern", """
            class A { int m(Object o, boolean flag) {
                return switch (o) { case String s when flag -> 1; default -> 0; };
            } }
            """),
        Map.entry("compact source file", """
            enum Foo { A, B }
            class Nested {}
            int field = 1;
            void main() {}
            """),
        Map.entry("modifiers javac rejects after parsing", """
            public synchronized class A {}
            """),
        Map.entry("annotation types javac rejects after parsing", """
            @interface Generic<T> {}
            @interface Extending extends Object {}
            class A { void m() { @interface Local {} } }
            """),
        Map.entry("var with type arguments", """
            class A { void m() { var<String> value = null; } }
            """),
        Map.entry("annotated qualifier in a method call", """
            class A { void m(String[] args) { java.util.@A Arrays.stream(args); } }
            """),
        // Each \\u is a Unicode escape of the case, which the test source itself keeps as text.
        Map.entry("unicode escapes translate before lexing", """
            class A {
                String quotes = \\u0022\\u0022;
                String backslash = "\\u005C\\]";
                int \\ud801\\udc00abc = 1;
                int 𐐁raw = 2;
                int a; \\u000D
                void m() { \\u002f\\u002f a comment
                }
            }
            """),
        Map.entry("lambdas as binary operands", """
            class A { void m() { test((int x) -> {} + (int x) -> {}); boolean b = (int x) -> {} instanceof Object; } }
            """));

    private static final Map<String, String> REJECTED_CASES = Map.ofEntries(
        Map.entry("name without a declaration at the top level", """
            class A {}
            stray
            """),
        Map.entry("restricted identifier as a class name", """
            class var {}
            """),
        Map.entry("restricted identifier as a type parameter", """
            class A<yield> {}
            """),
        Map.entry("var with array dimensions", """
            class A { void m() { var[] a = null; var b[] = null; } }
            """),
        Map.entry("var with several declarators", """
            class A { void m() { var a = 1, b = 2; } }
            """),
        Map.entry("annotated record pattern", """
            class A {
                record R(String s) {}
                boolean m(Object o) { return o instanceof @Deprecated R(var s); }
            }
            """));

    @TestFactory
    public List<DynamicTest> javacRejectedSourcesHaveSyntaxErrors() {
        return REJECTED_CASES.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
                assertFalse(javacParseErrors(entry.getValue()).isEmpty(), "javac must reject the case");
                assertFalse(JavaSyntaxParser.parseWithDiagnostics(entry.getValue()).diagnostics().isEmpty());
            }))
            .toList();
    }

    @TestFactory
    public List<DynamicTest> javacAcceptedSourcesParseWithoutRecovery() {
        return CASES.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
                assertEquals(List.of(), javacParseErrors(entry.getValue()), "javac must accept the case");
                JavaSyntaxAssertions.assertParsesWithoutRecovery(entry.getValue());
            }))
            .toList();
    }

    private static List<String> javacParseErrors(String source) throws IOException {
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var file = new SimpleJavaFileObject(URI.create("string:///Case.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        var task = (JavacTask) ToolProvider.getSystemJavaCompiler()
            .getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(file));
        task.parse();
        return diagnostics.getDiagnostics().stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(diagnostic -> diagnostic.getCode() + " at " + diagnostic.getLineNumber() + ":"
                + diagnostic.getColumnNumber())
            .toList();
    }
}
