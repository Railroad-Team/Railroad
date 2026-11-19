package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Builds a project-wide semantic index by extracting declaration facts from
 * Java source files.
 */
public class JavaProjectSemanticIndexer {
    private static final int MAXIMUM_HELPERS = Math.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, 7);
    private static final Semaphore HELPER_PERMITS = new Semaphore(MAXIMUM_HELPERS);

    private final JavaProjectSemanticExtractor extractor;

    /**
     * Creates a source indexer using the default Java declaration-fact extractor.
     */
    public JavaProjectSemanticIndexer() {
        this(new JavaProjectSemanticExtractor());
    }

    /**
     * Creates a source indexer using a declaration-fact extractor.
     *
     * @param extractor the extractor used for each source file
     */
    public JavaProjectSemanticIndexer(JavaProjectSemanticExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
    }

    /**
     * Builds a semantic index from Java source files with globally bounded parallel extraction.
     *
     * @param projectRoot the directory recursively searched for regular {@code .java} files
     * @return the immutable project source index
     */
    public JavaProjectSemanticIndex build(Path projectRoot) {
        return build(projectRoot, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Builds a source index with checks during discovery and file extraction.
     *
     * @param projectRoot directory containing the source files
     * @param activityCheck cooperative request activity check
     * @return complete source index
     */
    public JavaProjectSemanticIndex build(Path projectRoot, Runnable activityCheck) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(activityCheck, "activityCheck").run();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> javaFiles = paths
                .peek(_ -> activityCheck.run())
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .toList();
            return build(javaFiles, activityCheck);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to build semantic index for " + projectRoot, exception);
        }
    }

    /**
     * Builds a semantic index from Java source files with globally bounded parallel extraction.
     *
     * @param sourceFiles the source files to index; null entries are ignored
     * @return the immutable project source index
     */
    public JavaProjectSemanticIndex build(List<Path> sourceFiles) {
        return build(sourceFiles, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Indexes a captured source list, discarding the builder if cancellation or expiry is observed.
     * The calling worker invokes the supplied callback exclusively. Additional extraction workers
     * observe cancellation through the calling worker and interruption; each owns its checking budget.
     * Helpers are globally bounded and never wait for admission or reuse the request scheduler.
     *
     * @param sourceFiles files to index; null entries are ignored
     * @param activityCheck cooperative request activity check
     * @return complete immutable source index
     */
    public JavaProjectSemanticIndex build(List<Path> sourceFiles, Runnable activityCheck) {
        Objects.requireNonNull(sourceFiles, "sourceFiles");
        Objects.requireNonNull(activityCheck, "activityCheck").run();
        List<Path> files = new ArrayList<>(sourceFiles.size());
        for (Path sourceFile : sourceFiles) {
            activityCheck.run();
            if (sourceFile != null) {
                files.add(sourceFile);
            }
        }
        var indexed = new JavaProjectSemanticIndex.SourceFileIndex[files.size()];
        var next = new AtomicInteger();
        var stopped = new AtomicBoolean();
        var failure = new AtomicReference<Throwable>();
        Runnable helperCheck = () -> {
            ActivityCheckpoint.checkInterrupted();
            if (stopped.get())
                throw new CancellationException("Java source indexing stopped");
        };
        Runnable ownerCheck = () -> {
            activityCheck.run();
            ActivityCheckpoint.checkInterrupted();
            rethrowFailure(failure.get());
        };
        List<Thread> helpers = new ArrayList<>(MAXIMUM_HELPERS);
        try {
            int helperCount = Math.min(MAXIMUM_HELPERS, files.size() - 1);
            for (int helperIndex = 0; helperIndex < helperCount && HELPER_PERMITS.tryAcquire(); helperIndex++) {
                try {
                    Thread helper = Thread.ofVirtual().name("railroad-java-source-index").unstarted(() -> {
                        try {
                            extractFiles(files, indexed, next, helperCheck);
                        } catch (Throwable exception) {
                            failure.compareAndSet(null, exception);
                            stopped.set(true);
                        } finally {
                            HELPER_PERMITS.release();
                        }
                    });
                    helpers.add(helper);
                    helper.start();
                } catch (RuntimeException | Error exception) {
                    HELPER_PERMITS.release();
                    throw exception;
                }
            }
            extractFiles(files, indexed, next, ownerCheck);
            for (Thread helper : helpers) {
                while (helper.isAlive()) {
                    ownerCheck.run();
                    helper.join(10);
                }
            }
            ownerCheck.run();
            JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
            for (JavaProjectSemanticIndex.SourceFileIndex file : indexed) {
                ownerCheck.run();
                builder.putFile(file);
            }
            JavaProjectSemanticIndex result = builder.build();
            ownerCheck.run();
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Java source indexing was interrupted");
        } finally {
            stopped.set(true);
            helpers.forEach(Thread::interrupt);
        }
    }

    private void extractFiles(
        List<Path> files,
        JavaProjectSemanticIndex.SourceFileIndex[] indexed,
        AtomicInteger next,
        Runnable activityCheck
    ) {
        while (true) {
            activityCheck.run();
            int index = next.getAndIncrement();
            if (index >= files.size())
                return;
            indexed[index] = indexFile(files.get(index), activityCheck);
            activityCheck.run();
        }
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtime)
            throw runtime;
        if (failure instanceof Error error)
            throw error;
        if (failure != null)
            throw new IllegalStateException("Failed to index Java project", failure);
    }

    /**
     * Extracts a semantic index entry for one Java source file.
     *
     * @param sourceFile the source file path
     * @return the file's package, imports, and indexed declarations
     * @throws UncheckedIOException if the source file cannot be read
     */
    public JavaProjectSemanticIndex.SourceFileIndex indexFile(Path sourceFile) {
        return indexFile(sourceFile, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Reads and indexes a file, checking the request before and after filesystem access.
     *
     * @param sourceFile source file path
     * @param activityCheck cooperative request activity check
     * @return complete immutable source entry
     */
    public JavaProjectSemanticIndex.SourceFileIndex indexFile(Path sourceFile, Runnable activityCheck) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        activityCheck.run();
        String source = readSource(sourceFile);
        activityCheck.run();
        return indexFile(sourceFile, source, activityCheck);
    }

    /**
     * Extracts a semantic index entry for one Java source file.
     *
     * @param sourceFile the source file path
     * @param sourceContent the supplied Java text to index instead of reading the file
     * @return the file's package, imports, and indexed declarations
     */
    public JavaProjectSemanticIndex.SourceFileIndex indexFile(Path sourceFile, CharSequence sourceContent) {
        return indexFile(sourceFile, sourceContent, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Indexes captured source text with cooperative request controls.
     *
     * @param sourceFile source file identity
     * @param sourceContent captured text
     * @param activityCheck cooperative request activity check
     * @return complete immutable source entry
     */
    public JavaProjectSemanticIndex.SourceFileIndex indexFile(
        Path sourceFile,
        CharSequence sourceContent,
        Runnable activityCheck
    ) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(sourceContent, "sourceContent");
        return extractor.extract(sourceFile, sourceContent, activityCheck);
    }

    private static String readSource(Path sourceFile) {
        try {
            return Files.readString(sourceFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read Java source file " + sourceFile, exception);
        }
    }
}
