package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Request-owned captured Java sources for JDT, including failure and cancellation cleanup. */
public final class JavaSignatureSources implements AutoCloseable {
    private final Path directory;

    private JavaSignatureSources(Path directory) {
        this.directory = directory;
    }

    /**
     * Materializes unambiguous captured source units. Conflicting package/filename pairs are
     * omitted rather than allowing iteration order to choose a different module's declaration.
     *
     * @param analysis immutable source inputs for the request
     * @param parent optional temporary parent, or null for the system temporary directory
     * @return source tree whose owner must close it after parsing
     * @throws IOException if materialization fails
     * @throws CancellationException if the worker has been interrupted
     */
    public static JavaSignatureSources create(JavaWorkspaceAnalysis analysis, Path parent) throws IOException {
        return create(analysis, parent, Set.of());
    }

    /**
     * Stages captured dependencies while the requesting compilation unit is supplied directly to JDT.
     *
     * @param analysis captured source inputs
     * @param parent optional temporary parent
     * @param excluded declaration paths belonging to the requesting compilation unit
     * @return owned temporary source tree
     * @throws IOException if materialization fails
     */
    public static JavaSignatureSources create(JavaWorkspaceAnalysis analysis, Path parent, Set<Path> excluded)
        throws IOException {
        return create(analysis, parent, excluded, JavaSignatureSources::checkCancelled);
    }

    /**
     * Stages captured sources with request checks before each filesystem operation and publication.
     *
     * @param analysis captured sources
     * @param parent optional temporary parent
     * @param excluded paths supplied directly to the parser
     * @param activityCheck request cancellation and deadline check
     * @return owned source tree
     * @throws IOException if staging fails
     */
    public static JavaSignatureSources create(
        JavaWorkspaceAnalysis analysis,
        Path parent,
        Set<Path> excluded,
        Runnable activityCheck
    ) throws IOException {
        Objects.requireNonNull(activityCheck, "activityCheck").run();
        Path directory = parent == null
            ? Files.createTempDirectory("railroad-java-signatures-")
            : Files.createTempDirectory(parent, "railroad-java-signatures-");
        var sources = new JavaSignatureSources(directory);
        try {
            Map<Path, String> units = new HashMap<>();
            Set<Path> ambiguous = new HashSet<>();
            for (JavaProjectSemanticIndex.SourceFileIndex entry : analysis.sourceIndex().files().values()) {
                activityCheck.run();
                if (excluded.contains(entry.path()))
                    continue;
                String packageName = entry.packageName();
                Path folder = packageName == null ? directory : directory.resolve(packageName.replace('.', '/'));
                Path target = folder.resolve(entry.path().getFileName()).normalize();
                if (!target.startsWith(directory))
                    throw new IOException("Source location escapes request directory");
                String text = Objects.requireNonNull(analysis.sourceTexts().get(entry.path()),
                    "Missing captured source text");
                String previous = units.putIfAbsent(target, text);
                if (previous != null && !previous.equals(text)) {
                    ambiguous.add(target);
                }
            }
            for (Map.Entry<Path, String> entry : units.entrySet()) {
                activityCheck.run();
                if (ambiguous.contains(entry.getKey()))
                    continue;
                Files.createDirectories(entry.getKey().getParent());
                Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
            }
            activityCheck.run();
            return sources;
        } catch (IOException | RuntimeException exception) {
            sources.close();
            throw exception;
        }
    }

    /**
     * Returns the captured source root passed to JDT.
     *
     * @return temporary source directory
     */
    public Path directory() {
        return directory;
    }

    /** Throws when an interrupted request should stop staging or parsing. */
    public static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("Signature request cancelled");
    }

    @Override
    public void close() {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            Railroad.LOGGER.warn("Cannot remove signature request directory {}", directory, exception);
        }
    }
}
