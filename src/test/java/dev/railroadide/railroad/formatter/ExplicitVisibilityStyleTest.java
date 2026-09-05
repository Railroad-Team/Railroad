package dev.railroadide.railroad.formatter;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExplicitVisibilityStyleTest {
    @Test
    public void reportsPackagePrivateTypesFieldsMethodsAndConstructors() throws Exception {
        var violations = inspect("""
            class Example {
                int first, second;
                Example() {}
                void run() {}
                static class Nested {}
                interface Contract {}
                enum Choice { ONE }
                record Value(int number) {}
                @interface Marker {}
            }
            """);
        assertEquals(List.of(1, 2, 2, 3, 4, 5, 6, 7, 8, 9),
            violations.stream().map(ExplicitVisibilityStyle.Violation::line).toList());
        assertTrue(violations.get(0).message().contains("type 'Example'"));
        assertTrue(violations.get(1).message().contains("field 'first'"));
        assertTrue(violations.get(3).message().contains("constructor 'Example'"));
        assertTrue(violations.get(4).message().contains("method 'run'"));
    }

    @Test
    public void permitsExplicitAccessAndJavaImplicitVisibility() throws Exception {
        assertEquals(List.of(), inspect("""
            public class Example {
                private int value;
                protected Example() {}
                public interface Contract {
                    int CONSTANT = 1;
                    void run();
                    default int value() { return CONSTANT; }
                    static int shared() { return CONSTANT; }
                    private void helper() {}
                    class Nested {}
                    interface NestedContract {}
                    record Value(int number) {}
                }
                public @interface Marker { String value(); }
                public enum Choice {
                    ONE;
                    Choice() {}
                }
                public record Value(int number) {}
                public record Checked(int number) {
                    public Checked {}
                }
                private void run(int parameter) {
                    int local = parameter;
                    class Local {}
                    Object anonymous = new Object() {
                        private int field;
                        public String toString() { return "example"; }
                    };
                }
            }
            """));
    }

    @Test
    public void reportsCompactRecordConstructorsAndMembersOfLocalClasses() throws Exception {
        var violations = inspect("""
            public record Example(int value) {
                Example {}
                public void run() {
                    class Local {
                        int field;
                        void work() {}
                    }
                }
            }
            """);
        assertEquals(List.of(2, 5, 6), violations.stream().map(ExplicitVisibilityStyle.Violation::line).toList());
    }

    @Test
    public void doesNotReportGeneratedDefaultConstructorsOrTextInCommentsAndStrings() throws Exception {
        assertEquals(List.of(), inspect("""
            public class Example {
                // class Hidden { int field; }
                private String text = "class Hidden { void run() {} }";
                private static class Nested {}
            }
            """));
    }

    @Test
    public void applyFormatsAllFilesDespiteVisibilityViolationsWhileCheckStillFails(@TempDir Path directory)
        throws Exception {
        Path input = directory.resolve("Example.java");
        String source = "class Example { int value; void run(boolean ready) { if (ready) { return; } } }\n";
        Files.writeString(input, source);
        Path secondInput = directory.resolve("Other.java");
        String secondSource = source.replace("Example", "Other");
        Files.writeString(secondInput, secondSource);
        String formatted = RailroadJavaStyle.rewrite(source);
        assertTrue(!source.equals(formatted), "fixture must need structural formatting");
        String formatterClasspath = Path
            .of(RailroadJavaStyle.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString();
        for (String mode : List.of("--check", "--apply")) {
            Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", formatterClasspath,
                RailroadJavaStyle.class.getName(), mode, "--all", directory.toString()).redirectErrorStream(true)
                .start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean checkOnly = mode.equals("--check");
            assertEquals(checkOnly ? 1 : 0, process.waitFor(), output);
            assertTrue(output.contains("Example.java:1: Package-private type 'Example'"), output);
            assertTrue(output.contains("Package-private field 'value'"), output);
            assertTrue(output.contains("Other.java:1: Package-private type 'Other'"), output);
            assertEquals(checkOnly ? source : formatted, Files.readString(input));
            assertEquals(checkOnly ? secondSource : formatted.replace("Example", "Other"),
                Files.readString(secondInput));
        }
    }

    private static List<ExplicitVisibilityStyle.Violation> inspect(String source) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var input = new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        try (var fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, fileManager, diagnostics,
                List.of("-proc:none", "--release", "25"), null, List.of(input));
            var unit = task.parse().iterator().next();
            assertTrue(
                diagnostics.getDiagnostics().stream()
                    .noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR),
                diagnostics.getDiagnostics().toString());
            return ExplicitVisibilityStyle.inspect(unit, Trees.instance(task).getSourcePositions());
        }
    }
}
