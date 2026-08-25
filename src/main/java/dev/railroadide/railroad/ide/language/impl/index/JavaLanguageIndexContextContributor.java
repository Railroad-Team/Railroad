package dev.railroadide.railroad.ide.language.impl.index;

import coursierapi.Fetch;
import coursierapi.MavenRepository;
import coursierapi.Repository;
import coursierapi.error.CoursierError;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.project.GradleManager;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.language.index.LanguageIndexContext;
import dev.railroadide.railroad.ide.language.index.LanguageIndexContextContributor;
import dev.railroadide.railroad.maven.DefaultMavenModelService;
import dev.railroadide.railroad.maven.MavenModelService;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroadplugin.dto.RailroadConfiguration;
import dev.railroadide.railroadplugin.dto.RailroadContentRoot;
import dev.railroadide.railroadplugin.dto.RailroadCompilerOutput;
import dev.railroadide.railroadplugin.dto.RailroadDependency;
import dev.railroadide.railroadplugin.dto.RailroadJavaLanguageSettings;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import dev.railroadide.railroadplugin.dto.RailroadSourceDirectory;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class JavaLanguageIndexContextContributor implements LanguageIndexContextContributor {
    private static final MavenModelService MAVEN_MODELS = new DefaultMavenModelService();

    @Override
    public String languageId() {
        return JavaLanguageSupport.LANGUAGE_ID;
    }

    @Override
    public LanguageIndexContext resolve(Project project) {
        return new JavaLanguageIndexContext(
            resolveSourceRoots(project),
            resolveGeneratedRoots(project),
            resolveDependencyRoots(project),
            resolveClasspathRoots(project),
            resolveModuleRoots(project),
            resolveJdkHome(project));
    }

    private static List<Path> resolveSourceRoots(Project project) {
        List<Path> sourceRoots = new ArrayList<>();
        if (project.hasFacet(FacetManager.GRADLE)) {
            consumeGradleModel(project, model -> {
                for (RailroadModule module : model.project().getModules()) {
                    for (RailroadContentRoot sourceSet : module.getContentRoots()) {
                        for (RailroadSourceDirectory sourceDir : sourceSet.getSourceDirectories()) {
                            if (!sourceDir.isGenerated()) {
                                addReadableDirectory(sourceRoots, sourceDir.getDirectory().toPath());
                            }
                        }

                        for (RailroadSourceDirectory sourceDir : sourceSet.getTestSourceDirectories()) {
                            if (!sourceDir.isGenerated()) {
                                addReadableDirectory(sourceRoots, sourceDir.getDirectory().toPath());
                            }
                        }
                    }
                }
            });
        } else if (project.hasFacet(FacetManager.MAVEN)) {
            consumeMavenModel(project, model -> {
                Build build = model.getBuild();
                addReadableDirectory(sourceRoots,
                    resolveProjectPath(project, build != null ? build.getSourceDirectory() : null, "src/main/java"));
                addReadableDirectory(sourceRoots, resolveProjectPath(project,
                    build != null ? build.getTestSourceDirectory() : null, "src/test/java"));
            });
        } else {
            addReadableDirectory(sourceRoots, project.path().resolve("src/main/java"));
            addReadableDirectory(sourceRoots, project.path().resolve("src/test/java"));
        }

        return normalizePaths(sourceRoots);
    }

    private static List<Path> resolveGeneratedRoots(Project project) {
        List<Path> generatedRoots = new ArrayList<>();
        if (project.hasFacet(FacetManager.GRADLE)) {
            consumeGradleModel(project, model -> {
                for (RailroadModule module : model.project().getModules()) {
                    for (RailroadContentRoot sourceSet : module.getContentRoots()) {
                        for (RailroadSourceDirectory sourceDir : sourceSet.getSourceDirectories()) {
                            if (sourceDir.isGenerated()) {
                                addReadableDirectory(generatedRoots, sourceDir.getDirectory().toPath());
                            }
                        }

                        for (RailroadSourceDirectory sourceDir : sourceSet.getTestSourceDirectories()) {
                            if (sourceDir.isGenerated()) {
                                addReadableDirectory(generatedRoots, sourceDir.getDirectory().toPath());
                            }
                        }
                    }
                }
            });
        } else if (project.hasFacet(FacetManager.MAVEN)) {
            consumeMavenModel(project, model -> {
                Path buildDirectory = resolveMavenBuildDirectory(project, model);
                addReadableDirectory(generatedRoots, buildDirectory.resolve("generated-sources"));
                addReadableDirectory(generatedRoots, buildDirectory.resolve("generated-test-sources"));
            });
        }

        return normalizePaths(generatedRoots);
    }

    private static List<Path> resolveDependencyRoots(Project project) {
        List<Path> dependencyRoots = new ArrayList<>();
        if (project.hasFacet(FacetManager.GRADLE)) {
            consumeGradleModel(project, model -> {
                for (RailroadModule module : model.project().getModules()) {
                    module.getDependencyRoots().stream()
                        .map(File::toPath)
                        .forEach(path -> addReadableRoot(dependencyRoots, path));

                    for (RailroadConfiguration configuration : module.getConfigurations()) {
                        for (RailroadDependency dependency : configuration.getDependencies()) {
                            addDependencyRoot(dependencyRoots, dependency);
                        }
                    }
                }
            });
        } else if (project.hasFacet(FacetManager.MAVEN)) {
            consumeMavenModel(project, model -> addMavenDependencyRoots(project, model, dependencyRoots));
        } else {
            addLocalJarRoots(project, dependencyRoots);
        }

        return normalizePaths(dependencyRoots);
    }

    private static List<Path> resolveClasspathRoots(Project project) {
        List<Path> classpathRoots = new ArrayList<>();
        if (project.hasFacet(FacetManager.GRADLE)) {
            consumeGradleModel(project, model -> {
                for (RailroadModule module : model.project().getModules()) {
                    module.getClasspathRoots().stream()
                        .map(File::toPath)
                        .forEach(path -> addReadableRoot(classpathRoots, path));

                    RailroadCompilerOutput compilerOutput = module.getCompilerOutput();
                    if (compilerOutput != null) {
                        addReadableDirectory(classpathRoots, toPath(compilerOutput.getOutputDirectory()));
                        addReadableDirectory(classpathRoots, toPath(compilerOutput.getTestOutputDirectory()));
                    }

                    for (RailroadConfiguration configuration : module.getConfigurations()) {
                        if (!isClasspathConfiguration(configuration.getName()))
                            continue;

                        for (RailroadDependency dependency : configuration.getDependencies()) {
                            addDependencyRoot(classpathRoots, dependency);
                        }
                    }
                }
            });
        } else if (project.hasFacet(FacetManager.MAVEN)) {
            consumeMavenModel(project, model -> {
                addMavenDependencyRoots(project, model, classpathRoots);

                Path buildDirectory = resolveMavenBuildDirectory(project, model);
                addReadableDirectory(classpathRoots, buildDirectory.resolve("classes"));
                addReadableDirectory(classpathRoots, buildDirectory.resolve("test-classes"));
            });
        } else {
            addLocalJarRoots(project, classpathRoots);
        }

        return normalizePaths(classpathRoots);
    }

    private static boolean isClasspathConfiguration(String configurationName) {
        if (configurationName == null || configurationName.isBlank())
            return false;

        String normalizedName = configurationName.toLowerCase(Locale.ROOT);
        return normalizedName.endsWith("compileclasspath")
            || normalizedName.endsWith("runtimeclasspath")
            || normalizedName.equals("classpath");
    }

    private static void addDependencyRoot(List<Path> roots, RailroadDependency dependency) {
        if (dependency == null)
            return;

        File file = dependency.getFile();
        if (file != null) {
            addReadableRoot(roots, file.toPath());
        }

        for (RailroadDependency child : dependency.getChildren()) {
            addDependencyRoot(roots, child);
        }
    }

    private static List<Path> resolveModuleRoots(Project project) {
        List<Path> moduleRoots = new ArrayList<>();
        if (project.hasFacet(FacetManager.GRADLE)) {
            consumeGradleModel(project, model -> {
                model.project().getModules().stream()
                    .map(RailroadModule::getModulePathRoots)
                    .flatMap(List::stream)
                    .map(File::toPath)
                    .forEach(path -> addReadableRoot(moduleRoots, path));
            });
        } else if (project.hasFacet(FacetManager.MAVEN)) {
            addMavenModuleRoots(project.path(), moduleRoots);
        }

        return normalizePaths(moduleRoots);
    }

    private static Path resolveJdkHome(Project project) {
        if (project.hasFacet(FacetManager.GRADLE)) {
            Optional<Path> gradleJdkHome = resolveGradleJdkHome(project);
            if (gradleJdkHome.isPresent())
                return gradleJdkHome.get();
        }

        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank())
            return null;

        Path path = Path.of(javaHome);
        if (Files.exists(path) && Files.isDirectory(path) && Files.isReadable(path))
            return FileUtils.normalizePath(path);

        return null;
    }

    private static Optional<Path> resolveGradleJdkHome(Project project) {
        List<Path> paths = new ArrayList<>();
        consumeGradleModel(project, model -> {
            try {
                if (model.project() == null || model.project().javaLanguageSettings() == null)
                    return;

                RailroadJavaLanguageSettings settings = model.project().javaLanguageSettings();
                if (settings.getJdk() == null)
                    return;

                File javaHome = settings.getJdk().getJavaHome();
                if (javaHome != null) {
                    addReadableDirectory(paths, javaHome.toPath());
                }
            } catch (Exception exception) {
                Railroad.LOGGER.warn("Unable to resolve Gradle JDK home for project {}", project.getAlias(), exception);
            }
        });

        return normalizePaths(paths).stream().findFirst();
    }

    private static void addMavenDependencyRoots(Project project, Model model, List<Path> roots) {
        List<Dependency> dependencies = model.getDependencies();
        if (dependencies == null || dependencies.isEmpty())
            return;

        for (Dependency dependency : dependencies) {
            addMavenSystemDependencyRoot(project, dependency, roots);
        }

        coursierapi.Dependency[] coursierDependencies = dependencies.stream()
            .filter(JavaLanguageIndexContextContributor::shouldResolveWithCoursier)
            .map(dependency -> coursierapi.Dependency.of(
                dependency.getGroupId(),
                dependency.getArtifactId(),
                dependency.getVersion()))
            .toArray(coursierapi.Dependency[]::new);

        if (coursierDependencies.length == 0)
            return;

        MavenRepository[] repositories = model.getRepositories().stream()
            .map(org.apache.maven.model.Repository::getUrl)
            .filter(Objects::nonNull)
            .filter(url -> !url.isBlank())
            .map(MavenRepository::of)
            .toArray(MavenRepository[]::new);

        Fetch fetch = Fetch.create()
            .addRepositories(repositories)
            .addRepositories(Repository.central())
            .addDependencies(coursierDependencies);

        try {
            for (File jar : fetch.fetch()) {
                addReadableFile(roots, jar.toPath());
            }
        } catch (CoursierError error) {
            Railroad.LOGGER.error("Error resolving Maven dependencies for {}", project.path(), error);
        }
    }

    private static boolean shouldResolveWithCoursier(Dependency dependency) {
        if (dependency.getGroupId() == null || dependency.getArtifactId() == null || dependency.getVersion() == null)
            return false;

        if ("system".equals(dependency.getScope()) || "import".equals(dependency.getScope()))
            return false;

        String type = dependency.getType();
        return type == null || type.isBlank() || "jar".equals(type) || "bundle".equals(type) || "test-jar".equals(type);
    }

    private static void addMavenSystemDependencyRoot(Project project, Dependency dependency, List<Path> roots) {
        if (!"system".equals(dependency.getScope()) || dependency.getSystemPath() == null
            || dependency.getSystemPath().isBlank())
            return;

        try {
            addReadableFile(roots, resolveProjectPath(project, dependency.getSystemPath(), dependency.getSystemPath()));
        } catch (InvalidPathException exception) {
            Railroad.LOGGER.warn("Ignoring invalid Maven system dependency path '{}' in {}", dependency.getSystemPath(),
                project.path(), exception);
        }
    }

    private static void addMavenModuleRoots(Path projectRoot, List<Path> moduleRoots) {
        Optional<Model> model = buildMavenModel(projectRoot);
        if (model.isEmpty())
            return;

        List<String> modules = model.get().getModules();
        if (modules == null || modules.isEmpty())
            return;

        for (String module : modules) {
            if (module == null || module.isBlank())
                continue;

            Path moduleRoot = projectRoot.resolve(module);
            addReadableDirectory(moduleRoots, moduleRoot);
            addMavenModuleRoots(moduleRoot, moduleRoots);
        }
    }

    private static void addLocalJarRoots(Project project, List<Path> roots) {
        addLocalJarRoots(project.path().resolve("lib"), roots);
        addLocalJarRoots(project.path().resolve("libs"), roots);
    }

    private static void addLocalJarRoots(Path directory, List<Path> roots) {
        if (!Files.exists(directory) || !Files.isDirectory(directory) || !Files.isReadable(directory))
            return;

        try (var paths = Files.list(directory)) {
            paths
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .forEach(path -> addReadableFile(roots, path));
        } catch (Exception exception) {
            Railroad.LOGGER.error("Error resolving local JAR roots from {}", directory, exception);
        }
    }

    private static Path resolveMavenBuildDirectory(Project project, Model model) {
        Build build = model.getBuild();
        return resolveProjectPath(project, build != null ? build.getDirectory() : null, "target");
    }

    private static Path resolveProjectPath(Project project, String value, String defaultValue) {
        String path = value == null || value.isBlank() ? defaultValue : value;
        return project.path().resolve(path);
    }

    private static void addReadableDirectory(List<Path> paths, Path path) {
        if (path != null && Files.exists(path) && Files.isDirectory(path) && Files.isReadable(path)) {
            paths.add(path);
        }
    }

    private static void addReadableFile(List<Path> paths, Path path) {
        if (path != null && Files.exists(path) && Files.isRegularFile(path) && Files.isReadable(path)) {
            paths.add(path);
        }
    }

    private static void addReadableRoot(List<Path> paths, Path path) {
        if (path != null && Files.exists(path) && Files.isReadable(path)
            && (Files.isRegularFile(path) || Files.isDirectory(path))) {
            paths.add(path);
        }
    }

    private static Path toPath(File file) {
        return file == null ? null : file.toPath();
    }

    private static List<Path> normalizePaths(List<Path> paths) {
        if (paths.isEmpty())
            return List.of();

        return new ArrayList<>(new LinkedHashSet<>(FileUtils.normalizePaths(paths)));
    }

    private static void consumeMavenModel(Project project, Consumer<Model> modelConsumer) {
        MAVEN_MODELS.loadEffectiveModel(project.path()).ifPresent(modelConsumer);
    }

    private static Optional<Model> buildMavenModel(Path projectRoot) {
        return MAVEN_MODELS.loadEffectiveModel(projectRoot);
    }

    private static void consumeGradleModel(Project project, Consumer<GradleBuildModel> modelConsumer) {
        if (project.hasFacet(FacetManager.GRADLE)) {
            GradleManager gradleManager = project.getGradleManager();
            Optional<GradleBuildModel> cachedModel = gradleManager.getGradleModelService().getCachedModel();
            if (cachedModel.isPresent()) {
                modelConsumer.accept(cachedModel.get());
                return;
            }

            try {
                GradleBuildModel model = gradleManager.getGradleModelService()
                    .refreshModel(false)
                    .get(30, TimeUnit.SECONDS);
                if (model != null) {
                    modelConsumer.accept(model);
                }
            } catch (Exception exception) {
                Railroad.LOGGER.warn("Unable to load Gradle model for project {}", project.getAlias(), exception);
            }
        }
    }
}
