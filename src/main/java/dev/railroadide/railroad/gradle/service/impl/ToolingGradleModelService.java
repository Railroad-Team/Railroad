package dev.railroadide.railroad.gradle.service.impl;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.gradle.GradleEnvironment;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.GradleModelListener;
import dev.railroadide.railroad.gradle.model.PersistedGradleModelStore;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.function.ThrowingSupplier;
import dev.railroadide.railroadplugin.action.RailroadProjectAction;
import dev.railroadide.railroadplugin.dto.FabricDataModel;
import dev.railroadide.railroadplugin.dto.RailroadProject;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Implementation of {@link GradleModelService} that uses the Gradle Tooling API to load
 * the Gradle build model.
 * <p>
 * Every imported model is persisted. The persisted model is restored on first use, so a reopened project has its
 * model without waiting for Gradle; it is re-imported in the background only when its build inputs changed.
 */
public class ToolingGradleModelService implements GradleModelService {
    private static final String INIT_SCRIPT = "scripts/init-gradle-plugin.gradle";

    private final Project project;
    private final GradleEnvironment environment;
    private final Executor executor;
    private final PersistedGradleModelStore store;

    private final Object lock = new Object();
    private final AtomicReference<GradleBuildModel> cachedModel = new AtomicReference<>();
    private final Duration modelTimeout = Duration.ofMinutes(3);
    private final List<GradleModelListener> listeners = new CopyOnWriteArrayList<>();
    private volatile CompletableFuture<GradleBuildModel> ongoingRefresh = null;
    private boolean restoreAttempted;

    /**
     * Creates a new ToolingGradleModelService.
     *
     * @param project the project for which to load the Gradle model
     * @param environment the Gradle environment configuration
     * @param executor the executor to use for asynchronous operations
     * @param store the store that persists imported models between sessions
     */
    public ToolingGradleModelService(
        Project project,
        GradleEnvironment environment,
        Executor executor,
        PersistedGradleModelStore store
    ) {
        this.project = Objects.requireNonNull(project);
        this.environment = Objects.requireNonNull(environment);
        this.executor = Objects.requireNonNull(executor);
        this.store = Objects.requireNonNull(store);
    }

    /**
     * Identifies the importer, so models persisted by a different init script or Railroad Gradle plugin are
     * discarded rather than restored.
     *
     * @return a hash of the init script that applies the Railroad Gradle plugin
     */
    public static String importerVersion() {
        try (InputStream stream = AppResources.getResourceAsStream(INIT_SCRIPT)) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to read Gradle init script", exception);
        }
    }

    /**
     * Loads the Gradle build model for the given project and environment.
     *
     * @param project the project for which to load the Gradle model
     * @param environment the Gradle environment configuration
     * @return the loaded GradleBuildModel
     */
    public static GradleBuildModel loadModel(Project project, GradleEnvironment environment) {
        GradleConnector connector = GradleConnector.newConnector()
            .forProjectDirectory(project.getPath().toFile());
        configureConnector(connector, environment);

        Path initScriptPath = null;
        try (ProjectConnection connection = connector.connect()) {
            initScriptPath = writeInitScript();
            String[] initScriptArgs = {"--init-script", initScriptPath.toAbsolutePath().toString()};
            var step = ActivityProgress.current().count("railroad.activity.import_steps", ActivityProgress.Unit.STEPS,
                5);
            configureJvm(connection.newBuild(), environment)
                .withArguments(initScriptArgs)
                .run();

            step.run();
            BuildEnvironment buildEnvironment = configureJvm(connection.model(BuildEnvironment.class), environment)
                .withArguments(initScriptArgs)
                .get();
            step.run();
            GradleBuild gradleBuild = configureJvm(connection.model(GradleBuild.class), environment)
                .withArguments(initScriptArgs)
                .get();
            step.run();
            RailroadProject gradleProject = configureJvm(connection.action(new RailroadProjectAction()), environment)
                .withArguments(initScriptArgs)
                .run();
            step.run();
            FabricDataModel fabricDataModel = requestOptionalModel(
                connection, FabricDataModel.class, initScriptArgs, environment);

            step.run();
            String gradleVersion = buildEnvironment.getGradle().getGradleVersion();
            Path rootDir = gradleBuild.getRootProject().getProjectDirectory().toPath();

            return new GradleBuildModel(gradleVersion, rootDir, fabricDataModel, gradleProject);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to load Gradle model", exception);
        } finally {
            if (initScriptPath != null) {
                try {
                    Files.deleteIfExists(initScriptPath);
                } catch (Exception _) {
                }
            }
        }
    }

    private static Path writeInitScript() {
        try {
            Path path = Files.createTempFile("gradle-init-script", ".gradle");
            path.toFile().deleteOnExit();
            Files.copy(AppResources.getResourceAsStream(INIT_SCRIPT), path,
                StandardCopyOption.REPLACE_EXISTING);
            return path;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to write Gradle init script", exception);
        }
    }

    private static <T> T requestOptionalModel(
        ProjectConnection connection,
        Class<T> modelClass,
        String[] initScriptArgs,
        GradleEnvironment environment
    ) {
        try {
            return configureJvm(connection.model(modelClass), environment)
                .withArguments(initScriptArgs)
                .get();
        } catch (UnknownModelException exception) {
            Railroad.LOGGER.warn("Gradle model {} is not available; continuing without it", modelClass.getSimpleName());
            return null;
        }
    }

    /**
     * Configures the given GradleConnector based on the provided GradleEnvironment.
     *
     * @param connector the GradleConnector to configure
     * @param environment the GradleEnvironment containing configuration settings
     */
    public static void configureConnector(GradleConnector connector, GradleEnvironment environment) {
        if (environment == null || connector == null)
            return;

        if (environment.useWrapper()) {
            connector.useBuildDistribution();
        } else {
            environment.installationPath().ifPresent(path -> connector.useInstallation(path.toFile()));
            environment.userHomePath().ifPresent(path -> connector.useGradleUserHomeDir(path.toFile()));
        }
    }

    private static <T extends LongRunningOperation> T configureJvm(T operation, GradleEnvironment environment) {
        environment.jvm().ifPresent(jvm -> operation.setJavaHome(jvm.path().toFile()));
        return operation;
    }

    private static <T> Supplier<T> safely(ThrowingSupplier<T> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        };
    }

    @Override
    public void addListener(GradleModelListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(GradleModelListener listener) {
        listeners.remove(listener);
    }

    @Override
    public CompletableFuture<GradleBuildModel> refreshModel(boolean force) {
        restorePersistedModel();
        CompletableFuture<GradleBuildModel> refresh;
        synchronized (lock) {
            if (!force) {
                GradleBuildModel existingModel = cachedModel.get();
                if (existingModel != null)
                    return CompletableFuture.completedFuture(existingModel);
            }

            if (ongoingRefresh != null)
                return ongoingRefresh;

            refresh = CompletableFuture.supplyAsync(
                safely(() -> {
                    try (var activity = BackgroundActivities.shared().start("railroad.activity.importing_gradle")) {
                        Instant importStarted = Instant.now();
                        GradleBuildModel model = ScopedValue.where(ActivityProgress.CURRENT, activity)
                            .call(() -> loadModel(project, environment));
                        activity.indeterminate("railroad.activity.saving_model");
                        persist(model, importStarted);
                        return model;
                    }
                }),
                executor)
                .orTimeout(modelTimeout.toMillis(), TimeUnit.MILLISECONDS);
            ongoingRefresh = refresh;
        }

        notifyReloadStarted();
        refresh.whenComplete((model, throwable) -> completeRefresh(refresh, model, throwable));
        return refresh;
    }

    private void restorePersistedModel() {
        boolean stale;
        synchronized (lock) {
            if (restoreAttempted)
                return;

            restoreAttempted = true;
            Optional<PersistedGradleModelStore.Restored> restored;
            try {
                restored = store.load(project.getPath());
            } catch (IOException | RuntimeException exception) {
                Railroad.LOGGER.warn("Ignoring unreadable persisted Gradle model for {}", project.getPath(), exception);
                return;
            }

            if (restored.isEmpty() || !cachedModel.compareAndSet(null, restored.get().model()))
                return;

            stale = !restored.get().current();
            Railroad.LOGGER.info("Restored {} persisted Gradle model for {}", stale ? "stale" : "current",
                project.getPath());
        }

        if (stale) {
            refreshModel(true);
        }
    }

    private void persist(GradleBuildModel model, Instant importStarted) {
        try {
            if (!store.save(project.getPath(), model, importStarted)) {
                Railroad.LOGGER.info("Not persisting Gradle model for {}: build files changed during the import",
                    project.getPath());
            }
        } catch (IOException | RuntimeException exception) {
            Railroad.LOGGER.warn("Failed to persist Gradle model for {}", project.getPath(), exception);
        }
    }

    private void completeRefresh(
        CompletableFuture<GradleBuildModel> refresh,
        GradleBuildModel model,
        Throwable throwable
    ) {
        synchronized (lock) {
            if (throwable == null && model != null) {
                cachedModel.set(model);
            }
        }

        try {
            if (throwable == null && model != null) {
                listeners.forEach(listener -> notifyListener(
                    () -> listener.modelReloadSucceeded(model),
                    "success"));
            } else {
                Throwable error = throwable != null
                    ? throwable
                    : new IllegalStateException("Failed to load model");
                Railroad.LOGGER.error("Failed to reload Gradle model", error);
                listeners.forEach(listener -> notifyListener(
                    () -> listener.modelReloadFailed(error),
                    "failure"));
            }
        } finally {
            synchronized (lock) {
                if (ongoingRefresh == refresh) {
                    ongoingRefresh = null;
                }
            }
        }
    }

    private void notifyReloadStarted() {
        listeners.forEach(listener -> notifyListener(listener::modelReloadStarted, "start"));
    }

    private void notifyListener(Runnable callback, String phase) {
        try {
            callback.run();
        } catch (Throwable error) {
            Railroad.LOGGER.error("Gradle model listener failed during {} notification", phase, error);
        }
    }

    @Override
    public Optional<GradleBuildModel> getCachedModel() {
        restorePersistedModel();
        return Optional.ofNullable(cachedModel.get());
    }
}
