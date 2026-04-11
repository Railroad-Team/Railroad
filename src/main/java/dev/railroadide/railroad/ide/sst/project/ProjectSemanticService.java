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
 * The service owns one cached {@link ProjectSemanticIndex} per project root and
 * supports full rebuilds plus single-file updates/removals.
 */
public final class ProjectSemanticService {
    private final JavaProjectSemanticIndexer indexer;
    private final ProjectSemanticIndexPersistence persistence;
    private final Map<Path, ProjectSemanticIndex> indexesByProjectRoot = new ConcurrentHashMap<>();

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

    public ProjectSemanticIndex index(Project project) {
        Objects.requireNonNull(project, "project");
        return index(project.getPath());
    }

    public @Nullable ProjectSemanticIndex current(Project project) {
        Objects.requireNonNull(project, "project");
        return current(project.getPath());
    }

    public ProjectSemanticIndex index(Path projectRoot) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        return indexesByProjectRoot.computeIfAbsent(normalizedRoot, root -> {
            ProjectSemanticIndex persisted = persistence.loadIfCurrent(root);
            if (persisted != null)
                return persisted;

            ProjectSemanticIndex rebuilt = indexer.build(root);
            persistence.save(root, rebuilt);
            return rebuilt;
        });
    }

    public @Nullable ProjectSemanticIndex current(Path projectRoot) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        ProjectSemanticIndex current = indexesByProjectRoot.get(normalizedRoot);
        if (current != null)
            return current;

        ProjectSemanticIndex persisted = persistence.loadIfCurrent(normalizedRoot);
        if (persisted != null) {
            indexesByProjectRoot.put(normalizedRoot, persisted);
            return persisted;
        }

        return null;
    }

    public ProjectSemanticIndex rebuild(Project project) {
        Objects.requireNonNull(project, "project");
        return rebuild(project.getPath());
    }

    public ProjectSemanticIndex rebuild(Path projectRoot) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        ProjectSemanticIndex rebuilt = indexer.build(normalizedRoot);
        indexesByProjectRoot.put(normalizedRoot, rebuilt);
        persistence.save(normalizedRoot, rebuilt);
        return rebuilt;
    }

    public ProjectSemanticIndex.SourceFileIndex updateFile(Project project, Path file) {
        Objects.requireNonNull(project, "project");
        return updateFile(project.getPath(), file);
    }

    public ProjectSemanticIndex.SourceFileIndex updateFile(Path projectRoot, Path file) {
        Path normalizedRoot = normalizeRoot(projectRoot);
        Path normalizedFile = normalizeFile(file);
        ProjectSemanticIndex.SourceFileIndex indexedFile = indexer.indexFile(normalizedFile);

        ProjectSemanticIndex current = index(normalizedRoot);
        ProjectSemanticIndex.Builder builder = ProjectSemanticIndex.builder();
        current.files().forEach((path, sourceFileIndex) -> {
            if (!path.equals(normalizedFile))
                builder.putFile(sourceFileIndex);
        });
        builder.putFile(indexedFile);
        ProjectSemanticIndex updated = builder.build();
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
        ProjectSemanticIndex current = indexesByProjectRoot.get(normalizedRoot);
        if (current == null)
            return;

        Path normalizedFile = normalizeFile(file);
        ProjectSemanticIndex.Builder builder = ProjectSemanticIndex.builder();
        current.files().forEach((path, sourceFileIndex) -> {
            if (!path.equals(normalizedFile))
                builder.putFile(sourceFileIndex);
        });
        ProjectSemanticIndex updated = builder.build();
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
