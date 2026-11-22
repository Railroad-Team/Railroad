package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.project.CompositeJavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Protected members inherited from a compiled superclass in another package stay accessible through {@code this}.
 */
public class JavaInheritedProtectedAccessTest {
    @TempDir
    public static Path directory;
    private static JavaSymbolIndex index;

    @BeforeAll
    public static void compileBaseInAnotherPackage() throws Exception {
        Path source = Files.createDirectories(directory.resolve("base")).resolve("Base.java");
        Files.writeString(source, """
            package base;
            public class Base {
                protected int value;
                protected static int shared;
                protected void ping() {}
            }
            """);
        Path classes = Files.createDirectory(directory.resolve("classes"));
        var output = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, output, output, "--release", "25",
            "-proc:none", "-d", classes.toString(), source.toString()), output::toString);
        index = new CompositeJavaSymbolIndex(List.of(JavaLibrarySymbolIndex.build(List.of(classes)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
    }

    @Test
    public void thisQualifiedFieldIsAccessible() {
        assertAccessible("""
            package sub;
            import base.Base;
            class Sub extends Base { int read() { return this.value; } }
            """);
    }

    @Test
    public void thisQualifiedMethodIsAccessible() {
        assertAccessible("""
            package sub;
            import base.Base;
            class Sub extends Base { void run() { this.ping(); } }
            """);
    }

    @Test
    public void thisQualifiedFieldInsideLambdaIsAccessible() {
        assertAccessible("""
            package sub;
            import base.Base;
            class Sub extends Base { Runnable run() { return () -> this.value++; } }
            """);
    }

    @Test
    public void outerThisFromInnerClassIsAccessible() {
        assertAccessible("""
            package sub;
            import base.Base;
            class Sub extends Base {
                class Inner { int read() { return Sub.this.value; } }
            }
            """);
    }

    @Test
    public void thisQualifiedFieldInGenericSubclassIsAccessible() {
        assertAccessible("""
            package sub;
            import base.Base;
            abstract class Sub<T extends Sub<T>> extends Base { int read() { return this.value; } }
            """);
    }

    @Test
    public void directCallOnAnonymousClassUsesTheVisibilityOfItsOverride() {
        String source = """
            package sub;
            import base.Base;
            class Sub {
                void run() {
                    new Base() { @Override public void ping() {} }.ping();
                    (new Base() { @Override public void ping() {} }).ping();
                    new Base() {}.ping();
                }
            }
            """;
        List<SemanticDiagnostic> diagnostics = run(source);
        assertEquals(List.of("Call 'ping' is not accessible from here"),
            diagnostics.stream().map(SemanticDiagnostic::message).toList());
        assertEquals(source.lastIndexOf("ping"), diagnostics.getFirst().startOffset());
    }

    @Test
    public void unrelatedReceiverIsStillInaccessible() {
        List<SemanticDiagnostic> diagnostics = run("""
            package sub;
            import base.Base;
            class Sub extends Base { int read(Base other) { return other.value; } }
            """);
        assertEquals(List.of("Member 'value' is not accessible from here"),
            diagnostics.stream().map(SemanticDiagnostic::message).toList());
    }

    private static void assertAccessible(String source) {
        assertEquals(List.of(), run(source).stream().map(SemanticDiagnostic::message).toList());
    }

    private static List<SemanticDiagnostic> run(String source) {
        return JavaInspectionTestSupport.runProvider(new CoreAccessibilityInspection(), Path.of("sub/Sub.java"),
            source, index);
    }
}
