package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.semantic.api.Type;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaInferenceRepairTest {
    private static final JavaSymbolIndex JDK = JavaJdkSymbolIndex.fromCurrentRuntime();
    @TempDir
    public Path directory;

    @Test
    public void conditionalStringMapper() throws Exception {
        check("import java.util.*; class Example { void f(List<String> xs) { "
            + "xs.stream().map(x -> x == null ? \"\" : x.strip()).filter(x -> !x.isBlank()).toList(); }}", true);
    }

    @Test
    public void literalTypeIgnoresShadowing() throws Exception {
        String source = "class String {} class Example { java.lang.String s = \"x\"; }";
        check(source, true);
        var model = JavaSemanticAnalyzer.analyzeFacts(source);
        var context = new JavaRuleContext(Path.of("Example.java"), source, model);
        var literal = context.nodesOfKind("JAVA_LITERAL_EXPRESSION").getFirst();
        assertEquals(new Type.DeclaredType("java.lang.String", List.of()), model.inferredType(literal).orElseThrow());
    }

    @Test
    public void literalCannotBeAssignedToShadowingClass() throws Exception {
        check("class String {} class Example { String s = \"x\"; }", false);
    }

    @Test
    public void textBlockCannotBeAssignedToShadowingClass() throws Exception {
        check("class String {} class Example { String s = \"\"\"\ntext\n\"\"\"; }", false);
    }

    private void check(String source, boolean valid) throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, source);
        var output = new ByteArrayOutputStream();
        int exit = ToolProvider.getSystemJavaCompiler().run(null, output, output,
            "--release", "25", "-proc:none", "-d", directory.toString(), file.toString());
        assertEquals(valid, exit == 0, () -> "javac: " + output);
        var diagnostics = JavaInspectionTestSupport.runProviders(
            List.of(new CoreAssignmentInspection(), new CoreCallResolutionInspection()),
            file, source, JDK);
        boolean errors = diagnostics.stream().anyMatch(d -> d.severity() == SemanticDiagnostic.Severity.ERROR);
        assertEquals(valid, !errors, () -> "SST: " + diagnostics);
    }
}
