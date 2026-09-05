package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RailroadJavaStyleTest {
    @Test
    public void removesBracesFromTerminalControlFlowBodies() {
        String before = """
            class Example {
                void run(boolean condition) {
                    if (condition) {
                        return;
                    } else {
                        throw new IllegalStateException();
                    }
                    while (condition) {
                        break;
                    }
                    do {
                        continue;
                    } while (condition);
                }
            }
            """;
        String after = """
            class Example {
                void run(boolean condition) {
                    if (condition)
                        return;
                    else
                        throw new IllegalStateException();
                    while (condition)
                        break;
                    do
                        continue;
                    while (condition);
                }
            }
            """;

        assertRewrite(before, after);
    }

    @Test
    public void preservesBracesForNonTerminalAndMultiStatementBodies() {
        String source = """
            class Example {
                void run(boolean condition) {
                    if (condition) {
                        work();
                    }
                    if (!condition) {
                        work();
                        return;
                    }
                }

                void work() {
                }
            }
            """;

        assertRewrite(source, source);
    }

    @Test
    public void addsBracesToNonTerminalControlFlowBodies() {
        assertRewrite("""
            class Example {
                void run(boolean condition) {
                    if (condition)
                        work();
                    else if (ready())
                        work();
                    else
                        work();
                    while (condition)
                        work();
                }

                boolean ready() {
                    return true;
                }

                void work() {
                }
            }
            """, """
            class Example {
                void run(boolean condition) {
                    if (condition)
                        {
            work();
            }
                    else if (ready())
                        {
            work();
            }
                    else
                        {
            work();
            }
                    while (condition)
                        {
            work();
            }
                }

                boolean ready() {
                    return true;
                }

                void work() {
                }
            }
            """);
    }

    @Test
    public void preservesCommentsWhileRemovingOnlyTheOptionalBraces() {
        assertRewrite("""
            class Example {
                void run(boolean condition) {
                    if (condition) { // explain why
                        // keep this comment
                        return;
                    }
                }
            }
            """, """
            class Example {
                void run(boolean condition) {
                    if (condition) // explain why
                        // keep this comment
                        return;
                }
            }
            """);
    }

    @Test
    public void usesVarForExactConstructedLocalTypes() {
        assertRewrite("""
            class Example {
                void run() {
                    Example value = new Example();
                    final Example finalValue = new Example();
                    Box<String> box = new Box<String>();
                    for (Example current = new Example(); ready(); current = new Example()) {
                        use(current);
                    }
                }

                boolean ready() {
                    return true;
                }

                void use(Example value) {
                }

                static class Box<T> {
                }
            }
            """, """
            class Example {
                void run() {
                    var value = new Example();
                    final var finalValue = new Example();
                    var box = new Box<String>();
                    for (var current = new Example(); ready(); current = new Example()) {
                        use(current);
                    }
                }

                boolean ready() {
                    return true;
                }

                void use(Example value) {
                }

                static class Box<T> {
                }
            }
            """);
    }

    @Test
    public void preservesExplicitTypesWhenVarCouldChangeMeaningOrIsNotLocal() {
        String source = """
            class Example {
                Example field = new Example();

                void run(Example parameter) {
                    Parent interfaceType = new Child();
                    Box<String> diamond = new Box<>();
                    Example anonymous = new Example() { };
                    @Marker Example annotated = new Example();
                    Example fromMethod = create();
                    Example first = new Example(), second = new Example();
                }

                Example create() {
                    return new Example();
                }

                interface Parent {
                }

                static class Child implements Parent {
                }

                static class Box<T> {
                }

                @interface Marker {
                }
            }
            """;

        assertRewrite(source, source);
    }

    @Test
    public void replacesConventionalUnusedNamesWithUnnamedVariables() {
        assertRewrite("""
            class Example {
                void run() {
                    Object ignored = value();
                    for (Object $ = value(); ready(); ) {
                        work();
                    }
                    for (Object $1 : values()) {
                        work();
                    }
                    try (Resource $2 = open()) {
                        work();
                    } catch (Exception ignored) {
                        work();
                    }
                    Consumer consumer = ignored -> work();
                    BiConsumer pair = ($, $1) -> work();
                }
            }
            """, """
            class Example {
                void run() {
                    Object _ = value();
                    for (Object _ = value(); ready(); ) {
                        work();
                    }
                    for (Object _ : values()) {
                        work();
                    }
                    try (Resource _ = open()) {
                        work();
                    } catch (Exception _) {
                        work();
                    }
                    Consumer consumer = _ -> work();
                    BiConsumer pair = (_, _) -> work();
                }
            }
            """);
    }

    @Test
    public void preservesConventionalUnusedNamesWhenReferencedOrUnderscoreIsIllegal() {
        String source = """
            class Example {
                Object ignored = value();

                void run(Object ignored) {
                    Object $ = value();
                    use($);
                    for (Object $1 : values()) {
                        use($1);
                    }
                    try {
                        work();
                    } catch (Exception ignored) {
                        use(ignored);
                    }
                    Consumer consumer = ignored -> use(ignored);
                    Object ignored1 = value();
                    Object $name = value();
                    Object ignored = value(), $2 = value();
                }
            }
            """;

        assertRewrite(source, source);
    }

    @Test
    public void importsQualifiedTypesInDeclarationsConstructionsAndExpressions() {
        assertRewrite("""
            package example;

            @java.lang.Deprecated
            class Example {
                java.util.List<String> values = java.util.List.of();
                java.util.Map.Entry<String, String> entry;
                Object factory = (java.util.function.Supplier<?>) java.util.ArrayList::new;
                Class<?> type = java.util.HashMap.class;
                void run() {
                    java.util.ArrayList<String> items = new java.util.ArrayList<String>();
                }
            }
            """, """
            package example;

            import java.util.ArrayList;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.function.Supplier;

            @Deprecated
            class Example {
                List<String> values = List.of();
                Map.Entry<String, String> entry;
                Object factory = (Supplier<?>) ArrayList::new;
                Class<?> type = HashMap.class;
                void run() {
                    var items = new ArrayList<String>();
                }
            }
            """);
    }

    @Test
    public void reusesExistingAndImplicitImports() {
        assertRewrite("""
            package java.util;

            import java.time.Instant;

            class Example {
                Instant now;
                java.time.Instant later;
                java.lang.String text;
                java.util.List<?> values;
            }
            """, """
            package java.util;

            import java.time.Instant;

            class Example {
                Instant now;
                Instant later;
                String text;
                List<?> values;
            }
            """);
    }

    @Test
    public void preservesConflictingImportsAndAmbiguousQualifiedTypes() {
        String source = """
            import java.awt.List;

            class Example {
                java.util.List<?> values;
                java.util.Date utilDate;
                java.sql.Date sqlDate;
            }
            """;
        assertRewrite(source, source);
    }

    @Test
    public void preservesTypesShadowedByDeclarationsAndInheritedMembers() {
        String source = """
            class Parent {
                static class List {}
            }
            class Example<String> extends Parent {
                java.util.List<?> values;
                java.lang.String text;
                Object ArrayList;
                java.util.ArrayList<?> items;
                class Map {}
                java.util.Map<?, ?> map;
            }
            """;
        assertRewrite(source, source);
    }

    @Test
    public void preservesExistingSimpleNameBindingsFromWildcardImports() {
        String source = """
            import java.awt.*;

            class Example {
                List existing;
                java.util.List<?> values;
            }
            """;
        assertRewrite(source, source);
    }

    @Test
    public void ignoresUnresolvedNamesCommentsStringsAndFieldAccesses() {
        String source = """
            class Example {
                missing.library.Widget unresolved;
                String text = "java.util.List";
                // java.util.List stays in this comment.
                java./* explanation */util.List<?> commented;
                Example other;
                Object value = other.other;
            }
            """;
        assertRewrite(source, source);
    }

    @Test
    public void addsImportsWithoutAPackageAndPreservesCrLfAndTrailingComments() {
        assertRewrite("class Example { java.util.List<?> values; }\n",
            "import java.util.List;\n\nclass Example { List<?> values; }\n");
        assertRewrite("package example; // package comment\r\n\r\nclass Example { java.util.List<?> values; }\r\n",
            "package example; // package comment\r\n\r\nimport java.util.List;\r\n\r\nclass Example { List<?> values; }\r\n");
        assertRewrite("import java.util.Set; // import comment\n\nclass Example { java.util.List<?> values; }\n",
            "import java.util.Set; // import comment\nimport java.util.List;\n\nclass Example { List<?> values; }\n");
        assertRewrite("import static java.util.Collections.emptyList;\n\nclass Example { java.util.List<?> values; }\n",
            "import java.util.List;\n\nimport static java.util.Collections.emptyList;\n\nclass Example { List<?> values; }\n");
    }

    @Test
    public void usesVarAfterShorteningMixedQualifiedAndSimpleConstructionTypes() {
        assertRewrite("""
            import java.util.ArrayList;

            class Example {
                void run() {
                    java.util.ArrayList<String> first = new ArrayList<String>();
                    ArrayList<String> second = new java.util.ArrayList<String>();
                }
            }
            """, """
            import java.util.ArrayList;

            class Example {
                void run() {
                    var first = new ArrayList<String>();
                    var second = new ArrayList<String>();
                }
            }
            """);
    }

    @Test
    public void checkReportsWithoutWritingAndApplyResolvesProjectSourceTypes(@TempDir Path directory) throws Exception {
        Path library = Files.createDirectories(directory.resolve("library"));
        Files.writeString(library.resolve("Widget.java"), "package library; public class Widget {}\n");
        Path input = directory.resolve("Example.java");
        String before = "public class Example { private library.Widget widget; }\n";
        Files.writeString(input, before);
        String formatterClasspath = Path
            .of(RailroadJavaStyle.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString();
        Process check = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", formatterClasspath,
            RailroadJavaStyle.class.getName(), "--check", "--all", "--source-path", directory.toString(),
            input.toString())
            .redirectErrorStream(true).start();
        var output = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(1, check.waitFor(), output);
        assertTrue(output.contains("Example.java:1:"), output);
        assertEquals(before, Files.readString(input));

        RailroadJavaStyle
            .main(new String[]{"--apply", "--all", "--source-path", directory.toString(), input.toString()});
        assertEquals("import library.Widget;\n\npublic class Example { private Widget widget; }\n",
            Files.readString(input));
    }

    private static void assertRewrite(String before, String after) {
        assertEquals(after, RailroadJavaStyle.rewrite(before));
        assertEquals(after, RailroadJavaStyle.rewrite(after), "rewrite must be idempotent");
    }
}
