package dev.railroadide.railroad.gradle.service.impl;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.gradle.GradleEnvironment;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.GradleModelListener;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.function.ThrowingSupplier;
import dev.railroadide.railroadplugin.dto.FabricDataModel;
import dev.railroadide.railroadplugin.dto.RailroadProject;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Implementation of {@link GradleModelService} that uses the Gradle Tooling API to load
 * the Gradle build model.
 */
public class ToolingGradleModelService implements GradleModelService {
    private final Project project;
    private final GradleEnvironment environment;
    private final Executor executor;

    private final Object lock = new Object();
    private final AtomicReference<GradleBuildModel> cachedModel = new AtomicReference<>();
    private final Duration modelTimeout = Duration.ofMinutes(3);
    private final List<GradleModelListener> listeners = new CopyOnWriteArrayList<>();
    private volatile CompletableFuture<GradleBuildModel> ongoingRefresh = null;

    /**
     * Creates a new ToolingGradleModelService.
     *
     * @param project     the project for which to load the Gradle model
     * @param environment the Gradle environment configuration
     * @param executor    the executor to use for asynchronous operations
     */
    public ToolingGradleModelService(Project project, GradleEnvironment environment, Executor executor) {
        this.project = Objects.requireNonNull(project);
        this.environment = Objects.requireNonNull(environment);
        this.executor = Objects.requireNonNull(executor);
    }

    public static GradleBuildModel loadModel(Project project, GradleEnvironment environment) {
        GradleConnector connector = GradleConnector.newConnector()
            .forProjectDirectory(project.getPath().toFile());
        configureConnector(connector, environment);

        Path initScriptPath = null;
        try (ProjectConnection connection = connector.connect()) {
            initScriptPath = writeInitScript();
            String[] initScriptArgs = {"--init-script", initScriptPath.toAbsolutePath().toString()};
            connection.newBuild().withArguments(initScriptArgs).run();

            BuildEnvironment buildEnvironment = connection.model(BuildEnvironment.class)
                .withArguments(initScriptArgs)
                .get();
            GradleBuild gradleBuild = connection.model(GradleBuild.class)
                .withArguments(initScriptArgs)
                .get();
            RailroadProject gradleProject = requestOptionalModel(connection, RailroadProject.class, initScriptArgs);
            FabricDataModel fabricDataModel = requestOptionalModel(connection, FabricDataModel.class, initScriptArgs);

            String gradleVersion = buildEnvironment.getGradle().getGradleVersion();
            Path rootDir = gradleBuild.getRootProject().getProjectDirectory().toPath();

            return new GradleBuildModel(gradleVersion, rootDir, fabricDataModel, gradleProject);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to load Gradle model", exception);
        } finally {
            if (initScriptPath != null) {
                try {
                    Files.deleteIfExists(initScriptPath);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static Path writeInitScript() {
        try {
            Path path = Files.createTempFile("gradle-init-script", ".gradle");
            path.toFile().deleteOnExit();
            Files.copy(AppResources.getResourceAsStream("scripts/init-gradle-plugin.gradle"), path, StandardCopyOption.REPLACE_EXISTING);
            return path;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to write Gradle init script", exception);
        }
    }

    private static <T> T requestOptionalModel(ProjectConnection connection, Class<T> modelClass, String[] initScriptArgs) {
        try {
            return connection.model(modelClass)
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
     * @param connector   the GradleConnector to configure
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

        // TODO: Enable setting Java home via environment.jvm() when custom JVM support is implemented.
        // environment.jvm().ifPresent(jvm -> connector.setJavaHome(jvm.javaHome().toFile()));
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
                    safely(() -> ToolingGradleModelService.loadModel(this.project, this.environment)),
                    executor)
                .orTimeout(modelTimeout.toMillis(), TimeUnit.MILLISECONDS);
            ongoingRefresh = refresh;
        }

        notifyReloadStarted();
        refresh.whenComplete((model, throwable) -> completeRefresh(refresh, model, throwable));
        return refresh;
    }

    private void completeRefresh(CompletableFuture<GradleBuildModel> refresh,
                                 GradleBuildModel model,
                                 Throwable throwable) {
        synchronized (lock) {
            if (throwable == null && model != null) {
                cachedModel.set(model);
            }
        }

        try {
            if (throwable == null && model != null) {
                listeners.forEach(listener -> notifyListener(
                    () -> listener.modelReloadSucceeded(model),
                    "success"
                ));
            } else {
                Throwable error = throwable != null
                    ? throwable
                    : new IllegalStateException("Failed to load model");
                listeners.forEach(listener -> notifyListener(
                    () -> listener.modelReloadFailed(error),
                    "failure"
                ));
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
        return Optional.ofNullable(cachedModel.get());
    }
}
