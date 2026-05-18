package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory lifecycle service for project semantic indexes.
 * <p>
 * The service owns one cached {@link JavaProjectSemanticIndex} per project root and
 * supports full rebuilds plus single-file updates/removals.
 */
public final class ProjectSemanticService {
    private final JavaProjectSemanticIndexer indexer;
    private final ProjectSemanticIndexPersistence persistence;
    private final Map<Path, JavaProjectSemanticIndex> indexesByProjectRoot = new ConcurrentHashMap<>();

    public ProjectSemanticService() {
        this(new JavaProjectSemanticIndexer(), new ProjectSemanticIndexPersistence());
    }

    public ProjectSemanticService(JavaProjectSemanticIndexer indexer) {
        this(indexer, new ProjectSemanticIndexPersistence());
    }

    public ProjectSemanticService(JavaProjectSemanticIndexer indexer, ProjectSemanticIndexPersistence persistence) {
        this.indexer = Objects.requireNonNull(indexer, "indexer");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    public JavaProjectSemanticIndex index(Project project) {
        Objects.requireNonNull(project, "project");
        return index(project.getPath());
    }

    public @Nullable JavaProjectSemanticIndex current(Project project) {
        Objects.requireNonNull(project, "project");
        return current(project.getPath());
    }

    public JavaProjectSemanticIndex index(Path projectRoot) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        return indexesByProjectRoot.computeIfAbsent(normalizedRoot, root -> {
            JavaProjectSemanticIndex persisted = persistence.loadIfCurrent(root);
            if (persisted != null)
                return persisted;

            JavaProjectSemanticIndex rebuilt = indexer.build(root);
            persistence.save(root, rebuilt);
            return rebuilt;
        });
    }

    public @Nullable JavaProjectSemanticIndex current(Path projectRoot) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        JavaProjectSemanticIndex current = indexesByProjectRoot.get(normalizedRoot);
        if (current != null)
            return current;

        JavaProjectSemanticIndex persisted = persistence.loadIfCurrent(normalizedRoot);
        if (persisted != null) {
            indexesByProjectRoot.put(normalizedRoot, persisted);
            return persisted;
        }

        return null;
    }

    public JavaProjectSemanticIndex rebuild(Project project) {
        Objects.requireNonNull(project, "project");
        return rebuild(project.getPath());
    }

    public JavaProjectSemanticIndex rebuild(Path projectRoot) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        JavaProjectSemanticIndex rebuilt = indexer.build(normalizedRoot);
        indexesByProjectRoot.put(normalizedRoot, rebuilt);
        persistence.save(normalizedRoot, rebuilt);
        return rebuilt;
    }

    public JavaProjectSemanticIndex.SourceFileIndex updateFile(Project project, Path file) {
        Objects.requireNonNull(project, "project");
        return updateFile(project.getPath(), file);
    }

    public JavaProjectSemanticIndex.SourceFileIndex updateFile(Path projectRoot, Path file) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        Path normalizedFile = normalizeFile(file);
        JavaProjectSemanticIndex.SourceFileIndex indexedFile = indexer.indexFile(normalizedFile);

        JavaProjectSemanticIndex current = index(normalizedRoot);
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        current.files().forEach((path, sourceFileIndex) -> {
            if (!path.equals(normalizedFile))
                builder.putFile(sourceFileIndex);
        });
        builder.putFile(indexedFile);
        JavaProjectSemanticIndex updated = builder.build();
        indexesByProjectRoot.put(normalizedRoot, updated);
        persistence.save(normalizedRoot, updated);
        return indexedFile;
    }

    public void removeFile(Project project, Path file) {
        Objects.requireNonNull(project, "project");
        removeFile(project.getPath(), file);
    }

    public void removeFile(Path projectRoot, Path file) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        JavaProjectSemanticIndex current = indexesByProjectRoot.get(normalizedRoot);
        if (current == null)
            return;

        Path normalizedFile = normalizeFile(file);
        JavaProjectSemanticIndex.Builder builder = JavaProjectSemanticIndex.builder();
        current.files().forEach((path, sourceFileIndex) -> {
            if (!path.equals(normalizedFile))
                builder.putFile(sourceFileIndex);
        });
        JavaProjectSemanticIndex updated = builder.build();
        indexesByProjectRoot.put(normalizedRoot, updated);
        persistence.save(normalizedRoot, updated);
    }

    public void invalidate(Project project) {
        Objects.requireNonNull(project, "project");
        invalidate(project.getPath());
    }

    public void invalidate(Path projectRoot) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        indexesByProjectRoot.remove(normalizedRoot);
    }

    public boolean hasIndex(Project project) {
        Objects.requireNonNull(project, "project");
        return hasIndex(project.getPath());
    }

    public boolean hasIndex(Path projectRoot) {
        return indexesByProjectRoot.containsKey(normalizeRoot(projectRoot));
    }

    private static Path normalizeRoot(Path projectRoot) {
        return FileUtils.normalizePath(Objects.requireNonNull(projectRoot, "projectRoot"));
    }

    private static Path normalizeFile(Path file) {
        return FileUtils.normalizePath(Objects.requireNonNull(file, "file"));
    }
}
