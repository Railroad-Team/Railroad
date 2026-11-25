package dev.railroadide.railroad.ide.language.impl.index;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import java.nio.file.Path;
import java.util.*;

/**
 * The Java compile inputs of each source set in one imported build.
 */
public final class JavaSourceSets {
    private static final LoadingCache<WorkspaceConfigurationSnapshot, JavaSourceSets> CACHE = CacheBuilder.newBuilder()
        .weakKeys().maximumSize(16).build(CacheLoader.from(JavaSourceSets::new));

    /**
     * The compile inputs of one source set.
     *
     * @param module identifier of the owning module
     * @param name source set name, such as {@code main} or {@code test}
     * @param visibleRoots source roots whose declarations the source set can see, including its own
     * @param classpath binary compile classpath in configured order
     * @param jdkHome JDK used to compile the source set, or {@code null} when unknown
     */
    public record Scope(
        String module,
        String name,
        Set<Path> visibleRoots,
        List<Path> classpath,
        Path jdkHome
    ) {
    }

    private final Map<Path, Scope> owners = new LinkedHashMap<>();
    private final Path importRoot;
    private final Path openRoot;
    private final List<Scope> scopes;
    private final Set<Path> outputs;

    private JavaSourceSets(WorkspaceConfigurationSnapshot configuration) {
        Map<String, String> settings = configuration.settings();
        openRoot = settings.containsKey("gradle.openRoot") ? Path.of(settings.get("gradle.openRoot")) : null;
        importRoot = settings.containsKey("gradle.importRoot") ? Path.of(settings.get("gradle.importRoot")) : null;
        record Input(String module, String name, String prefix, List<Path> roots) {
        }

        List<Input> inputs = new ArrayList<>();
        Map<Path, List<Path>> outputRoots = new HashMap<>();
        for (String module : configuration.moduleIds().stream().sorted().toList()) {
            String prefix = "module." + module + ".";
            for (String name : settings.getOrDefault(prefix + "sourceSets", "").lines().sorted().toList()) {
                String set = prefix + "set." + name + ".";
                List<Path> roots = paths(settings, set + "sources");
                inputs.add(new Input(module, name, set, roots));
                paths(settings, set + "outputs").forEach(output -> outputRoots.put(output, roots));
            }
        }

        outputs = Set.copyOf(outputRoots.keySet());
        List<Scope> selected = new ArrayList<>();
        for (Input input : inputs) {
            Set<Path> visible = new LinkedHashSet<>(input.roots());
            List<Path> classpath = new ArrayList<>();
            for (Path entry : paths(settings, input.prefix() + "classpath")) {
                List<Path> producer = outputRoots.get(entry);
                if (producer == null) {
                    classpath.add(entry);
                } else {
                    visible.addAll(producer);
                }
            }

            String jdk = settings.getOrDefault(input.prefix() + "jdkHome",
                settings.get("module." + input.module() + ".jdkHome"));
            var scope = new Scope(input.module(), input.name(), Set.copyOf(visible),
                List.copyOf(classpath), jdk == null ? null : Path.of(jdk));
            input.roots().forEach(path -> owners.putIfAbsent(path, scope));
            selected.add(scope);
        }

        scopes = List.copyOf(selected);
    }

    /**
     * Returns the source sets of an imported build, cached per configuration.
     *
     * @param configuration captured workspace configuration
     * @return source sets described by the configuration
     */
    public static JavaSourceSets of(WorkspaceConfigurationSnapshot configuration) {
        return CACHE.getUnchecked(configuration);
    }

    private Path importedPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return openRoot != null && importRoot != null && normalized.startsWith(openRoot)
            ? importRoot.resolve(openRoot.relativize(normalized))
            : normalized;
    }

    /**
     * Reports whether the configuration describes no source sets, such as before a build is imported.
     *
     * @return whether there are no source sets
     */
    public boolean isEmpty() {
        return scopes.isEmpty();
    }

    /**
     * Finds the source set whose source roots contain a file.
     *
     * @param path file or directory to look up
     * @return owning source set, or {@code null} when no source set owns the path
     */
    public Scope owner(Path path) {
        return owners.get(owningRoot(path));
    }

    private Path owningRoot(Path path) {
        if (path == null)
            return null;

        for (Path current = importedPath(path); current != null; current = current.getParent()) {
            if (owners.containsKey(current))
                return current;
        }

        return null;
    }

    /**
     * Finds the source set of a module that compiles against an archive, preferring {@code main}.
     *
     * @param module identifier of the importing module
     * @param archive binary on the classpath
     * @return importing source set, or {@code null} when none of the module's source sets uses the archive
     */
    public Scope importing(String module, Path archive) {
        if (archive == null || module == null)
            return null;

        return scopes.stream().filter(scope -> scope.module().equals(module) && scope.classpath().contains(archive))
            .min(Comparator.comparing(scope -> !scope.name().equals("main"))).orElse(null);
    }

    /**
     * Reports whether a source set can see the declarations of a file.
     *
     * @param scope source set resolving references
     * @param path source file declaring the referenced symbol
     * @return whether the file lies in one of the source set's visible roots
     */
    public boolean visible(Scope scope, Path path) {
        Path root = owningRoot(path);
        return root != null && scope.visibleRoots().contains(root);
    }

    /**
     * Returns the compiled output directories of every source set.
     *
     * @return normalized output directories
     */
    public Set<Path> outputs() {
        return outputs;
    }

    private static List<Path> paths(Map<String, String> settings, String key) {
        return settings.getOrDefault(key, "").lines().filter(line -> !line.isBlank()).map(Path::of)
            .map(path -> path.toAbsolutePath().normalize()).distinct().toList();
    }
}
