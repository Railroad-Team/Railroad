package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.GradleModelListener;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.ide.sst.document.api.DocumentStore;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.event.EventListener;
import dev.railroadide.railroad.plugin.spi.events.ProjectConfigurationChangedEvent;
import dev.railroadide.railroad.project.FacetDetectedEvent;
import dev.railroadide.railroad.project.data.ProjectDataStore;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroadplugin.dto.*;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Publishes immutable project configuration when facets, project settings, or imported modules change. */
public final class ProjectWorkspaceConfiguration implements AutoCloseable {
    private final Project project;
    private final DocumentStore store;
    private final EventListener<FacetDetectedEvent> facetListener;
    private final ProjectDataStore.FileChangeListener settingsListener;
    private final GradleModelListener modelListener;
    private final EventListener<ProjectConfigurationChangedEvent> configurationListener;
    private GradleModelService modelService;
    private boolean closed;

    /**
     * Subscribes to configuration changes for one project session.
     *
     * @param project configuration owner
     * @param store store receiving immutable configuration revisions
     */
    public ProjectWorkspaceConfiguration(Project project, DocumentStore store) {
        this.project = project;
        this.store = store;
        facetListener = event -> {
            if (event.project() == project) {
                refreshSettings();
                attachModelService();
            }
        };
        settingsListener = (_, _) -> refreshSettings();
        configurationListener = event -> {
            if (event.project() == project) {
                refreshSettings();
            }
        };
        modelListener = new GradleModelListener() {
            @Override
            public void modelReloadStarted() {
            }

            @Override
            public void modelReloadSucceeded(GradleBuildModel model) {
                publishModel(model);
            }

            @Override
            public void modelReloadFailed(Throwable error) {
                // Retain the last successful immutable configuration on import failure.
            }
        };
        Railroad.EVENT_BUS.subscribe(FacetDetectedEvent.class, facetListener);
        Railroad.EVENT_BUS.subscribe(ProjectConfigurationChangedEvent.class, configurationListener);
        try {
            project.getDataStore().addFileChangeListener(settingsListener);
        } catch (IllegalStateException exception) {
            project.getDataStore().removeFileChangeListener(settingsListener);
            Railroad.LOGGER.warn("Cannot watch project settings for {}", project.getPath(), exception);
        }
        attachModelService();
    }

    /**
     * Captures initial project settings without advancing the store's workspace revision.
     *
     * @param project configuration owner
     * @return initial immutable configuration
     */
    public static WorkspaceConfigurationSnapshot initial(Project project) {
        Map<String, String> values = settings(project);
        return new WorkspaceConfigurationSnapshot(0, moduleIds(values), values);
    }

    private synchronized void attachModelService() {
        if (closed || modelService != null || !project.hasFacet(FacetManager.GRADLE)
            || !project.getGradleManager().isGradleProject())
            return;

        modelService = project.getGradleManager().getGradleModelService();
        modelService.addListener(modelListener);
        modelService.getCachedModel().ifPresent(this::publishModel);
    }

    private synchronized void refreshSettings() {
        if (closed)
            return;

        WorkspaceConfigurationSnapshot current = store.capture().configuration();
        Map<String, String> values = new TreeMap<>(current.settings());
        values.keySet().removeIf(key -> key.startsWith("project."));
        values.putAll(settings(project));
        publish(modelService == null ? moduleIds(values) : current.moduleIds(), values);
    }

    private synchronized void publishModel(GradleBuildModel model) {
        if (closed || model.project() == null)
            return;

        Map<String, String> values = new TreeMap<>(store.capture().configuration().settings());
        values.keySet().removeIf(key -> key.startsWith("module.") || key.startsWith("gradle."));
        values.put("gradle.version", model.gradleVersion());
        RailroadJavaLanguageSettings language = model.project().javaLanguageSettings();
        languageSettings(values, "gradle.", language);
        Set<String> ids = new TreeSet<>();
        for (RailroadModule module : model.project().getModules()) {
            String id = module.getPath();
            ids.add(id);
            String prefix = "module." + id + ".";
            values.put(prefix + "classpath", paths(module.getClasspathRoots()));
            values.put(prefix + "modulePath", paths(module.getModulePathRoots()));
            values.put(prefix + "dependencies", paths(module.getDependencyRoots()));
            values.put(prefix + "directory", module.getProjectDir().toAbsolutePath().normalize().toString());
            languageSettings(values, prefix, module.getJavaLanguageSettings());
            RailroadCompilerOutput output = module.getCompilerOutput();
            if (output != null) {
                if (output.getOutputDirectory() != null) {
                    values.put(prefix + "output",
                        output.getOutputDirectory().toPath().toAbsolutePath().normalize().toString());
                }
                if (output.getTestOutputDirectory() != null) {
                    values.put(prefix + "testOutput",
                        output.getTestOutputDirectory().toPath().toAbsolutePath().normalize().toString());
                }
            }
            Set<String> sources = new TreeSet<>();
            for (RailroadContentRoot root : module.getContentRoots()) {
                for (RailroadSourceDirectory source : root.getSourceDirectories()) {
                    sources.add(source.getDirectory().toPath().toAbsolutePath().normalize() + ":generated="
                        + source.isGenerated());
                }
                for (RailroadSourceDirectory source : root.getTestSourceDirectories()) {
                    sources.add(source.getDirectory().toPath().toAbsolutePath().normalize() + ":test:generated="
                        + source.isGenerated());
                }
            }
            values.put(prefix + "sources", String.join("\n", sources));
        }
        publish(ids, values);
    }

    private void publish(Set<String> ids, Map<String, String> values) {
        WorkspaceConfigurationSnapshot current = store.capture().configuration();
        if (!current.moduleIds().equals(ids) || !current.settings().equals(values)) {
            store.publishConfiguration(new WorkspaceConfigurationSnapshot(current.revision() + 1, ids, values));
        }
    }

    private static String paths(Collection<File> files) {
        return files.stream().map(file -> file.toPath().toAbsolutePath().normalize().toString())
            .collect(Collectors.joining("\n"));
    }

    private static void languageSettings(
        Map<String, String> values,
        String prefix,
        RailroadJavaLanguageSettings language
    ) {
        if (language == null)
            return;
        if (language.getSourceCompatibility() != null) {
            values.put(prefix + "sourceLevel", language.getSourceCompatibility().toString());
        }
        if (language.getTargetCompatibility() != null) {
            values.put(prefix + "targetLevel", language.getTargetCompatibility().toString());
        }
        if (language.getJdk() != null && language.getJdk().getJavaHome() != null) {
            values.put(prefix + "jdkHome",
                language.getJdk().getJavaHome().toPath().toAbsolutePath().normalize().toString());
        }
    }

    private static Map<String, String> settings(Project project) {
        Map<String, String> values = new TreeMap<>();
        values.put("project.root", project.getPath().toAbsolutePath().normalize().toString());
        values.put("project.facets", project.getFacets().stream().map(facet -> facet.getType().id())
            .sorted().collect(Collectors.joining("\n")));
        captureMavenModules(project.getPath(), project.getPath(), values, new HashSet<>());
        for (String name : List.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "gradle.properties")) {
            Path file = project.getPath().resolve(name);
            if (Files.isRegularFile(file)) {
                try {
                    values.put("project.build." + name, Files.readString(file));
                } catch (IOException exception) {
                    throw new IllegalStateException("Cannot capture build configuration " + file, exception);
                }
            }
        }
        Path directory = project.getPath().resolve(".railroad");
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.walk(directory, 3)) {
                for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> !directory.relativize(path).toString().equals("project.json")).toList()) {
                    values.put("project.settings." + directory.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot capture project configuration", exception);
            }
        }
        return values;
    }

    private static Set<String> moduleIds(Map<String, String> settings) {
        Set<String> ids = new TreeSet<>();
        String prefix = "project.maven.module.";
        settings.keySet().stream().filter(key -> key.startsWith(prefix) && key.endsWith(".pom"))
            .map(key -> key.substring(prefix.length(), key.length() - 4)).forEach(ids::add);
        return ids.isEmpty() ? Set.of(":") : ids;
    }

    @SuppressWarnings("deprecation")
    private static void captureMavenModules(Path root, Path module, Map<String, String> values, Set<Path> visited) {
        Path pom = module.resolve("pom.xml");
        if (!Files.isRegularFile(pom))
            return;
        try {
            if (!visited.add(pom.toRealPath()))
                return;
            String relative = root.toAbsolutePath().normalize().relativize(module.toAbsolutePath().normalize())
                .toString().replace('\\', ':').replace('/', ':');
            String prefix = "project.maven.module.:" + relative;
            values.put(prefix + ".pom", Files.readString(pom));
            Model model;
            try (Reader reader = Files.newBufferedReader(pom)) {
                model = new MavenXpp3Reader().read(reader);
            }
            for (String child : model.getModules()) {
                captureMavenModules(root, module.resolve(child).normalize(), values, visited);
            }
        } catch (Exception exception) {
            // The raw POM remains captured so a malformed build can still be opened and repaired.
            Railroad.LOGGER.warn("Cannot discover modules from {}", pom, exception);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        Railroad.EVENT_BUS.unsubscribe(FacetDetectedEvent.class, facetListener);
        Railroad.EVENT_BUS.unsubscribe(ProjectConfigurationChangedEvent.class, configurationListener);
        project.getDataStore().removeFileChangeListener(settingsListener);
        if (modelService != null) {
            modelService.removeListener(modelListener);
        }
    }
}
