package dev.railroadide.railroad.debug.source;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySources;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.utility.ProjectPathIdentityUtils;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds the attached sources of classpath jars for the classes a debugged program stops in, such as Minecraft or mod
 * loader classes.
 * <p>
 * The jar that contains a runtime class supplies its source through {@link JavaLibrarySources}, which writes it to the
 * same read-only file that library navigation opens. Breakpoints set in such a file therefore match the frames the
 * debugger reports. Jars are searched in classpath order. Their class entries are listed once, on the first lookup,
 * and the result for each class is cached. Instances are thread-safe.
 */
public final class LibrarySourceIndex implements DependencySourceIndex {
    private final List<Path> archives;
    private final JavaLibrarySources sources;
    private final Map<String, Optional<DebugSource>> resolved = new ConcurrentHashMap<>();
    private volatile @Nullable Map<String, List<Path>> archivesByPackage;

    /**
     * Creates an index over classpath jars. Entries that are not jar files are ignored.
     *
     * @param classpath classpath roots in lookup order
     * @param sources provider that materializes attached sources
     */
    public LibrarySourceIndex(List<Path> classpath, JavaLibrarySources sources) {
        this.archives = Objects.requireNonNull(classpath, "classpath").stream()
            .map(path -> path.toAbsolutePath().normalize())
            .filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".jar"))
            .distinct()
            .toList();
        this.sources = Objects.requireNonNull(sources, "sources");
    }

    /**
     * Creates an index over a launch's own classpath followed by the classpaths of the project's imported Gradle
     * modules. The module that matches {@code modulePath} or {@code moduleDirectory} comes first, because projects
     * targeting several loaders contain the same classes in different jars. A project without an imported Gradle
     * model contributes only the launch classpath.
     *
     * @param project project being debugged
     * @param launchClasspath classpath entries of the launch itself, searched first
     * @param modulePath Gradle path of the launched module, such as {@code :neoforge}, or {@code null}
     * @param moduleDirectory directory of the launched module, or {@code null}
     * @return index over the combined classpath, using the shared library source provider
     */
    public static LibrarySourceIndex forProject(
        Project project,
        List<Path> launchClasspath,
        @Nullable String modulePath,
        @Nullable Path moduleDirectory
    ) {
        List<Path> classpath = new ArrayList<>(launchClasspath);
        if (project.hasFacet(FacetManager.GRADLE)) {
            project.getGradleManager().getGradleModelService().getCachedModel().ifPresent(model -> {
                List<RailroadModule> modules = new ArrayList<>(model.project().getModules());
                modules.sort(Comparator.comparing(module -> !isModule(module, modulePath, moduleDirectory)));
                for (RailroadModule module : modules) {
                    module.getClasspathRoots().forEach(root -> classpath.add(root.toPath()));
                }
            });
        }
        return new LibrarySourceIndex(classpath, JavaLibrarySources.shared());
    }

    private static boolean isModule(RailroadModule module, @Nullable String path, @Nullable Path directory) {
        if (path != null)
            return path.equals(module.getPath());

        return directory != null && module.getProjectDir() != null
            && ProjectPathIdentityUtils.matches(module.getProjectDir(), directory);
    }

    @Override
    public Optional<DebugSource> resolve(String runtimeSourcePath, String declaringType) {
        Objects.requireNonNull(runtimeSourcePath, "runtimeSourcePath");
        Objects.requireNonNull(declaringType, "declaringType");
        String relative = runtimeSourcePath.replace(File.separatorChar, '/');
        String topLevel = topLevelName(declaringType);
        return resolved.computeIfAbsent(relative + '\n' + topLevel, _ -> find(relative, topLevel));
    }

    @Override
    public List<Path> sourceRoots() {
        return archives.stream().map(sources::viewRoot).toList();
    }

    private Optional<DebugSource> find(String relative, String topLevel) {
        String classEntry = topLevel.replace('.', '/') + ".class";
        int slash = classEntry.lastIndexOf('/');
        String packagePath = slash < 0 ? "" : classEntry.substring(0, slash);
        for (Path archive : archivesByPackage().getOrDefault(packagePath, List.of())) {
            if (!contains(archive, classEntry))
                continue;

            Optional<Path> file = sources.materializeAttachedSource(archive, relative);
            if (file.isPresent())
                return Optional.of(new DebugSource.FileSource(file.get()));
        }
        return Optional.empty();
    }

    private Map<String, List<Path>> archivesByPackage() {
        Map<String, List<Path>> index = archivesByPackage;
        if (index != null)
            return index;

        synchronized (this) {
            if (archivesByPackage == null) {
                archivesByPackage = buildPackageIndex(archives);
            }
            return archivesByPackage;
        }
    }

    private static Map<String, List<Path>> buildPackageIndex(List<Path> archives) {
        Map<String, List<Path>> index = new HashMap<>();
        for (Path archive : archives) {
            if (!Files.isRegularFile(archive))
                continue;

            Set<String> packages = new HashSet<>();
            try (var zip = new ZipFile(archive.toFile())) {
                for (var entries = zip.entries(); entries.hasMoreElements();) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/"))
                        continue;

                    int slash = name.lastIndexOf('/');
                    packages.add(slash < 0 ? "" : name.substring(0, slash));
                }
            } catch (IOException exception) {
                Railroad.LOGGER.debug("Failed to list classes in {}", archive, exception);
                continue;
            }
            for (String packagePath : packages) {
                index.computeIfAbsent(packagePath, _ -> new ArrayList<>()).add(archive);
            }
        }
        return Map.copyOf(index);
    }

    private static boolean contains(Path archive, String entry) {
        try (var zip = new ZipFile(archive.toFile())) {
            return zip.getEntry(entry) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private static String topLevelName(String declaringType) {
        int nested = declaringType.indexOf('$');
        return nested < 0 ? declaringType : declaringType.substring(0, nested);
    }
}
