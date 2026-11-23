package dev.railroadide.railroad.ide.projectexplorer;

import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroadplugin.dto.RailroadContentRoot;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import dev.railroadide.railroadplugin.dto.RailroadSourceDirectory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable lookup of the build roles of project directories, used to decorate project explorer rows.
 */
public final class ProjectStructure {
    /**
     * Structure that assigns no role to any directory.
     */
    public static final ProjectStructure EMPTY = new Builder().build();

    private final Map<Path, Role> roles;

    private ProjectStructure(Map<Path, Role> roles) {
        this.roles = Map.copyOf(roles);
    }

    /**
     * Build role of a project directory.
     */
    public enum Role {
        MODULE,
        SOURCE_SET,
        SOURCES,
        TEST_SOURCES,
        RESOURCES,
        TEST_RESOURCES,
        GENERATED_SOURCES,
        EXCLUDED
    }

    /**
     * Finds the role of a directory.
     *
     * @param path path to look up, or {@code null}
     * @return role of the directory, or {@code null} if it has none
     */
    public Role roleOf(Path path) {
        return path == null ? null : roles.get(normalize(path));
    }

    /**
     * Describes the modules and source directories of an imported Gradle build.
     *
     * @param model imported Gradle build
     * @return structure of every module in the build
     */
    public static ProjectStructure fromGradle(GradleBuildModel model) {
        var builder = new Builder();
        for (RailroadModule module : model.project().getModules()) {
            if (module.getProjectDir() != null) {
                builder.gradleModule(module.getProjectDir(), module.getContentRoots());
            }
        }
        return builder.build();
    }

    /**
     * Describes the conventional {@code src/main} and {@code src/test} layout of a project without a build model.
     *
     * @param projectRoot root directory of the project
     * @return structure treating the project root as the only module
     */
    public static ProjectStructure conventional(Path projectRoot) {
        Path src = projectRoot.resolve("src");
        return new Builder()
            .module(projectRoot)
            .sourceDirectory(projectRoot, src.resolve("main/java"), Role.SOURCES)
            .sourceDirectory(projectRoot, src.resolve("main/resources"), Role.RESOURCES)
            .sourceDirectory(projectRoot, src.resolve("test/java"), Role.TEST_SOURCES)
            .sourceDirectory(projectRoot, src.resolve("test/resources"), Role.TEST_RESOURCES)
            .build();
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * Collects directory roles; modules take precedence over source directories, which take precedence over the
     * source sets inferred from them.
     */
    public static final class Builder {
        private final List<Path> modules = new ArrayList<>();
        private final Map<Path, Role> directories = new HashMap<>();
        private final Map<Path, Role> sourceSets = new HashMap<>();

        /**
         * Marks a directory as a module root.
         *
         * @param directory module root directory
         * @return this builder
         */
        public Builder module(Path directory) {
            modules.add(normalize(directory));
            return this;
        }

        /**
         * Adds a Gradle module and its source sets. The imported model lists resource roots among its source roots and
         * does not say which source sets hold tests, so resource roots take precedence and test roots are recognised by
         * their source set's directory name. The module's {@code build} and {@code .gradle} directories are excluded
         * even when the module has no Java source sets.
         *
         * @param moduleDir module root directory
         * @param contentRoots source sets of the module
         * @return this builder
         */
        public Builder gradleModule(Path moduleDir, Iterable<? extends RailroadContentRoot> contentRoots) {
            module(moduleDir);
            for (RailroadContentRoot contentRoot : contentRoots) {
                Set<Path> resources = new HashSet<>();
                for (RailroadSourceDirectory directory : concat(contentRoot.getResourceDirectories(),
                    contentRoot.getTestResourceDirectories())) {
                    Path path = normalize(directory.getDirectory().toPath());
                    resources.add(path);
                    gradleDirectory(moduleDir, path, directory.isGenerated(), Role.RESOURCES, Role.TEST_RESOURCES);
                }
                for (RailroadSourceDirectory directory : concat(contentRoot.getSourceDirectories(),
                    contentRoot.getTestSourceDirectories())) {
                    Path path = normalize(directory.getDirectory().toPath());
                    if (!resources.contains(path)) {
                        gradleDirectory(moduleDir, path, directory.isGenerated(), Role.SOURCES, Role.TEST_SOURCES);
                    }
                }
                for (File excluded : contentRoot.getExcludedDirectories()) {
                    excluded(excluded.toPath());
                }
            }
            excluded(moduleDir.resolve("build"));
            excluded(moduleDir.resolve(".gradle"));
            return this;
        }

        private void gradleDirectory(Path moduleDir, Path directory, boolean generated, Role role, Role testRole) {
            if (generated) {
                sourceDirectory(moduleDir, directory, Role.GENERATED_SOURCES);
            } else {
                sourceDirectory(moduleDir, directory, isTestSourceSet(directory.getParent()) ? testRole : role);
            }
        }

        private static boolean isTestSourceSet(Path sourceSet) {
            return sourceSet != null && sourceSet.getFileName() != null
                && sourceSet.getFileName().toString().toLowerCase(Locale.ROOT).contains("test");
        }

        private static List<RailroadSourceDirectory> concat(
            Iterable<? extends RailroadSourceDirectory> first,
            Iterable<? extends RailroadSourceDirectory> second
        ) {
            List<RailroadSourceDirectory> directories = new ArrayList<>();
            first.forEach(directories::add);
            second.forEach(directories::add);
            return directories;
        }

        /**
         * Marks a directory as a source or resource root. A non-generated root's parent is marked as its source set
         * when that parent lies strictly inside the root's module.
         *
         * @param module root directory of the module containing the directory
         * @param directory source or resource root
         * @param role role of the directory
         * @return this builder
         */
        public Builder sourceDirectory(Path module, Path directory, Role role) {
            Path normalized = normalize(directory);
            directories.putIfAbsent(normalized, role);
            if (role == Role.GENERATED_SOURCES)
                return this;
            Path owner = normalize(module);
            Path sourceSet = normalized.getParent();
            if (sourceSet != null && sourceSet.startsWith(owner) && !sourceSet.equals(owner)) {
                sourceSets.putIfAbsent(sourceSet, Role.SOURCE_SET);
            }
            return this;
        }

        /**
         * Marks a directory as excluded from the build's sources, such as a build output directory.
         *
         * @param directory excluded directory
         * @return this builder
         */
        public Builder excluded(Path directory) {
            directories.putIfAbsent(normalize(directory), Role.EXCLUDED);
            return this;
        }

        /**
         * Creates the immutable structure.
         *
         * @return structure containing every collected role
         */
        public ProjectStructure build() {
            Map<Path, Role> roles = new HashMap<>(sourceSets);
            roles.putAll(directories);
            for (Path module : modules) {
                roles.put(module, Role.MODULE);
            }
            return new ProjectStructure(roles);
        }

    }
}
