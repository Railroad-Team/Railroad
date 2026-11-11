package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaBinaryWildcardInferenceTest {
    @TempDir
    public Path directory;
    private static final JavaSymbolIndex JDK = JavaJdkSymbolIndex.fromCurrentRuntime();

    @Test
    public void unboundedWildcardUsesDeclarationBound() throws Exception {
        check("int f() { return Api.bounded().get().intValue(); }", true);
    }

    @Test
    public void nestedUnboundedWildcardsPreserveBoundChain() throws Exception {
        check("int f() { return Api.diagnostic().location().range().start; }", true);
    }

    @Test
    public void boundedWildcardCannotCallUnrelatedMember() throws Exception {
        check("boolean f() { return Api.bounded().get().isBlank(); }", false);
    }

    @Test
    public void unboundedWildcardCannotBeReadAsString() throws Exception {
        check("void f() { String x = Api.unknown().get(); }", false);
    }

    @Test
    public void lowerBoundCanBeReadAsObject() throws Exception {
        check("void f() { Object x = Api.lower().get(); }", true);
    }

    @Test
    public void lowerBoundDoesNotProvideMembers() throws Exception {
        check("boolean f() { return Api.lower().get().isBlank(); }", false);
    }

    @Test
    public void lowerBoundCannotBeReadAsString() throws Exception {
        check("void f() { String x = Api.lower().get(); }", false);
    }

    @Test
    public void explicitUpperBoundPreserved() throws Exception {
        check("int f() { return Api.upper().get().intValue(); }", true);
    }

    private void check(String body, boolean valid) throws Exception {
        Path api = directory.resolve("Api.java");
        Files.writeString(api, """
            package api;
            public class Api {
                public static class Box<T> { public T get() { return null; } }
                public static class Bounded<T extends Number> { public T get() { return null; } }
                public static class Range<S> { public int start; }
                public interface Location<R extends Range<?>> { R range(); }
                public interface Diagnostic<L extends Location<?>> { L location(); }
                public static Bounded<?> bounded() { return null; }
                public static Box<?> unknown() { return null; }
                public static Box<? super String> lower() { return null; }
                public static Box<? extends Number> upper() { return null; }
                public static Diagnostic<?> diagnostic() { return null; }
            }
            """);
        Path classes = Files.createDirectory(directory.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var output = new ByteArrayOutputStream();
        assertEquals(0, compiler.run(null, output, output, "--release", "25", "-proc:none",
            "-d", classes.toString(), api.toString()), () -> output.toString());
        String source = "import api.Api; class Example { " + body + " }";
        Path caller = directory.resolve("Example.java");
        Files.writeString(caller, source);
        int exit = compiler.run(null, output, output, "--release", "25", "-proc:none",
            "-classpath", classes.toString(), "-d", directory.resolve("caller").toString(), caller.toString());
        assertEquals(valid, exit == 0, () -> "javac: " + output);
        // Only Api's class files are indexed: caller output cannot hide an inference failure.
        var index = new CompositeJavaSymbolIndex(List.of(JavaLibrarySymbolIndex.build(List.of(classes)), JDK));
        var diagnostics = JavaInspectionTestSupport.runProviders(
            List.of(new CoreAssignmentInspection(), new CoreCallResolutionInspection(),
                new CoreMemberResolutionInspection()),
            caller, source, index);
        boolean errors = diagnostics.stream().anyMatch(d -> d.severity() == SemanticDiagnostic.Severity.ERROR);
        assertEquals(valid, !errors, () -> "SST: " + diagnostics);
    }
}
