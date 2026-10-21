package dev.railroadide.railroad.debug.source;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Maps runtime JDI locations to project files or indexed dependency sources.
 */
public final class SourceResolver {
    private final List<Path> sourceRoots;
    private final DependencySourceIndex dependencySources;

    /**
     * Creates a source mapper with normalized project roots and a dependency-source fallback.
     *
     * @param sourceRoots project source roots, normalized during construction
     * @param dependencySources fallback index for dependency source content
     */
    public SourceResolver(List<Path> sourceRoots, DependencySourceIndex dependencySources) {
        this.sourceRoots = sourceRoots.stream()
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .toList();
        this.dependencySources = dependencySources;
    }

    /**
     * Resolves runtime source metadata against project roots, then consults the dependency index.
     *
     * @param location runtime location to resolve or compare
     * @return the matching source, or empty when metadata or source content is unavailable
     */
    public Optional<DebugSource> resolve(Location location) {
        String runtimePath = getRuntimeSourcePath(location);
        if (runtimePath == null)
            return Optional.empty();

        String normalized = runtimePath.replace('/', File.separatorChar);
        for (Path root : sourceRoots) {
            Path candidate = root.resolve(normalized).normalize();
            if (candidate.startsWith(root) && Files.isRegularFile(candidate))
                return Optional.of(new DebugSource.FileSource(candidate));
        }

        return dependencySources.resolve(
            runtimePath,
            location.declaringType().name());
    }

    private String getRuntimeSourcePath(Location location) {
        try {
            return location.sourcePath();
        } catch (AbsentInformationException _) {
        }

        try {
            String sourceName = location.sourceName();
            String className = location.declaringType().name();
            int lastDot = className.lastIndexOf('.');
            if (lastDot == -1)
                return sourceName;

            String packageName = className.substring(0, lastDot)
                .replace('.', '/');
            return packageName + "/" + sourceName;
        } catch (AbsentInformationException _) {
            return null;
        }
    }

    /**
     * Resolves a runtime location and compares it with the expected source identity.
     *
     * @param expected source identity expected at the runtime location
     * @param location runtime location to resolve or compare
     * @return true when the resolved runtime source matches the expected source
     */
    public boolean matches(DebugSource expected, Location location) {
        Optional<DebugSource> actual = resolve(location);
        return actual.isPresent() && expected.matches(actual.get());
    }

    /**
     * Returns the basename used to filter class-prepare requests by source name.
     *
     * @param source source identity used by the breakpoint
     * @return the source name, or empty when unavailable
     */
    public Optional<String> sourceName(DebugSource source) {
        return Optional.ofNullable(source.name());
    }

    /**
     * Derives a runtime class-name filter using this resolver's project source roots.
     *
     * @param source source identity used by the breakpoint
     * @return the source class pattern, or empty when unsupported or outside the source roots
     */
    public Optional<String> classPatternFor(DebugSource source) {
        return source.classPattern(sourceRoots);
    }
}
