package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRegistries;
import dev.railroadide.railroad.ide.sst.project.CompositeJavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaJdkSymbolIndex;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndexer;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Names that javac accepts must not produce SST errors. Each case comes from a javac test that SST rejected.
 */
public class JavaNameResolutionComplianceTest {
    @TempDir
    private Path directory;

    @Test
    public void staticImportsFromNestedBinaryTypesResolve() {
        assertClean("""
            import static java.lang.Thread.State.NEW;
            import static java.lang.Thread.State.*;
            class Imports {
                Object single = NEW;
                Object onDemand = RUNNABLE;
            }
            """);
    }

    @Test
    public void qualifiedCallIsNotAmbiguousWithStaticOnDemandImports() {
        assertClean("""
            import static java.lang.Thread.State.*;
            import static java.util.concurrent.TimeUnit.*;
            class Imports {
                Object state = Thread.State.valueOf("NEW");
            }
            """);
    }

    @Test
    public void onDemandImportOfABinaryTypeImportsItsMemberTypes() {
        assertClean("""
            import java.util.Map.*;
            class Imports { Entry<String, String> entry; }
            """);
    }

    @Test
    public void expressionQualifierSelectsStaticMembers() {
        assertClean("""
            class Qualifier {
                static Qualifier shared;
                static int count() { return 0; }
                static class Holder { private static int value; }
                void use(Qualifier other, Holder holder) {
                    other.shared = null;
                    holder.value = other.count();
                }
            }
            """);
    }

    @Test
    public void defaultPackageTypeNamedLikeATypeVariableKeepsItsMembers() {
        assertClean("""
            class H {
                K k = new K();
                { Object value = k.value; }
            }
            class K { final K value = this; }
            """);
    }

    @Test
    public void localClassShadowsMemberTypeOfTheEnclosingClass() {
        assertClean("""
            class Shadow {
                static void test() {
                    class A { int x = 1; }
                    class B extends A {}
                    int value = new B().x;
                }
                static class A {}
            }
            """);
    }

    @Test
    public void staticFinalMethodsAreAllowed() {
        assertClean("""
            class Methods { static final void run() {} }
            """);
    }

    @Test
    public void defaultPackageTypesFromOtherFilesQualifyExpressions() throws IOException {
        Map<String, String> files = Map.of(
            "Helper.java", "public class Helper { public static final String TEMPLATE = \"\"; }\n",
            "Signature.java", "public @interface Signature { String descriptor(); }\n",
            "Main.java", """
                import java.util.function.Function;
                class Main extends Helper {
                    String template = Helper.TEMPLATE;
                    Function<Signature, String> descriptor = Signature::descriptor;
                }
                """);
        for (var file : files.entrySet()) {
            Files.writeString(directory.resolve(file.getKey()), file.getValue());
        }
        JavaSymbolIndex index = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(directory), JavaJdkSymbolIndex.fromCurrentRuntime()));
        assertClean(files.get("Main.java"), directory.resolve("Main.java"), index);
    }

    private static void assertClean(String source) {
        assertClean(source, Path.of("Source.java"), JavaJdkSymbolIndex.fromCurrentRuntime());
    }

    private static void assertClean(String source, Path file, JavaSymbolIndex index) {
        List<SemanticDiagnostic> diagnostics = JavaInspectionTestSupport.runProviders(
            JavaInspectionRegistries.coreRuleProviders(), file, source, index);
        assertEquals(List.of(), diagnostics.stream()
            .filter(diagnostic -> diagnostic.severity() == SemanticDiagnostic.Severity.ERROR)
            .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
            .toList());
    }
}
