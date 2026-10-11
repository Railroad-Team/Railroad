package dev.railroadide.railroad.debug.source;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Discovers project Java sources for both direct and Gradle debug launches.
 */
public final class ProjectSourceResolver {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(".git", ".gradle", "build", "out", "target",
        "node_modules");

    private ProjectSourceResolver() {
    }

    /**
     * Finds conventional {@code src/<source-set>/java} roots in the project and its subprojects.
     * Dependency sources are intentionally left to a separate source attachment feature.
     *
     * @param projectRoot directory containing the project
     * @return resolver using the discovered roots in deterministic order
     * @throws IOException if project directories cannot be read
     */
    public static SourceResolver create(Path projectRoot) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        List<Path> sourceRoots = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public @NonNull FileVisitResult preVisitDirectory(
                @NonNull Path directory,
                @NonNull BasicFileAttributes attributes
            ) {
                if (!directory.equals(root) && EXCLUDED_DIRECTORIES.contains(directory.getFileName().toString()))
                    return FileVisitResult.SKIP_SUBTREE;

                Path sourceSet = directory.getParent();
                if (directory.endsWith("java") && sourceSet != null && sourceSet.getParent() != null
                    && sourceSet.getParent().endsWith("src")) {
                    sourceRoots.add(directory);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }
        });
        sourceRoots.sort(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString));
        return new SourceResolver(sourceRoots, DependencySourceIndex.EMPTY);
    }
}
