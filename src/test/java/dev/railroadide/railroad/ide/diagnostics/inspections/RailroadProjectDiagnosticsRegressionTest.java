package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slow, project-wide regression kept separate from focused inspection unit tests. */
class RailroadProjectDiagnosticsRegressionTest {
    @Test
    void currentRailroadSourcesHaveNoErrorDiagnostics() throws Exception {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath().normalize();
        Path scanRoot = Path.of("src").toAbsolutePath().normalize();
        List<Path> runtimeClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .filter(Files::exists)
            .toList();
        JavaSymbolIndex symbolIndex = new CompositeJavaSymbolIndex(List.of(
            new JavaProjectSemanticIndexer().build(sourceRoot),
            JavaLibrarySymbolIndex.build(runtimeClasspath),
            JavaJdkSymbolIndex.fromCurrentRuntime()
        ));
        List<Path> sourceFiles = sourceFiles(scanRoot);
        List<JavaInspectionRuleProvider> errorProviders = errorProviders();

        int parallelism = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        var executor = Executors.newFixedThreadPool(parallelism);
        List<Future<List<String>>> futures = new ArrayList<>(sourceFiles.size());
        try {
            for (Path sourceFile : sourceFiles) {
                futures.add(executor.submit(() -> JavaInspectionTestSupport.runProviders(
                        errorProviders, sourceFile, Files.readString(sourceFile), symbolIndex).stream()
                    .filter(diagnostic -> diagnostic.severity() == SemanticDiagnostic.Severity.ERROR)
                    .map(diagnostic -> scanRoot.relativize(sourceFile) + ":"
                        + diagnostic.startOffset() + " " + diagnostic.code() + " " + diagnostic.message())
                    .toList()));
            }

            List<String> errors = new ArrayList<>();
            for (int index = 0; index < futures.size(); index++) {
                try {
                    errors.addAll(futures.get(index).get());
                } catch (ExecutionException exception) {
                    throw new AssertionError("Failed to analyze " + sourceFiles.get(index), exception.getCause());
                }
            }
            assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<Path> sourceFiles(Path scanRoot) throws Exception {
        String filter = System.getenv("RAILROAD_SCAN_PATH");
        List<String> fragments = filter == null || filter.isBlank()
            ? List.of()
            : Arrays.stream(filter.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
        try (Stream<Path> paths = Files.walk(scanRoot)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("corpus" + File.separator + "recovery" + File.separator))
                .filter(path -> fragments.isEmpty()
                    || fragments.stream().anyMatch(fragment -> path.toString().contains(fragment)))
                .toList();
        }
    }

    private static List<JavaInspectionRuleProvider> errorProviders() {
        return List.of(
            new CoreAccessibilityInspection(),
            new CoreAssignmentInspection(),
            new CoreCallResolutionInspection(),
            new CoreControlFlowInspection(),
            new CoreDefiniteAssignmentInspection(),
            new CoreDuplicateDeclarationInspection(),
            new CoreExceptionInspection(),
            new CoreImportInspection(),
            new CoreInheritanceInspection(),
            new CoreMemberResolutionInspection(),
            new CoreModifierInspection(),
            new CoreNameResolutionInspection(),
            new CoreTypeResolutionInspection()
        );
    }
}
