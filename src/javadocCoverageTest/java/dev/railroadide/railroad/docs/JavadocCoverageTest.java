package dev.railroadide.railroad.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavadocCoverageTest {
    @TempDir
    private Path temporary;

    @Test
    public void requiresDescriptionsParametersTypeParametersAndNonVoidReturns() throws IOException {
        var report = analyze("""
            package example;
            /** API. */
            public class Api {
                public static final String NAME = "example";
                /** Does work.
                 * @param input
                 * @param wrong not the real parameter
                 * @return
                 */
                public <T> T work(T input) { return input; }
                /** Runs. */
                public void run() {}
                /** Constructs.
                 * @param value the value
                 */
                public Api(int value) {}
                public int missing(int count) { return count; }
            }
            """);
        assertEquals(6, report.total());
        assertEquals(3, report.complete());
        assertEquals(
            List.of("Missing or empty @param <T>", "Missing or empty @param input", "Missing or empty @return"),
            entry(report, "work(").issues());
        assertEquals(List.of("Missing Javadoc", "Missing or empty @param count", "Missing or empty @return"),
            entry(report, "missing(").issues());
        assertEquals(List.of("Missing Javadoc"), entry(report, "NAME").issues());
        assertTrue(entry(report, "Api(int").complete());
        assertTrue(entry(report, "work(").line() > 1);
        assertEquals("Api.java", entry(report, "work(").source());
    }

    @Test
    public void handlesImplicitPublicInterfaceAndAnnotationMembersAndInaccessibleTypes() throws IOException {
        var report = analyze("""
            package example;
            /** API. */
            public interface Api {
                int LIMIT = 5;
                int compute(int count);
                default void run() {}
                static void start() {}
                private void helper() {}
                /** Nested API. */
                class Nested {
                    public void visible() {}
                    protected void protectedMethod() {}
                    private void hidden() {}
                    void internal() {}
                }
                @interface Option {
                    String value() default "";
                }
            }
            class Hidden {
                public void ignored() {}
                public static class AlsoHidden { public void ignored() {} }
            }
            """);
        assertEquals(List.of("Api", "Api.Nested", "Api.Option"),
            report.classes().stream().map(JavadocCoverage.TypeCoverage::name).toList());
        assertEquals(9, report.total());
        assertEquals(List.of("Missing Javadoc", "Missing or empty @return"), entry(report, "value(").issues());
        assertTrue(report.classes().stream().flatMap(type -> type.entries().stream())
            .noneMatch(entry -> entry.name().contains("hidden") || entry.name().contains("helper")
                || entry.name().contains("internal")));
    }

    @Test
    public void countsEnumConstantsAndPublicStaticFinalFieldsButNotGeneratedMembers() throws IOException {
        var report = analyze("""
            /** Choices. */
            public enum Api {
                /** First choice. */
                FIRST,
                SECOND { public void anonymousMethod() {} };
                /** Shared objects. */
                public static final Object OBJECT = new Object();
                public static final int LEFT = 1, RIGHT = 2;
                public final int instanceField = 1;
                public static int mutable = 1;
                private static final int PRIVATE = 1;
                protected static final int PROTECTED = 1;
            }
            """);
        assertEquals(6, report.total());
        assertEquals(3, report.complete());
        assertEquals("Constant", entry(report, "SECOND").kind());
        assertEquals(List.of("Missing Javadoc"), entry(report, "RIGHT").issues());
    }

    @Test
    public void acceptsDocumentedGenericsInlineReturnsAndMarkdownComments() throws IOException {
        var report = analyze("""
            /// A generic API.
            /// @param <T> value type
            public class Api<T> {
                /** {@return the supplied value}
                 * @param <R> result type
                 * @param value the value
                 */
                public <R> R identity(R value) { return value; }
                /** Does nothing. */
                public void noop() {}
            }
            """);
        assertEquals(3, report.total());
        assertEquals(0, report.incomplete());
    }

    @Test
    public void requiresRecordComponentDocsWithoutInventingImplicitAccessors() throws IOException {
        var report = analyze("""
            /** A pair.
             * @param <T> value type
             * @param first first value
             */
            public record Api<T>(T first, int second) {
                /** Constructs a pair.
                 * @param first first value
                 * @param second second value
                 */
                public Api {}
            }
            """);
        assertEquals(2, report.total());
        assertEquals(List.of("Missing or empty @param second"),
            report.classes().getFirst().entries().getFirst().issues());
        assertTrue(entry(report, "Api(T").complete());
    }

    @Test
    public void doesNotCreditUnverifiedInheritedDocsOrEmptyComments() throws IOException {
        var report = analyze("""
            /** */
            public class Api {
                /** {@inheritDoc} */
                public int hashCode() { return 1; }
                /** <p></p> */
                public void empty() {}
                /** Works.
                 * @param value <b></b>
                 */
                public void markup(int value) {}
                /** Works.
                 * @param value {@code }
                 * @return {@literal }
                 */
                public int emptyInline(int value) { return value; }
            }
            """);
        assertEquals(0, report.complete());
        assertTrue(entry(report, "hashCode(").issues().stream()
            .anyMatch(issue -> issue.startsWith("Inherited documentation")));
        assertEquals(List.of("Missing or empty @param value"), entry(report, "markup(").issues());
        assertEquals(List.of("Missing or empty @param value", "Missing or empty @return"),
            entry(report, "emptyInline(").issues());
    }

    @Test
    public void excludesAnnotatedOverridesFromCoverageIncludingParametersAndReturns() throws IOException {
        var report = analyze("""
            /** API. */
            public class Api extends missing.Parent {
                @Override public String toString() { return ""; }
                /** {@inheritDoc} */
                @java.lang.Override public int compute(int input) { return input; }
                /** Broken {@link */
                @Override public int hashCode() { return 1; }
                public int compute(String input) { return 1; }
                /** Nested API. */
                public interface Nested extends missing.Contract {
                    @Override int read(int input);
                    int ownMethod(int value);
                }
            }
            """);
        assertEquals(4, report.total());
        assertEquals(2, report.complete());
        var names = report.classes().stream().flatMap(type -> type.entries().stream())
            .map(JavadocCoverage.Entry::name).toList();
        assertEquals(List.of("Api", "compute(String input) : int", "Api.Nested", "ownMethod(int value) : int"), names);
        assertEquals(List.of("Missing Javadoc", "Missing or empty @param input", "Missing or empty @return"),
            entry(report, "compute(").issues());
    }

    @Test
    public void flagsMalformedComments() throws IOException {
        var report = analyze("""
            /** API. */
            public class Api {
                /** Broken {@link */
                public void broken() {}
            }
            """);
        assertTrue(entry(report, "broken(").issues().stream().anyMatch(issue -> issue.startsWith("Malformed Javadoc")));
    }

    @Test
    public void parsesWithoutDependenciesAndRejectsSyntaxErrors() throws IOException {
        var report = analyze("""
            import missing.Dependency;
            /** API. */
            public class Api extends Dependency {
                /** Reads a value. @return unused */
                public Dependency read() { return null; }
            }
            """);
        assertEquals(2, report.total());
        assertThrows(IOException.class, () -> analyze("public class Api { public void broken( }"));
    }

    @Test
    public void rendersEscapedOverloadsPackageNavigationAndStandaloneAssets() throws IOException {
        var report = analyze("""
            package example;
            /** API. */
            public class Api {
                public <T> T overloaded(T value) { return value; }
                public int overloaded(int value) { return value; }
            }
            """);
        String html = JavadocCoverage.render(report);
        assertTrue(html.contains("Missing or empty @param &lt;T&gt;"));
        assertFalse(html.contains("@param <T>"));
        assertTrue(html.contains("aria-label=\"Packages\""));
        assertTrue(html.contains("class=\"package\""));
        assertTrue(html.contains("class=\"type\""));
        assertTrue(html.contains("overloaded(T value) : T"));
        assertTrue(html.contains("overloaded(int value) : int"));
        assertFalse(html.contains("<script src="));
        var ids = Pattern.compile(" id=\"([^\"]+)\"").matcher(html).results()
            .map(match -> match.group(1)).toList();
        assertEquals(ids.size(), ids.stream().distinct().count());
    }

    @Test
    public void templateEscapesReportDataAndEmbedsAssetsVerbatim() throws IOException {
        String text = "<script>alert(\"quoted\")</script> & ${7 * 7}";
        var entry = new JavadocCoverage.Entry("Method", text, text + ".java", 1001, List.of(text));
        var type = new JavadocCoverage.TypeCoverage(text, text, List.of(entry));
        String html = JavadocCoverage.render(new JavadocCoverage.Report(List.of(type)));
        assertTrue(html.contains("&lt;script&gt;alert(&quot;quoted&quot;)&lt;/script&gt; &amp; ${7 * 7}"));
        assertFalse(html.contains(text));
        assertTrue(html.contains(".java:1001"));
        assertTrue(html.contains("data-complete=\"false\""));
        for (String asset : List.of("report.css", "report.js")) {
            try (var input = JavadocCoverage.class.getResourceAsStream(asset)) {
                assertTrue(html.contains(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
    }

    @Test
    public void handlesEmptyScopeAndWritesViolationCountBeforeStrictCheck() throws IOException {
        var empty = JavadocCoverage.analyze(temporary, List.of());
        assertEquals(0, empty.total());
        assertTrue(JavadocCoverage.render(empty).contains("N/A"));
        assertTrue(JavadocCoverage.render(empty).contains("No public API declarations found"));
        var source = temporary.resolve("Api.java");
        Files.writeString(source, "public class Api {}");
        var manifest = temporary.resolve("sources.txt");
        Files.writeString(manifest, source.toString());
        var output = temporary.resolve("report");
        String[] args = {temporary.toString(), manifest.toString(), output.toString()};
        JavadocCoverage.main(args);
        assertEquals("1", Files.readString(output.resolve("violations.txt")));
        assertTrue(Files.exists(output.resolve("index.html")));
        Files.writeString(source, "public class Api { invalid");
        assertThrows(IOException.class, () -> JavadocCoverage.main(args));
        assertFalse(Files.exists(output.resolve("index.html")));
        assertFalse(Files.exists(output.resolve("violations.txt")));
    }

    private JavadocCoverage.Report analyze(String source) throws IOException {
        var file = temporary.resolve("Api.java");
        Files.writeString(file, source);
        return JavadocCoverage.analyze(temporary, List.of(file));
    }

    private JavadocCoverage.Entry entry(JavadocCoverage.Report report, String namePrefix) {
        return report.classes().stream().flatMap(type -> type.entries().stream())
            .filter(entry -> entry.name().startsWith(namePrefix)).findFirst().orElseThrow();
    }
}
