package dev.railroadide.railroad.ide.sst.project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/** Repeatable command-line timings for the cold and persisted Java index paths. */
public final class JavaIndexBenchmarkRunner {
    private JavaIndexBenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length == 0
            ? Path.of("").toAbsolutePath().normalize()
            : Path.of(args[0]).toAbsolutePath().normalize();
        boolean includeBinaryIndexes = Arrays.asList(args).contains("--binary-indexes");
        boolean saveCache = Arrays.asList(args).contains("--save-cache");
        List<Path> sources = sourceFiles(projectRoot);

        System.out.printf("Java index benchmark: root=%s, sourceFiles=%d%n", projectRoot, sources.size());
        var persistence = new JavaProjectSemanticPersistence();
        Timed<JavaProjectSemanticIndex> persisted = time(
            () -> persistence.loadIfCurrent(projectRoot, sources));
        print("persisted project index load", persisted.elapsedNanos(),
            persisted.value() == null ? "cache miss" : persisted.value().files().size() + " files");

        Timed<JavaProjectSemanticIndex> built = time(
            () -> new JavaProjectSemanticIndexer().build(sources));
        print("cold project index build", built.elapsedNanos(), built.value().files().size() + " files");
        if (saveCache) {
            Timed<Void> saved = time(() -> {
                persistence.save(projectRoot, built.value());
                return null;
            });
            print("project index cache save", saved.elapsedNanos(), built.value().files().size() + " files");
        }

        if (includeBinaryIndexes) {
            List<Path> classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(Path::of)
                .filter(Files::exists)
                .toList();
            Timed<JavaLibrarySymbolIndex> libraries = time(() -> JavaLibrarySymbolIndex.build(classpath));
            print("library index build", libraries.elapsedNanos(),
                libraries.value().declaredQualifiedNames().size() + " classes");
            Timed<JavaJdkSymbolIndex> jdk = time(JavaJdkSymbolIndex::fromCurrentRuntime);
            print("JDK index build", jdk.elapsedNanos(),
                jdk.value().declaredQualifiedNames().size() + " classes");
        }
    }

    private static List<Path> sourceFiles(Path projectRoot) throws IOException {
        List<Path> roots = List.of(
            projectRoot.resolve("src/main/java"),
            projectRoot.resolve("src/test/java"));
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root))
                continue;
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .forEach(files::add);
            }
        }
        files.sort(Path::compareTo);
        return List.copyOf(files);
    }

    private static <T> Timed<T> time(Callable<T> operation) throws Exception {
        long started = System.nanoTime();
        T value = operation.call();
        return new Timed<>(value, System.nanoTime() - started);
    }

    private static void print(String operation, long nanos, String detail) {
        System.out.printf("%-32s %8.3f s  (%s)%n", operation, nanos / 1_000_000_000.0, detail);
    }

    private record Timed<T>(T value, long elapsedNanos) {
    }
}
