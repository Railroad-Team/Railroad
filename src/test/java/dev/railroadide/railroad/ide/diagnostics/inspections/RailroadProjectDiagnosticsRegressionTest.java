package dev.railroadide.railroad.ide.diagnostics.inspections;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.railroadide.railroad.ide.classparser.ClassStubParser;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Post-build source-plus-binary regression using Gradle's fresh compiled outputs.
 * This is not an independent source-only compiler correctness test; own bytecode
 * supplies generated members and other facts absent from the source index.
 */
public class RailroadProjectDiagnosticsRegressionTest {
    private static final Set<String> SOURCE_SETS = Set.of("main", "test", "formatter",
        "cssValidation", "cssValidationTest", "javadocCoverageTool", "javadocCoverageTest");
    private static final Path CORPUS = Path.of(
        "src/test/resources/dev/railroadide/railroad/ide/sst/impl/java/corpus").toAbsolutePath().normalize();
    private static final InspectionSettingsAccess DEFAULTS = new InspectionSettingsAccess() {
        @Override
        public boolean isEnabled(LanguageInspectionRule<?> rule) {
            return true;
        }

        @Override
        public SemanticDiagnostic.Severity effectiveSeverity(LanguageInspectionRule<?> rule) {
            return rule.defaultSeverity();
        }
    };

    private record CompilationContext(
        String name,
        List<String> sourceRoots,
        List<String> sources,
        List<String> sourceDependencies,
        List<String> compileClasspath,
        List<String> outputs,
        String javaHome,
        int release
    ) {
    }

    private record ErrorRecord(String sourceSet, String path, int start_offset, String code, String message) {
    }

    @Test
    public void currentRailroadSourcesHaveNoErrorDiagnostics() throws Exception {
        assertTrue(System.getenv("RAILROAD_SCAN_PATH") == null || System.getenv("RAILROAD_SCAN_PATH").isBlank(),
            "RAILROAD_SCAN_PATH would reduce regression coverage; unset it");
        String manifest = System.getProperty("railroad.selfAnalysis.contexts");
        assertNotNull(manifest, "Run via Gradle test: required build-context manifest is unavailable");
        var contexts = List.of(new Gson().fromJson(Files.readString(Path.of(manifest)), CompilationContext[].class));
        assertEquals(SOURCE_SETS, new HashSet<>(contexts.stream().map(CompilationContext::name).toList()),
            "Unexpected source-set omission/addition: update the explicit coverage boundary");
        assertEquals(SOURCE_SETS.size(), contexts.size(), "Duplicate compilation contexts");
        Path scanRoot = Path.of("src").toAbsolutePath().normalize();
        Set<Path> accounted = new HashSet<>();
        for (var context : contexts) {
            assertEquals(Runtime.version().feature(), context.release(), "Worker JDK must match release");
            assertEquals(Path.of(System.getProperty("java.home")).toRealPath(),
                Path.of(context.javaHome()).toRealPath(),
                "Worker must use the compilation toolchain JDK");
            assertFalse(context.sources().isEmpty(), "Empty source set: " + context.name());
            for (String source : context.sources()) {
                Path path = requiredPath(source);
                assertTrue(accounted.add(path), "Source belongs to multiple source sets: " + path);
                assertTrue(context.sourceRoots().stream().map(Path::of).anyMatch(path::startsWith),
                    "Source outside declared roots: " + path);
            }
            for (String output : context.outputs()) {
                try (var paths = Files.walk(requiredPath(output))) {
                    assertTrue(paths.anyMatch(path -> path.toString().endsWith(".class")),
                        "Empty compiled output: " + output);
                }
            }
            assertFalse(context.outputs().isEmpty(), "No compiled outputs: " + context.name());
        }
        // Preserve the old ten valid parser-resource fixtures, independently of source sets.
        List<Path> fixtures = javaFiles(CORPUS.resolve("valid"));
        assertEquals(10, fixtures.size(), "Unexpected valid-corpus coverage change");
        assertEquals(4, javaFiles(CORPUS.resolve("recovery")).size(), "Unexpected recovery-fixture change");
        accounted.addAll(fixtures);
        accounted.addAll(javaFiles(CORPUS.resolve("recovery")));
        assertEquals(new HashSet<>(javaFiles(scanRoot)), accounted,
            "Java files under src must be in a declared source set or the explicit corpus groups");

        System.out.println("SST post-build regression: JDK " + System.getProperty("java.runtime.version")
            + ", home=" + System.getProperty("java.home") + ", maxHeap=" + Runtime.getRuntime().maxMemory());
        // Strict scanner reuses the production class parser, but propagates failures rather
        // than JavaLibrarySymbolIndex's log-and-skip behavior. Cache per root across contexts.
        Map<Path, Map<String, ClassStub>> binaries = new HashMap<>();
        JavaSymbolIndex jdk = currentJdkIndex();
        assertNotNull(jdk.lookupClassStub("java.lang.Object"), "Failed JDK context construction");
        List<ErrorRecord> errors = new ArrayList<>();
        List<JavaInspectionRuleProvider> providers = errorProviders();
        assertEquals(13, providers.size());
        for (var context : contexts) {
            List<Path> sources = context.sources().stream().map(Path::of).sorted().toList();
            List<Path> classpath = new ArrayList<>(context.compileClasspath().stream().map(Path::of).toList());
            classpath.addAll(context.outputs().stream().map(Path::of).toList());
            JavaSymbolIndex index;
            try {
                // Include source facts only for project outputs actually on this compile classpath.
                // This preserves the hybrid main-source assistance used by application tests.
                List<Path> indexedSources = new ArrayList<>(sources);
                for (String dependency : context.sourceDependencies()) {
                    var sourceDependency = contexts.stream().filter(candidate -> candidate.name().equals(dependency))
                        .findFirst().orElseThrow(() -> new AssertionError("Unknown source dependency: " + dependency));
                    indexedSources.addAll(sourceDependency.sources().stream().map(Path::of).toList());
                }
                index = new CompositeJavaSymbolIndex(List.of(new JavaProjectSemanticIndexer().build(indexedSources),
                    binaryIndex(classpath, binaries), jdk));
            } catch (Exception exception) {
                throw new AssertionError("Failed context construction: " + context.name(), exception);
            }
            analyze(context.name(), sources, index, providers, scanRoot, errors);
            if (context.name().equals("main")) {
                analyze("validCorpus", fixtures, index, providers, scanRoot, errors);
            }
        }
        Path report = Path.of(manifest).resolveSibling("diagnostics.json");
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(errors));
        assertTrue(errors.isEmpty(), () -> errors.size() + " ERROR diagnostics; ledger: " + report + "\n"
            + String.join("\n", errors.stream().map(Object::toString).toList()));
    }

    private static Path requiredPath(String value) {
        Path path = Path.of(value).toAbsolutePath().normalize();
        assertTrue(Files.exists(path) && Files.isReadable(path), "Unavailable analysis input: " + path);
        return path;
    }

    private static List<Path> javaFiles(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
                .sorted().toList();
        }
    }

    private static JavaSymbolIndex currentJdkIndex() throws Exception {
        // JDK 25 distributions can omit jmods; jrt is the selected worker's runtime image.
        Path modules = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("/modules");
        Map<String, ClassStub> stubs = new LinkedHashMap<>();
        Map<String, Path> origins = new LinkedHashMap<>();
        try (var paths = Files.walk(modules)) {
            for (Path path : paths.filter(path -> isClass(path.toString())).sorted().toList()) {
                try (var input = Files.newInputStream(path)) {
                    ClassStub stub = ClassStubParser.parse(new ClassReader(input));
                    stubs.putIfAbsent(stub.getFullName(), stub);
                    origins.putIfAbsent(stub.getFullName(), Path.of(System.getProperty("java.home")));
                }
            }
        }
        return new JavaStubSymbolIndex(stubs, origins);
    }

    private static JavaSymbolIndex binaryIndex(List<Path> roots, Map<Path, Map<String, ClassStub>> cache)
        throws Exception {
        Map<String, ClassStub> stubs = new LinkedHashMap<>();
        Map<String, Path> origins = new LinkedHashMap<>();
        for (Path value : roots) {
            Path root = requiredPath(value.toString());
            Map<String, ClassStub> entries = cache.get(root);
            if (entries == null) {
                entries = new LinkedHashMap<>();
                if (Files.isDirectory(root)) {
                    try (var paths = Files.walk(root)) {
                        for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                            if (isClass(path.toString())) {
                                try (var input = Files.newInputStream(path)) {
                                    ClassStub stub = ClassStubParser.parse(new ClassReader(input));
                                    entries.putIfAbsent(stub.getFullName(), stub);
                                }
                            }
                        }
                    }
                } else {
                    assertTrue(root.toString().endsWith(".jar") || root.toString().endsWith(".jmod"),
                        "Unsupported binary input: " + root);
                    try (var archive = new JarFile(root.toFile())) {
                        for (var entry : archive.stream().filter(entry -> isClass(entry.getName())).toList()) {
                            try (var input = archive.getInputStream(entry)) {
                                ClassStub stub = ClassStubParser.parse(new ClassReader(input));
                                entries.putIfAbsent(stub.getFullName(), stub);
                            }
                        }
                    }
                }
                cache.put(root, entries);
            }
            for (var entry : entries.entrySet()) {
                stubs.putIfAbsent(entry.getKey(), entry.getValue());
                origins.putIfAbsent(entry.getKey(), root);
            }
        }
        return new JavaStubSymbolIndex(stubs, origins);
    }

    private static boolean isClass(String path) {
        return path.endsWith(".class") && !path.endsWith("module-info.class") && !path.endsWith("package-info.class");
    }

    private static void analyze(
        String name,
        List<Path> sources,
        JavaSymbolIndex index,
        List<JavaInspectionRuleProvider> providers,
        Path scanRoot,
        List<ErrorRecord> errors
    ) throws Exception {
        int before = errors.size();
        int parallelism = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        try (var executor = Executors.newFixedThreadPool(parallelism)) {
            List<Future<List<ErrorRecord>>> futures = new ArrayList<>();
            for (Path source : sources) {
                futures.add(executor.submit(() -> {
                    String document = Files.readString(source);
                    var model = JavaSemanticAnalyzer.analyzeFacts(document, index);
                    var context = new JavaRuleContext(source, document, model, index);
                    List<ErrorRecord> reports = new ArrayList<>();
                    for (var provider : providers) {
                        for (var rule : provider.rules()) {
                            assertNotNull(rule, "Null rule in " + provider.id());
                            var reporter = new DefaultLanguageInspectionRuleReporter<>(rule, diagnostic -> {
                                if (diagnostic.severity() == SemanticDiagnostic.Severity.ERROR) {
                                    reports.add(new ErrorRecord(name, scanRoot.relativize(source).toString()
                                        .replace('\\', '/'), diagnostic.startOffset(), diagnostic.code(),
                                        diagnostic.message()));
                                }
                            }, DEFAULTS);
                            try {
                                // Direct evaluation keeps rule exceptions visible, with immutable default settings.
                                rule.evaluate(context, reporter);
                            } catch (Exception exception) {
                                throw new AssertionError("Failed rule " + provider.id() + ":" + rule.id(), exception);
                            }
                        }
                    }
                    return reports;
                }));
            }
            for (int i = 0; i < futures.size(); i++) {
                try {
                    errors.addAll(futures.get(i).get());
                } catch (ExecutionException exception) {
                    throw new AssertionError("Failed to analyze " + name + ": " + sources.get(i), exception.getCause());
                }
            }
        }
        System.out.println("SST context " + name + ": " + sources.size() + " sources, "
            + (errors.size() - before) + " ERROR diagnostics");
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
            new CoreTypeResolutionInspection());
    }
}
