package dev.railroadide.railroad.ide.sst.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Builds a project-wide semantic index by extracting declaration facts from
 * Java source files.
 */
public final class JavaProjectSemanticIndexer {
    private final JavaProjectSemanticExtractor extractor;

    public JavaProjectSemanticIndexer() {
        this(new JavaProjectSemanticExtractor());
    }

    public JavaProjectSemanticIndexer(JavaProjectSemanticExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
    }

    public JavaProjectSemanticIndex build(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .toList();
            return build(javaFiles);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to build semantic index for " + projectRoot, exception);
        }
    }

    public JavaProjectSemanticIndex build(List<Path> sourceFiles) {
        Objects.requireNonNull(sourceFiles, "sourceFiles");

        List<Path> files = sourceFiles.stream()
            .filter(Objects::nonNull)
            .toList();
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        if (files.size() < 2) {
            files.forEach(sourceFile -> builder.putFile(indexFile(sourceFile)));
            return builder.build();
        }

        int parallelism = Math.min(files.size(), Math.max(2,
            Math.min(Runtime.getRuntime().availableProcessors(), 8)));
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        List<Future<JavaProjectSemanticIndex.SourceFileIndex>> futures = new ArrayList<>(files.size());
        try {
            for (Path sourceFile : files) {
                futures.add(executor.submit(() -> indexFile(sourceFile)));
            }
            for (Future<JavaProjectSemanticIndex.SourceFileIndex> future : futures) {
                builder.putFile(future.get());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Java project indexing was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException)
                throw runtimeException;
            throw new IllegalStateException("Failed to index Java project", cause);
        } finally {
            executor.shutdownNow();
        }

        return builder.build();
    }

    public JavaProjectSemanticIndex.SourceFileIndex indexFile(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        return extractor.extract(sourceFile, readSource(sourceFile));
    }

    public JavaProjectSemanticIndex.SourceFileIndex indexFile(Path sourceFile, CharSequence sourceContent) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(sourceContent, "sourceContent");
        return extractor.extract(sourceFile, sourceContent);
    }

    private static String readSource(Path sourceFile) {
        try {
            return Files.readString(sourceFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read Java source file " + sourceFile, exception);
        }
    }
}
