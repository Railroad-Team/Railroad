package dev.railroadide.railroad.ide.syntaxhighlighting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TreeSitterJavaSyntaxHighlightingTest {
    @Test
    public void highlightsEveryCommentFormWithoutColouringSurroundingCode() {
        String source = "/** Documentation. */\nclass Example {\n"
            + "    // Line comment\n"
            + "    int count; /* Block\n       comment */ int limit;\n"
            + "    String text = \"/* not a comment */\";\n}";
        var styles = TreeSitterJavaSyntaxHighlighting.computeHighlighting(source);
        var comments = new ArrayList<Boolean>();
        for (var span : styles) {
            comments.addAll(Collections.nCopies(span.getLength(), span.getStyle().contains("comment")));
        }

        assertEquals(source.length(), comments.size());
        var expected = new ArrayList<>(Collections.nCopies(source.length(), false));
        for (String comment : new String[]{"/** Documentation. */", "// Line comment", "/* Block\n       comment */"}) {
            int start = source.indexOf(comment);
            for (int offset = start; offset < start + comment.length(); offset++) {
                expected.set(offset, true);
            }
        }

        assertEquals(expected, comments);
    }

    @Test
    public void highlightsKeywordsAndPrimitiveTypesInValidCode() {
        String source = """
            package example;
            import java.io.*;
            public abstract sealed class Base permits Child {
                protected transient volatile int count;
                private static final boolean FLAG = true;
                byte b; short s; long l; char c; float f; double d;
                abstract void run() throws Exception;
                native void nativeCall();
                synchronized strictfp void work() {
                    assert true;
                    for (int i = 0; i < 1; i++) { if (false) continue; else break; }
                    do { count--; } while (count > 0);
                    try { throw new Exception(); } catch (Exception e) { return; } finally { this.count = 0; }
                    var result = switch (count) { case 0 -> 1; default -> { yield 2; } };
                }
            }
            final class Child extends Base { void run() { super.toString(); } }
            interface Face {} enum Mode { ON } record Data(int value) implements Face {}
            non-sealed class Other extends Base { void run() {} }
            """;
        for (String keyword : List.of("package", "import", "public", "abstract", "sealed", "class", "permits",
            "protected", "transient", "volatile", "int", "private", "static", "final", "boolean", "byte", "short",
            "long", "char", "float", "double", "void", "throws", "native", "synchronized", "strictfp", "assert",
            "for", "if", "continue", "else", "break", "do", "while", "try", "throw", "new", "catch", "return",
            "finally", "this", "var", "switch", "case", "default", "yield", "extends", "super", "interface",
            "enum", "record", "implements", "non-sealed")) {
            assertStyle(source, keyword, "keyword");
        }
        assertStyle("class X { boolean f(Object o) { return o instanceof String s; } }", "instanceof", "keyword");
        assertStyle(
            "class X { int f(Object o) { return switch(o) { case String s when s.isEmpty() -> 1; default -> 0; }; } }",
            "when", "keyword");
    }

    @Test
    public void highlightsModulesAndTheirNames() {
        String source = "open module example.app { requires transitive java.base; exports example.api to other.app; "
            + "opens example.internal; uses example.Service; provides example.Service with example.Impl; }";
        for (String keyword : List.of("open", "module", "requires", "transitive", "exports", "to", "opens", "uses",
            "provides", "with")) {
            assertStyle(source, keyword, "keyword");
        }
        assertStyle(source, "example.app", "namespace", true);
    }

    @Test
    public void highlightsAllLiteralForms() {
        String source = "class X { Object[] values = { true, false, null, 12, 1.5e2, 0xAB, 077, 0b101, 0x1.fp3, 'a', \"hello\\n\" }; }";
        for (String literal : List.of("true", "false", "null")) {
            assertStyle(source, literal, "literal");
        }
        for (String number : List.of("12", "1.5e2", "0xAB", "077", "0b101", "0x1.fp3")) {
            assertStyle(source, number, "number");
        }
        assertStyle(source, "'a'", "string");
        assertStyle(source, "hello", "string");
        assertStyle(source, "\\n", "escape");
        String block = "class X { String s = \"\"\"\ntext block\n\"\"\"; }";
        assertStyle(block, "\"\"\"", "string");
        assertStyle(block, "text block", "string");
    }

    @Test
    public void distinguishesDeclarationsReferencesAndShadowing() {
        String source = """
            class Example {
                static final int LIMIT = 3;
                int field;
                Example() { this.field = LIMIT; }
                void method(int parameter, String... rest) {
                    int local = parameter;
                    field = local;
                    method(local, rest);
                    { int field = 2; field++; }
                    field++;
                    java.util.function.Function<Integer, Integer> fn = item -> item + 1;
                }
            }
            """;
        assertStyle(source, "Example", "type");
        assertStyle(source, "Example()", "constructor", true);
        assertEveryOccurrence(source, "LIMIT", "constant");
        assertEveryOccurrence(source, "parameter", "parameter");
        assertEveryOccurrence(source, "rest", "parameter");
        assertEveryOccurrence(source, "local", "variable");
        assertEveryOccurrence(source, "method", "method");
        assertEveryOccurrence(source, "item", "parameter");
        var styles = styles(source);
        int nested = source.indexOf("int field = 2");
        for (int at = source.indexOf("field"); at >= 0; at = source.indexOf("field", at + 5)) {
            String expected = at >= nested && at < source.indexOf("}", nested) ? "variable" : "field";
            assertTrue(styles.get(at).contains(expected), "field at " + at + ": " + styles.get(at));
        }
    }

    @Test
    public void highlightsAnnotationsGenericsLabelsAndReferences() {
        String source = """
            @interface Mark { String value(); }
            @Mark(value = "ok") record Data<T>(T component) {
                void test() {
                    label: for (;;) { break label; }
                    Object supplier = Data::new;
                    Object function = this::test;
                }
            }
            enum Mode { FIRST, SECOND }
            """;
        assertStyle(source, "@interface", "keyword");
        assertStyle(source, "@Mark", "annotation");
        assertStyle(source, "value =", "annotation", true);
        assertStyle(source, "component", "parameter");
        assertEveryOccurrence(source, "label", "label");
        assertEveryOccurrence(source, "test", "method");
        assertStyle(source, "FIRST", "constant");
        assertStyle(source, "SECOND", "constant");
    }

    @Test
    public void highlightsOperatorsPunctuationAndUnnamedPatterns() {
        String source = "class X { int[] a; void f() { a[0] += (1 << 2); boolean b = !true && false || 2 >= 1; Object r = x -> x; } }";
        for (String operator : List.of("+=", "<<", "=", "!", "&&", "||", ">=", "->")) {
            assertStyle(source, operator, "operator");
        }
        for (String punctuation : List.of("{", "}", "[", "]", "(", ")", ";")) {
            assertStyle(source, punctuation, "punctuation");
        }
        assertStyle("class X { void f() { try {} catch (Exception _) {} } }", "_", "variable");
    }

    @Test
    public void highlightsJavadocTagsAndReferences() {
        String source = "/** See {@link Example#run()} and {@code int}.\n * @param value input\n * @return result\n */ class Example {}";
        for (String tag : List.of("@link", "@code", "@param", "@return")) {
            assertStyle(source, tag, "doc-tag");
        }
        assertStyle(source, "Example#run()", "doc-link");
        assertStyle(source, "value", "doc-parameter");
        assertStyle(source, "input", "comment");
    }

    @Test
    public void preservesOffsetsForUnicodeWhitespaceAndIncompleteInput() {
        for (String source : List.of("", "  \n\t",
            "// caf\u00e9 \ud83d\ude00\nclass Caf\u00e9 { String s = \"\u4f60\u597d\ud83d\ude00\"; }  \n",
            "class X { void f( ", "/* unfinished")) {
            assertEquals(source.length(), styles(source).size(), source);
        }
        String source = "// caf\u00e9 \ud83d\ude00\nclass Caf\u00e9 { String s = \"\u4f60\u597d\ud83d\ude00\"; }  \n";
        assertStyle(source, "class", "keyword");
        assertStyle(source, "Caf\u00e9", "type");
        assertStyle(source, "\u4f60\u597d\ud83d\ude00", "string");
    }

    @Test
    public void preservesSpanLengthsAcrossTheParserCorpus() throws IOException {
        try (var files = Files.walk(Path.of(
            "src/test/resources/dev/railroadide/railroad/ide/sst/impl/java/corpus"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertEquals(source.length(), TreeSitterJavaSyntaxHighlighting.computeHighlighting(source).length(),
                    file.toString());
            }
        }
    }

    @Test
    public void distinguishesImportNamespacesAndEscapedCharacters() {
        String source = "package example.app; import java.util.List; import java.io.*; class X { char c = '\\n'; }";
        assertStyle(source, "example", "namespace");
        assertStyle(source, "app", "namespace");
        assertStyle(source, "java", "namespace");
        assertStyle(source, "util", "namespace");
        assertStyle(source, "List", "type");
        assertStyle(source, "io", "namespace");
        assertStyle(source, "\\n", "escape");
    }

    @Test
    public void endsStringHighlightingBeforeClosingParentheses() {
        String source = "class Railroad { Object logger = LoggerService.builder()"
            + ".configFile(ConfigHandler.getConfigDirectory().resolve(\"logger_config.json\")); }";
        assertStyle(source, "\"logger_config.json\"", "string");
        var styles = styles(source);
        int closingParentheses = source.indexOf("\"logger_config.json\"))") + "\"logger_config.json\"".length();
        assertEquals(List.of("punctuation"), styles.get(closingParentheses));
        assertEquals(List.of("punctuation"), styles.get(closingParentheses + 1));
        assertEquals(source.length(), styles.size());
    }

    private static List<Collection<String>> styles(String source) {
        var result = new ArrayList<Collection<String>>();
        for (var span : TreeSitterJavaSyntaxHighlighting.computeHighlighting(source)) {
            result.addAll(Collections.nCopies(span.getLength(), span.getStyle()));
        }
        return result;
    }

    private static void assertEveryOccurrence(String source, String token, String role) {
        var styles = styles(source);
        for (int start = source.indexOf(token); start >= 0; start = source.indexOf(token, start + token.length())) {
            assertTrue(styles.get(start).contains(role), token + " at " + start + ": " + styles.get(start));
        }
    }

    private static void assertStyle(String source, String token, String role) {
        assertStyle(source, token, role, false);
    }

    private static void assertStyle(String source, String token, String role, boolean firstOnly) {
        int start = source.indexOf(token);
        assertTrue(start >= 0, token);
        var styles = styles(source);
        for (int offset = start; offset < start + (firstOnly ? 1 : token.length()); offset++) {
            assertTrue(styles.get(offset).contains(role),
                token + " at " + offset + " expected " + role + " but was " + styles.get(offset));
        }
    }
}
