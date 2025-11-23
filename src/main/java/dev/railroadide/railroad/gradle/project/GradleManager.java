package dev.railroadide.railroad.gradle.project;

import dev.railroadide.railroad.DefaultGradleEnvironment;
import dev.railroadide.railroad.gradle.GradleEnvironment;
import dev.railroadide.railroad.gradle.GradleSettings;
import dev.railroadide.railroad.gradle.service.GradleConsoleMode;
import dev.railroadide.railroad.gradle.service.GradleExecutionService;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.gradle.service.impl.ToolingGradleExecutionService;
import dev.railroadide.railroad.gradle.service.impl.ToolingGradleModelService;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionHandle;
import dev.railroadide.railroad.gradle.service.task.GradleTaskExecutionRequest;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationTypes;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.data.GradleFacetData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Encapsulates all Gradle-related state for a {@link Project}, including cached environments and models.
 */
public final class GradleManager {
    private final Project project;
    private final Object lock = new Object();

    private ExecutorService modelExecutor;
    private ExecutorService executionExecutor;
    private GradleModelService modelService;
    private GradleExecutionService executionService;
    private GradleEnvironment environment;

    /**
     * Creates a new Gradle manager for the given project.
     *
     * @param project the project
     */
    public GradleManager(Project project) {
        this.project = Objects.requireNonNull(project);
    }

    /**
     * Gets the shared Gradle model service for this project.
     *
     * @return the model service
     * @throws IllegalStateException if the project is not a Gradle project
     */
    public GradleModelService getGradleModelService() {
        ensureIsGradleProject();

        synchronized (lock) {
            if (modelService == null) {
                if (modelExecutor == null || modelExecutor.isShutdown()) {
                    modelExecutor = Executors.newSingleThreadExecutor(runnable -> {
                        var thread = new Thread(runnable, "railroad-gradle-model-" + project.getPathString());
                        thread.setDaemon(true);
                        return thread;
                    });
                }

                modelService = new ToolingGradleModelService(
                    project,
                    getGradleEnvironment(),
                    modelExecutor
                );
            }

            return modelService;
        }
    }

    /**
     * Gets the Gradle environment for this project.
     *
     * @return the Gradle environment
     * @throws IllegalStateException if the project is not a Gradle project
     */
    public GradleEnvironment getGradleEnvironment() {
        ensureIsGradleProject();

        synchronized (lock) {
            if (environment == null) {
                Facet<GradleFacetData> gradleFacet = project.getFacet(FacetManager.GRADLE).orElseThrow();
                GradleFacetData gradleData = gradleFacet.getData();

                GradleSettings settings = buildGradleSettings(gradleData);
                Path gradleHome = discoverGradleInstallationPath();

                environment = new DefaultGradleEnvironment(
                    project,
                    gradleHome,
                    settings
                );
            }

            return environment;
        }
    }

    /**
     * Runs a simple task using the shared execution service; completes the provided future when done.
     *
     * @param taskName the name of the task to run
     * @param jdk      the JDK to use for execution
     * @param future   the future to complete when done
     */
    public void runBuildTaskAsync(String taskName, JDK jdk, CompletableFuture<Runnable> future) {
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(jdk, "jdk");

        try {
            ensureIsGradleProject();
        } catch (IllegalStateException exception) {
            future.completeExceptionally(exception);
            return;
        }

        GradleInvocationPreferences prefs = loadGradleInvocationPreferences();
        GradleExecutionService execService = getExecutionService(jdk);

        var request = new GradleTaskExecutionRequest(
            taskName,
            List.of(),
            Map.of(),
            Map.of(),
            prefs.offlineMode(),
            false,
            false,
            GradleConsoleMode.RICH
        );

        GradleTaskExecutionHandle handle = execService.runTask(request);
        handle.completionFuture().whenComplete((result, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
            } else {
                future.complete(() -> {
                });
            }
        });
    }

    private void ensureIsGradleProject() {
        if (!project.hasFacet(FacetManager.GRADLE))
            throw new IllegalStateException("Project does not have a Gradle facet.");
    }

    private GradleSettings buildGradleSettings(GradleFacetData gradleData) {
        GradleInvocationPreferences prefs = loadGradleInvocationPreferences();

        boolean useWrapper = hasGradleWrapper();
        String wrapperVersion = gradleData != null ? gradleData.getGradleVersion() : null;
        Path gradleUserHome = getEnvPath("GRADLE_USER_HOME").orElse(prefs.gradleUserHome());
        JDK gradleJvm = JDKManager.getDefaultJDK();

        List<RunConfiguration<?>> gradleRunConfigs =
            project.getRunConfigManager().getConfigurations().stream()
                .filter(config -> config != null && config.type() == RunConfigurationTypes.GRADLE)
                .toList();

        Path customGradleHome = useWrapper ? null : getEnvPath("GRADLE_HOME").orElse(prefs.customGradleHome());

        int maxWorkers = prefs.maxWorkerCount() != null ?
            prefs.maxWorkerCount() :
            Runtime.getRuntime().availableProcessors();

        return new GradleSettings(
            useWrapper,
            wrapperVersion,
            customGradleHome,
            gradleUserHome,
            gradleJvm,
            prefs.offlineMode(),
            prefs.enableBuildCache(),
            prefs.parallelExecution(),
            maxWorkers,
            gradleRunConfigs,
            prefs.isDaemonEnabled(),
            prefs.daemonIdleTimeout()
        );
    }

    private Path discoverGradleInstallationPath() {
        if (!hasGradleWrapper())
            return getEnvPath("GRADLE_HOME").orElse(null);

        // Prefer wrapper when available; external installation only needed when wrapper is absent.
        return null;
    }

    private boolean hasGradleWrapper() {
        Path wrapperProps = project.getPath().resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties");
        return Files.isRegularFile(wrapperProps);
    }

    private Optional<Path> getEnvPath(String envKey) {
        String value = System.getenv(envKey);
        if (value == null || value.isBlank())
            return Optional.empty();

        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            return Files.exists(path) ? Optional.of(path) : Optional.empty();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private GradleInvocationPreferences loadGradleInvocationPreferences() {
        return project.getDataStore().readJson("gradle/settings.json", GradleInvocationPreferences.class)
            .orElseGet(GradleInvocationPreferences::defaults);
    }

    // TODO: Support changing JDK at runtime (this would require recreating the execution service)
    private GradleExecutionService getExecutionService(JDK jdkOverride) {
        synchronized (lock) {
            if (executionService == null) {
                if (executionExecutor == null || executionExecutor.isShutdown()) {
                    executionExecutor = Executors.newCachedThreadPool(r -> {
                        var thread = new Thread(r, "railroad-gradle-exec-" + project.getPathString());
                        thread.setDaemon(true);
                        return thread;
                    });
                }

                var execEnv = new JdkOverridingEnvironment(getGradleEnvironment(), jdkOverride);
                executionService = new ToolingGradleExecutionService(project, execEnv, executionExecutor);
            }

            return executionService;
        }
    }
}
