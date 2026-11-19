package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexService.Session;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.GradleModelListener;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.ide.sst.document.api.DocumentStore;
import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.RequestPriority;
import dev.railroadide.railroad.ide.sst.request.api.SstScheduler;
import dev.railroadide.railroad.ide.sst.request.api.WorkspaceRequestContext;
import dev.railroadide.railroad.ide.sst.provider.api.ProviderExecutor;
import java.time.Duration;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.language.impl.index.JavaLanguageIndexContext;
import dev.railroadide.railroad.ide.language.impl.index.JavaLanguageIndexContextContributor;
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
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Consumer;
import dev.railroadide.railroad.ide.sst.request.api.RequestContext;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.BiFunction;

/** Publishes immutable project configuration when facets, project settings, or imported modules change. */
public final class ProjectWorkspaceConfiguration implements AutoCloseable {
    private final Session indexSession;
    private long binaryGeneration;
    private boolean forceBinaryRefresh;
    private Future<?> binaryTask;
    private CompletableFuture<Void> binaryResolution = CompletableFuture.completedFuture(null);
    private final Project project;
    private final DocumentStore store;
    private final EventListener<FacetDetectedEvent> facetListener;
    private final ProjectDataStore.FileChangeListener settingsListener;
    private final GradleModelListener modelListener;
    private final EventListener<ProjectConfigurationChangedEvent> configurationListener;
    private final Function<Project, JavaLanguageIndexContext> dependencyResolver;
    private final BiFunction<Session, WorkspaceConfigurationSnapshot, WorkspaceConfigurationSnapshot> binaryPreparer;
    private final SstScheduler.Scope backgroundRequests = SstScheduler.shared().newScope();
    private Future<?> dependencyTask;
    private CompletableFuture<Void> resolution = CompletableFuture.completedFuture(null);
    private Map<String, String> requestedSettings;
    private long resolutionGeneration;
    private GradleModelService modelService;
    private boolean closed;

    /**
     * Subscribes to configuration changes for one project session.
     *
     * @param project configuration owner
     * @param store store receiving immutable configuration revisions
     */
    public ProjectWorkspaceConfiguration(Project project, DocumentStore store) {
        this(project, store, new JavaLanguageIndexContextContributor()::resolveWorkspace,
            Services.PROJECT_LANGUAGE_INDEX_SERVICE.session(project));
    }

    /**
     * Subscribes to configuration changes with a Maven resolver that runs on a background worker.
     *
     * @param project configuration owner
     * @param store store receiving immutable configuration revisions
     * @param dependencyResolver resolver invoked off the caller thread for Maven projects
     */
    public ProjectWorkspaceConfiguration(
        Project project,
        DocumentStore store,
        Function<Project, JavaLanguageIndexContext> dependencyResolver
    ) {
        this(project, store, dependencyResolver, null);
    }

    private ProjectWorkspaceConfiguration(
        Project project,
        DocumentStore store,
        Function<Project, JavaLanguageIndexContext> dependencyResolver,
        Session indexSession
    ) {
        this(project, store, dependencyResolver, indexSession,
            (session, configuration) -> JavaLanguageSupport.analysisContextProvider().prepareConfiguration(session,
                configuration));
    }

    /**
     * Creates configuration ownership with controlled dependency and artifact preparation.
     *
     * @param project configuration owner
     * @param store store receiving immutable configuration revisions
     * @param dependencyResolver resolver invoked on a background worker for Maven projects
     * @param indexSession active index session, or {@code null} for standalone configuration
     * @param binaryPreparer prepares artifact identity on a background worker when a session is present
     */
    public ProjectWorkspaceConfiguration(
        Project project,
        DocumentStore store,
        Function<Project, JavaLanguageIndexContext> dependencyResolver,
        Session indexSession,
        BiFunction<Session, WorkspaceConfigurationSnapshot, WorkspaceConfigurationSnapshot> binaryPreparer
    ) {
        this.indexSession = indexSession;
        this.binaryPreparer = Objects.requireNonNull(binaryPreparer);
        this.project = project;
        this.store = store;
        this.dependencyResolver = Objects.requireNonNull(dependencyResolver);
        facetListener = event -> {
            if (event.project() == project) {
                refreshSettings();
                attachModelService();
            }
        };
        settingsListener = (_, _) -> refreshSettings();
        configurationListener = event -> {
            if (event.project() == project) {
                if (indexSession == null) {
                    refreshSettings();
                } else {
                    refresh();
                }
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
        if (isMavenProject()) {
            refreshSettings();
        } else if (indexSession != null && binaryTask == null) {
            refreshSettings();
        }
    }

    /**
     * Returns completion of the currently requested dependency resolution and its configuration publication.
     *
     * @return completion for the current request; superseded or closed requests are cancelled
     */
    public synchronized CompletableFuture<Void> ready() {
        return resolution.thenCompose(_ -> binaryResolution.copy());
    }

    /** Explicitly refreshes binary inputs even when build settings and artifact metadata are unchanged. */
    public synchronized void refresh() {
        forceBinaryRefresh = true;
        requestedSettings = null;
        refreshSettings();
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

        Map<String, String> settings = settings(project);
        if (isMavenProject()) {
            resolveMaven(settings);
            return;
        }
        cancelResolution();
        WorkspaceConfigurationSnapshot current = store.capture().configuration();
        Map<String, String> values = new TreeMap<>(current.settings());
        values.keySet().removeIf(key -> key.startsWith("project."));
        if (modelService == null) {
            values.keySet().removeIf(key -> key.startsWith("module.") || key.startsWith("gradle."));
        }
        values.putAll(settings);
        publish(modelService == null ? moduleIds(values) : current.moduleIds(), values);
    }

    private boolean isMavenProject() {
        return project.hasFacet(FacetManager.MAVEN) && !project.hasFacet(FacetManager.GRADLE);
    }

    private void resolveMaven(Map<String, String> settings) {
        if (settings.equals(requestedSettings) && !resolution.isCompletedExceptionally())
            return;
        cancelResolution();
        // A new dependency request supersedes every stage of the preceding request.
        // Keep the published configuration until this request's binary inputs are ready.
        binaryGeneration++;
        if (binaryTask != null) {
            binaryTask.cancel(true);
            binaryTask = null;
        }
        binaryResolution.cancel(false);
        binaryResolution = CompletableFuture.completedFuture(null);
        requestedSettings = Map.copyOf(settings);
        long generation = resolutionGeneration;
        WorkspaceConfigurationSnapshot expected = store.capture().configuration();
        Path root = project.getPath();
        Map<String, String> inputs = requestedSettings;
        CompletableFuture<Void> completed = new CompletableFuture<>();
        resolution = completed;
        dependencyTask = submitBackground("dependencies", completed, request -> {
            try {
                JavaLanguageIndexContext context = dependencyResolver.apply(project);
                request.checkActive();
                Map<String, String> values = new TreeMap<>(inputs);
                captureJavaConfiguration(root, context, values);
                synchronized (this) {
                    request.checkActive();
                    if (closed || generation != resolutionGeneration || !root.equals(project.getPath())
                        || store.capture().configuration() != expected || !isMavenProject()) {
                        completed.cancel(false);
                        return;
                    }
                    // A changed POM may reach the filesystem before its configuration event is delivered.
                    if (!inputs.equals(settings(project))) {
                        refreshSettings();
                        completed.cancel(false);
                        return;
                    }
                    publish(moduleIds(values), values);
                }
                completed.complete(null);
            } catch (CancellationException exception) {
                completed.cancel(false);
            } catch (Exception exception) {
                synchronized (this) {
                    if (!closed && generation == resolutionGeneration) {
                        Railroad.LOGGER.warn("Cannot resolve workspace dependencies for {}", root, exception);
                    }
                }
                completed.completeExceptionally(exception);
            }
        });
    }

    private CompletableFuture<Void> submitBackground(
        String key,
        CompletableFuture<Void> completed,
        Consumer<RequestContext> operation
    ) {
        var cancellation = new CancellationTokenSource();
        Duration budget = Duration.ofMinutes(2);
        var context = new WorkspaceRequestContext(store.capture(), cancellation.token(), Deadline.after(budget));
        CompletableFuture<Void> task = backgroundRequests.submit(key, RequestPriority.BACKGROUND_INDEXING, context,
            cancellation, () -> ProviderExecutor.shared().executeWithContext("project-configuration:" + key,
                context, budget, child -> {
                    operation.accept(child);
                    return null;
                }));
        task.whenComplete((_, failure) -> {
            if (failure != null) {
                completed.completeExceptionally(failure);
            }
        });
        return task;
    }

    private void cancelResolution() {
        resolutionGeneration++;
        if (dependencyTask != null) {
            dependencyTask.cancel(true);
            dependencyTask = null;
        }
        resolution.cancel(false);
        resolution = CompletableFuture.completedFuture(null);
        requestedSettings = null;
    }

    private synchronized void publishModel(GradleBuildModel model) {
        if (closed || model.project() == null || !project.hasFacet(FacetManager.GRADLE))
            return;

        cancelResolution();
        forceBinaryRefresh = true;
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
        if (indexSession != null) {
            Map<String, String> previousInputs = new TreeMap<>(current.settings());
            previousInputs.keySet().removeIf(key -> key.startsWith("index."));
            Map<String, String> nextInputs = new TreeMap<>(values);
            nextInputs.keySet().removeIf(key -> key.startsWith("index."));
            if (!forceBinaryRefresh && current.settings().containsKey("index.binary.fingerprint")
                && current.moduleIds().equals(ids) && previousInputs.equals(nextInputs)) {
                // A revert to the published inputs also supersedes a different preparation in flight.
                binaryGeneration++;
                if (binaryTask != null) {
                    binaryTask.cancel(true);
                }
                binaryTask = null;
                binaryResolution.cancel(false);
                binaryResolution = CompletableFuture.completedFuture(null);
                return;
            }
            forceBinaryRefresh = false;
            long generation = ++binaryGeneration;
            if (binaryTask != null) {
                binaryTask.cancel(true);
            }
            binaryResolution.cancel(false);
            CompletableFuture<Void> completed = new CompletableFuture<>();
            binaryResolution = completed;
            var candidate = new WorkspaceConfigurationSnapshot(current.revision() + 1, ids, values);
            binaryTask = submitBackground("binary-inputs", completed, request -> {
                try {
                    var provider = JavaLanguageSupport.analysisContextProvider();
                    var prepared = binaryPreparer.apply(indexSession, candidate);
                    synchronized (this) {
                        request.checkActive();
                        if (closed || indexSession.isClosed() || generation != binaryGeneration) {
                            completed.cancel(false);
                            return;
                        }
                        Services.PROJECT_LANGUAGE_INDEX_SERVICE.refreshProject(indexSession);
                        provider.adoptConfiguration(indexSession, prepared);
                        store.publishConfiguration(prepared);
                    }
                    completed.complete(null);
                } catch (CancellationException exception) {
                    completed.cancel(false);
                } catch (Exception exception) {
                    if (!indexSession.isClosed()) {
                        Railroad.LOGGER.warn("Cannot refresh Java binary inputs for {}", project.getPath(), exception);
                    }
                    completed.completeExceptionally(exception);
                }
            });
            return;
        }
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
        if (!project.hasFacet(FacetManager.GRADLE) && !project.hasFacet(FacetManager.MAVEN)) {
            captureJavaConfiguration(project.getPath(),
                new JavaLanguageIndexContextContributor().resolveWorkspace(project),
                values);
        }
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

    private static void captureJavaConfiguration(
        Path root,
        JavaLanguageIndexContext context,
        Map<String, String> values
    ) {
        String prefix = "module.:.";
        values.put(prefix + "directory", root.toAbsolutePath().normalize().toString());
        values.put(prefix + "classpath", paths(context.classpathRoots().stream().map(Path::toFile).toList()));
        values.put(prefix + "modulePath", paths(context.modulePathRoots().stream().map(Path::toFile).toList()));
        values.put(prefix + "dependencies", paths(context.dependencyRoots().stream().map(Path::toFile).toList()));
        values.put(prefix + "sources", Stream.concat(
            context.sourceRoots().stream().map(path -> path + ":generated=false"),
            context.generatedRoots().stream().map(path -> path + ":generated=true"))
            .collect(Collectors.joining("\n")));
        if (context.jdkHome() != null) {
            values.put(prefix + "jdkHome", context.jdkHome().toString());
        }
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
        binaryGeneration++;
        if (binaryTask != null) {
            binaryTask.cancel(true);
        }
        binaryResolution.cancel(false);
        cancelResolution();
        backgroundRequests.close();
        Railroad.EVENT_BUS.unsubscribe(FacetDetectedEvent.class, facetListener);
        Railroad.EVENT_BUS.unsubscribe(ProjectConfigurationChangedEvent.class, configurationListener);
        project.getDataStore().removeFileChangeListener(settingsListener);
        if (modelService != null) {
            modelService.removeListener(modelListener);
        }
    }
}
