package dev.railroadide.railroad.ide.sst.impl.java;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles Java sources with the system compiler and runs a static {@code String run()} entry point.
 */
public final class CompiledJava {
    private static final AtomicInteger BUILDS = new AtomicInteger();

    private CompiledJava() {
    }

    /**
     * Compiles sources and returns the compiler's error messages.
     *
     * @param directory scratch directory
     * @param sources source text by path relative to the source root
     * @return error messages, empty when the sources compile
     */
    public static List<String> errors(Path directory, Map<String, String> sources) {
        return compile(directory, sources).errors();
    }

    /**
     * Compiles sources, which must compile, and invokes {@code public static String run()} of a class.
     *
     * @param directory scratch directory
     * @param sources source text by path relative to the source root
     * @param className binary name of the class to run
     * @return value returned by {@code run()}
     */
    public static String run(Path directory, Map<String, String> sources, String className) {
        Build build = compile(directory, sources);
        assertTrue(build.errors().isEmpty(), () -> "Expected the sources to compile: " + build.errors() + "\n"
            + sources);
        try (var loader = new URLClassLoader(new URL[]{build.classes().toUri().toURL()},
            CompiledJava.class.getClassLoader().getParent())) {
            return (String) loader.loadClass(className).getMethod("run").invoke(null);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not run " + className, exception);
        }
    }

    private static Build compile(Path directory, Map<String, String> sources) {
        Path root = directory.resolve("javac-" + BUILDS.incrementAndGet());
        Path sourceRoot = root.resolve("src");
        Path classes = root.resolve("classes");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
            StandardCharsets.UTF_8)) {
            Files.createDirectories(classes);
            List<Path> paths = new ArrayList<>();
            for (Map.Entry<String, String> source : sources.entrySet()) {
                Path path = sourceRoot.resolve(source.getKey());
                Files.createDirectories(path.getParent());
                Files.writeString(path, source.getValue());
                paths.add(path);
            }

            compiler.getTask(null, files, diagnostics, List.of("-d", classes.toString(), "-proc:none", "-Xlint:none"),
                null, files.getJavaFileObjectsFromPaths(paths)).call();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        List<String> errors = diagnostics.getDiagnostics().stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(diagnostic -> diagnostic.getSource().getName() + ":" + diagnostic.getLineNumber() + ": "
                + diagnostic.getMessage(Locale.ROOT))
            .toList();
        return new Build(classes, errors);
    }

    private record Build(Path classes, List<String> errors) {
    }
}
