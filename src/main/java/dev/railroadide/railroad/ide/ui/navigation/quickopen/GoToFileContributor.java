package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import javafx.scene.Node;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lists the files of the project, skipping hidden directories and build outputs.
 */
public final class GoToFileContributor implements GoToContributor<GoToFileContributor.FileEntry> {
    private static final Set<String> BUILD_OUTPUTS = Set.of("build", "out", "target", "bin", "node_modules");

    @Override
    public String titleKey() {
        return "railroad.ide.go_to.file.title";
    }

    @Override
    public List<FileEntry> collect(Project project, Runnable activityCheck) throws IOException {
        return scan(project.getPath(), activityCheck);
    }

    /**
     * Lists the regular files below a directory. Directories whose name starts with a dot are skipped, as are build
     * output directories such as {@code build} unless they lie inside a {@code src} directory.
     *
     * @param root directory to scan
     * @param activityCheck cancellation check, run for every visited directory
     * @return files in visiting order
     * @throws IOException when the root cannot be read
     */
    public static List<FileEntry> scan(Path root, Runnable activityCheck) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<FileEntry> files = new ArrayList<>();
        Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                activityCheck.run();
                if (directory.equals(normalizedRoot))
                    return FileVisitResult.CONTINUE;
                String name = directory.getFileName().toString();
                if (name.startsWith(".") || BUILD_OUTPUTS.contains(name) && !insideSources(directory))
                    return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile()) {
                    files.add(new FileEntry(file, normalizedRoot.relativize(file).toString().replace('\\', '/')));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) {
                return FileVisitResult.CONTINUE;
            }

            private boolean insideSources(Path directory) {
                for (Path segment : normalizedRoot.relativize(directory.getParent())) {
                    if (segment.toString().equals("src"))
                        return true;
                }
                return false;
            }
        });
        return files;
    }

    @Override
    public String name(FileEntry item) {
        return item.path().getFileName().toString();
    }

    @Override
    public String path(FileEntry item) {
        return item.relativePath();
    }

    @Override
    public String detail(FileEntry item) {
        int separator = item.relativePath().lastIndexOf('/');
        return separator < 0 ? "" : item.relativePath().substring(0, separator);
    }

    @Override
    public char separator() {
        return '/';
    }

    @Override
    public String normalizePattern(String pattern) {
        return pattern.replace('\\', '/');
    }

    @Override
    public Node icon(FileEntry item) {
        return FileUtils.getIcon(item.path());
    }

    @Override
    public void open(FileEntry item, GoToSearch.Query query) {
        GoToContributor.openInEditor(item.path(), query.hasLine() ? query::offsetIn : null);
    }

    /**
     * Project file.
     *
     * @param path absolute file path
     * @param relativePath path relative to the project root, separated by {@code /}
     */
    public record FileEntry(Path path, String relativePath) {
    }
}
