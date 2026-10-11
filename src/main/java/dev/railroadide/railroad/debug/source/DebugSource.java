package dev.railroadide.railroad.debug.source;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Identifies debugger source content in a file or archive and supplies breakpoint matching hints.
 */
public sealed interface DebugSource {
    /**
     * Compares this source identity with another source according to its file or archive representation.
     *
     * @param other source identity to compare with this source
     * @return true when both identities refer to the same source content
     */
    boolean matches(DebugSource other);
    /**
     * Returns the source filename used for runtime source-name filtering.
     *
     * @return the file or archive-entry basename
     */
    String name();
    /**
     * Derives a runtime class-name pattern from the configured roots when this source supports that mapping.
     *
     * @param paths source roots used to derive a runtime class-name filter
     * @return a class filter including nested-class matches, or empty when no pattern can be derived
     */
    Optional<String> classPattern(List<Path> paths);

    /**
     * Identifies a source file using its normalized absolute path for matching.
     *
     * @param file path to the source file
     */
    record FileSource(Path file) implements DebugSource {
        @Override
        public boolean matches(DebugSource other) {
            return other instanceof FileSource(Path file1) &&
                Objects.equals(file.toAbsolutePath().normalize(), file1.toAbsolutePath().normalize());
        }

        @Override
        public String name() {
            return file.getFileName().toString();
        }

        @Override
        public Optional<String> classPattern(List<Path> paths) {
            Path absolute = file.toAbsolutePath().normalize();
            for (Path root : paths) {
                if (!absolute.startsWith(root))
                    continue;

                Path relative = root.relativize(absolute);
                String className = relative.toString().replace(File.separatorChar, '.');
                if (!className.endsWith(".java"))
                    continue;

                className = className.substring(0, className.length() - ".java".length());

                return Optional.of(className + "*");
            }

            return Optional.empty();
        }
    }

    /**
     * Identifies a source entry within an archive.
     *
     * @param archive path to the source archive
     * @param entry source entry path within the archive
     */
    record ArchiveSource(Path archive, String entry) implements DebugSource {
        @Override
        public boolean matches(DebugSource other) {
            return other instanceof ArchiveSource(Path archive1, String entry1) &&
                Objects.equals(archive, archive1) && Objects.equals(entry, entry1);
        }

        @Override
        public String name() {
            int slash = entry.lastIndexOf('/');
            return slash == -1 ? entry : entry.substring(slash + 1);
        }

        @Override
        public Optional<String> classPattern(List<Path> paths) {
            return Optional.empty(); // TODO: Integrate gradle/maven dependency roots
        }
    }
}
