package dev.railroadide.railroad.ide.sst.javac;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OpenJdkJavacHarnessTest {
    @TempDir
    private Path checkout;

    @Test
    public void discoversTaggedCasesAndSelectsAnIndividualId() throws IOException {
        write("group/Test.java", """
            /* copyright */
            /*
             * @test
             * @compile
             *     -XDrawDiagnostics Test.java
             */
            /*
             * @test id=second
             * @run main Test
             */
            class Test {
                String fake = "/* @test */";
            }
            """);
        write("group/Support.java", "class Support {}\n");
        write("group/driver.sh", "#!/bin/sh\n# @test\n# @run shell driver.sh\n");
        var suite = new OpenJdkJavacSuite(checkout);
        assertEquals(List.of("group/Test.java#id0", "group/Test.java#second", "group/driver.sh"),
            suite.discover("group").stream().map(OpenJdkJavacSuite.TestCase::id).toList());
        assertEquals("-XDrawDiagnostics Test.java", suite.discover("group/Test.java#id0").getFirst()
            .tags().getLast().value());
        assertEquals("group/Test.java#second", suite.discover("group/Test.java#second").getFirst().id());
        assertThrows(IOException.class, () -> suite.discover("group/Test.java#missing"));
        assertThrows(IOException.class, () -> suite.discover("../../TEST.ROOT"));
    }

    @Test
    public void checksOrderedSourcesAndReferenceLocation() throws IOException {
        Path helper = checkout.resolve("test/lib/p/Helper.java");
        Files.createDirectories(helper.getParent());
        Files.writeString(helper, "package p; public class Helper {}\n");
        write("Positive.java", """
            /*
             * @test
             * @library /test/lib
             * @run clean Positive
             * @build p.Helper
             * @run compile Positive.java
             * @compile/fail/ref=Negative.out -XDrawDiagnostics Negative.java
             */
            class Positive extends p.Helper {}
            """);
        write("Negative.java", "class Negative extends Positive { int x; int x; }\n");
        write("Negative.out", "Negative.java:1:46: compiler.err.already.defined: kindname.variable, x\n1 error\n");

        var suite = new OpenJdkJavacSuite(checkout);
        var test = suite.discover("Positive.java").getFirst();
        try (var checks = new OpenJdkJavacChecks(suite.root())) {
            var result = checks.check(suite.root(), test);
            assertEquals(OpenJdkJavacChecks.Status.PASS, result.status(), result::toString);
            assertEquals(3, result.compileActions());
            assertEquals(3, result.syntaxFiles());

            write("Negative.out",
                "Negative.java:1:46: compiler.err.cant.resolve.location: kindname.variable, x\n1 error\n");
            result = checks.check(suite.root(), test);
            assertEquals(OpenJdkJavacChecks.Status.MISMATCH, result.status(), result::toString);
            assertTrue(result.findings().getFirst().contains("SEM_DUPLICATE_DECLARATION"));

            write("Negative.out", "Negative.java:1:1: compiler.err.already.defined: kindname.variable, x\n1 error\n");
            result = checks.check(suite.root(), test);
            assertEquals(OpenJdkJavacChecks.Status.MISMATCH, result.status(), result::toString);
        }
    }

    @Test
    public void resolvesSourcesJavacLoadsImplicitlyFromTheTestDirectory() throws IOException {
        write("implicit/Main.java", """
            /*
             * @test
             * @compile Main.java
             */
            import p.Helper;

            class Main extends Helper {
                Sibling sibling = new Sibling();
                int value = Helper.VALUE;
            }
            """);
        write("implicit/p/Helper.java", "package p; public class Helper { public static final int VALUE = 1; }\n");
        write("implicit/Sibling.java", "class Sibling {}\n");
        // javac never loads this file, so its conflicting declaration must stay invisible to SST.
        write("implicit/Unrelated.java", "class Main {}\nclass Sibling {}\n");

        var suite = new OpenJdkJavacSuite(checkout);
        try (var checks = new OpenJdkJavacChecks(suite.root())) {
            var result = checks.check(suite.root(), suite.discover("implicit/Main.java").getFirst());
            assertEquals(OpenJdkJavacChecks.Status.PASS, result.status(), result::toString);
            assertEquals(1, result.syntaxFiles());
        }
    }

    @Test
    public void matchesJavacSelectorAnchorToTheSstIdentifier() throws IOException {
        write("Selector.java", """
            /*
             * @test
             * @compile/fail/ref=Selector.out -XDrawDiagnostics Selector.java
             */
            class Selector {
            \tint read(Selector other) { return other.missing; }
            }
            """);
        write("Selector.out", "Selector.java:6:48: compiler.err.cant.resolve.location: kindname.variable, missing\n"
            + "1 error\n");
        var suite = new OpenJdkJavacSuite(checkout);
        try (var checks = new OpenJdkJavacChecks(suite.root())) {
            var result = checks.check(suite.root(), suite.discover("").getFirst());
            assertEquals(OpenJdkJavacChecks.Status.PASS, result.status(), result::toString);
        }
    }

    @Test
    public void matchesSelfReferenceWithinJavacLocalInferenceErrors() throws IOException {
        write("SelfReference.java", """
            /*
             * @test
             * @compile/fail/ref=SelfReference.out -XDrawDiagnostics SelfReference.java
             */
            class SelfReference {
                void run() { var x = x = 1; }
            }
            """);
        String reference = "SelfReference.java:6:22: compiler.err.cant.infer.local.var.type: x, (%s)\n1 error\n";
        write("SelfReference.out", reference.formatted("compiler.misc.local.self.ref"));
        var suite = new OpenJdkJavacSuite(checkout);
        var test = suite.discover("").getFirst();
        try (var checks = new OpenJdkJavacChecks(suite.root())) {
            var matching = checks.check(suite.root(), test);
            assertEquals(OpenJdkJavacChecks.Status.PASS, matching.status(), matching::toString);

            write("SelfReference.out", reference.formatted("compiler.misc.local.missing.init"));
            var otherInferenceError = checks.check(suite.root(), test);
            assertEquals(OpenJdkJavacChecks.Status.UNSUPPORTED, otherInferenceError.status(),
                otherInferenceError::toString);
        }
    }

    @Test
    public void reportsUnsupportedChecksWithoutTreatingThemAsPasses() throws IOException {
        write("Driver.java", """
            /*
             * @test
             * @run main Driver
             */
            class Driver { int value = missing; }
            """);
        write("Options.java", """
            /*
             * @test
             * @compile -processor Example Options.java
             */
            class Options {}
            """);
        write("Reference.java", """
            /*
             * @test
             * @compile/fail/ref=Reference.out -XDrawDiagnostics Reference.java
             */
            class Reference {}
            """);
        write("Reference.out", "- compiler.err.warnings.and.werror\n1 error\n");
        var suite = new OpenJdkJavacSuite(checkout);
        try (var checks = new OpenJdkJavacChecks(suite.root())) {
            for (var test : suite.discover("")) {
                var result = checks.check(suite.root(), test);
                assertEquals(OpenJdkJavacChecks.Status.UNSUPPORTED, result.status(), result::toString);
                assertEquals(1, result.syntaxFiles());
                assertFalse(result.unsupported().isEmpty());
            }
        }
    }

    @Test
    public void negativeCompileWithSemanticErrorStillRequiresCleanSyntax() throws IOException {
        write("Semantic.java", """
            /*
             * @test
             * @compile/fail Semantic.java
             */
            class Semantic { int value = missing; }
            """);
        var suite = new OpenJdkJavacSuite(checkout);
        try (var checks = new OpenJdkJavacChecks(suite.root())) {
            var result = checks.check(suite.root(), suite.discover("").getFirst());
            assertEquals(OpenJdkJavacChecks.Status.PASS, result.status(), result::toString);
            assertEquals(1, result.syntaxFiles());
            assertEquals(1, result.compileActions());
        }
    }

    @Test
    public void reportsAnInputErrorAndContinuesWithTheNextCase() throws IOException {
        write("Broken.java", "/*\n * @test\n * @compile Missing.java\n */\nclass Broken {}\n");
        write("Valid.java", "/*\n * @test\n * @compile Valid.java\n */\nclass Valid {}\n");
        var suite = new OpenJdkJavacSuite(checkout);
        var tests = suite.discover("");
        try (var checks = new OpenJdkJavacChecks(suite.root())) {
            var error = OpenJdkJavacRunner.runCase(checks, suite.root(), tests.getFirst());
            assertEquals(OpenJdkJavacChecks.Status.ERROR, error.status());
            assertTrue(error.findings().getFirst().contains("Missing test file"));
            var pass = OpenJdkJavacRunner.runCase(checks, suite.root(), tests.getLast());
            assertEquals(OpenJdkJavacChecks.Status.PASS, pass.status(), pass::toString);
        }
    }

    private void write(String relativePath, String text) throws IOException {
        Path langtools = checkout.resolve("test/langtools");
        Path file = langtools.resolve("tools/javac").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(langtools.resolve("TEST.ROOT"), "");
        Files.writeString(file, text);
    }
}
