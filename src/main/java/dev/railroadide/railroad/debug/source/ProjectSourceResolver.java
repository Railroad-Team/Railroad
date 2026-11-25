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
     * Finds conventional {@code src/<source-set>/java} roots in the project and its subprojects, without dependency
     * sources.
     *
     * @param projectRoot directory containing the project
     * @return resolver using the discovered roots in deterministic order
     * @throws IOException if project directories cannot be read
     */
    public static SourceResolver create(Path projectRoot) throws IOException {
        return create(projectRoot, DependencySourceIndex.EMPTY);
    }

    /**
     * Finds conventional {@code src/<source-set>/java} roots in the project and its subprojects and falls back to a
     * dependency index for classes outside them.
     *
     * @param projectRoot directory containing the project
     * @param dependencies index consulted for classes not found in the project's sources
     * @return resolver using the discovered roots in deterministic order
     * @throws IOException if project directories cannot be read
     */
    public static SourceResolver create(Path projectRoot, DependencySourceIndex dependencies) throws IOException {
        return create(projectRoot, null, "", dependencies);
    }

    /**
     * Prioritizes the launched class's package root, including nonstandard Gradle source directories, and falls
     * back to a dependency index for classes outside the project's sources.
     *
     * @param projectRoot directory containing the project
     * @param mainSource source file of the launched class, or {@code null}
     * @param mainClass qualified name of the launched class, or an empty string
     * @param dependencies index consulted for classes not found in the project's sources
     * @return resolver using the discovered roots, the launched class's root first
     * @throws IOException if project directories cannot be read
     */
    public static SourceResolver create(
        Path projectRoot,
        Path mainSource,
        String mainClass,
        DependencySourceIndex dependencies
    ) throws IOException {
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
        if (mainSource != null) {
            Path preferred = mainSource.toAbsolutePath().normalize().getParent();
            int separator = mainClass.lastIndexOf('.');
            if (separator >= 0) {
                Path packagePath = Path.of(mainClass.substring(0, separator).replace('.', '/'));
                if (preferred.endsWith(packagePath)) {
                    for (int i = 0; i < packagePath.getNameCount(); i++) {
                        preferred = preferred.getParent();
                    }
                } else {
                    preferred = null;
                }
            }
            if (preferred != null) {
                sourceRoots.remove(preferred);
                sourceRoots.addFirst(preferred);
            }
        }
        return new SourceResolver(sourceRoots, dependencies);
    }
}
