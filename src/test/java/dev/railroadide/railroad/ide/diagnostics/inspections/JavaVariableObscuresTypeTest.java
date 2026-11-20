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
 * A library class whose simple name matches a variable, such as an obfuscated class {@code e}, must not turn the
 * variable's field accesses into static accesses on that class.
 */
public class JavaVariableObscuresTypeTest {
    @TempDir
    public static Path directory;
    private static JavaSymbolIndex index;

    @BeforeAll
    public static void compileUnrelatedTypeNamedE() throws Exception {
        Path source = Files.createDirectories(directory.resolve("other")).resolve("e.java");
        Files.writeString(source, "package other; public class e { public static int count; }");
        Path classes = Files.createDirectory(directory.resolve("classes"));
        var output = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, output, output, "--release", "25",
            "-proc:none", "-d", classes.toString(), source.toString()), output::toString);
        index = new CompositeJavaSymbolIndex(List.of(JavaLibrarySymbolIndex.build(List.of(classes)),
            JavaJdkSymbolIndex.fromCurrentRuntime()));
    }

    @Test
    public void lambdaParameterObscuresAnUnimportedType() {
        assertResolves("""
            record Key(String location) {
                static java.util.function.Function<Key, String> f() { return e -> "#" + e.location; }
            }
            """);
    }

    @Test
    public void methodParameterObscuresAnUnimportedType() {
        assertResolves("record Key(String location) { static String f(Key e) { return e.location; } }");
    }

    @Test
    public void localVariableObscuresAnUnimportedType() {
        assertResolves("record Key(String location) { static String f() { Key e = null; return e.location; } }");
    }

    @Test
    public void variableObscuresAnImportedType() {
        assertResolves("""
            import other.e;
            record Key(String location) { static String f(Key e) { return e.location; } }
            """);
    }

    @Test
    public void importedTypeStillProvidesStaticFieldsWithoutAVariable() {
        assertResolves("import other.e; class Key { static int f() { return e.count; } }");
    }

    private static void assertResolves(String source) {
        List<SemanticDiagnostic> diagnostics = JavaInspectionTestSupport.runProviders(
            List.of(new CoreMemberResolutionInspection(), new CoreAssignmentInspection()), Path.of("Key.java"),
            source, index);
        assertEquals(List.of(), diagnostics.stream().map(SemanticDiagnostic::message).toList());
    }
}
