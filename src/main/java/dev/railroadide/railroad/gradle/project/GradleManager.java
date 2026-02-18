package dev.railroadide.railroad.gradle.project;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.DefaultGradleEnvironment;
import dev.railroadide.railroad.Railroad;
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
import dev.railroadide.railroad.project.RailroadProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates all Gradle-related state for a {@link RailroadProject}, including cached environments and models.
 */
public final class GradleManager {
    private static final String DOWNLOAD_SOURCES_TASK = "railroadDownloadAllSources";
    private static final String DOWNLOAD_SOURCES_INIT_RESOURCE = "scripts/init-download-sources.gradle";

    private final RailroadProject project;
    private final Object lock = new Object();

    private ExecutorService modelExecutor;
    private ExecutorService executionExecutor;
    private GradleModelService modelService;
    private GradleExecutionService executionService;
    private GradleEnvironment environment;
    private GradleSettings gradleSettings;

    /**
     * Creates a new Gradle manager for the given project.
     *
     * @param project the project
     */
    public GradleManager(RailroadProject project) {
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
                GradleSettings settings = getGradleSettings();
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

    public GradleSettings getGradleSettings() {
        ensureIsGradleProject();

        synchronized (lock) {
            if (gradleSettings == null) {
                gradleSettings = buildGradleSettings();
            }

            return gradleSettings;
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

    /**
     * Downloads sources for all Gradle projects using the bundled download-sources plugin.
     *
     * @return a future that completes when the download task finishes
     */
    public CompletableFuture<Void> downloadAllSources() {
        var completion = new CompletableFuture<Void>();

        try {
            ensureIsGradleProject();
        } catch (IllegalStateException exception) {
            completion.completeExceptionally(exception);
            return completion;
        }

        Path initScriptPath;
        try {
            initScriptPath = extractInitScript(DOWNLOAD_SOURCES_INIT_RESOURCE, "railroad-download-sources");
        } catch (IOException exception) {
            completion.completeExceptionally(exception);
            return completion;
        }

        GradleSettings settings = getGradleSettings();
        JDK jdk = settings.getGradleJvm() != null ? settings.getGradleJvm() : JDKManager.getDefaultJDK();
        GradleExecutionService execService = getExecutionService(jdk);

        boolean offline = settings.isOfflineMode();
        var request = new GradleTaskExecutionRequest(
            DOWNLOAD_SOURCES_TASK,
            List.of("--init-script", initScriptPath.toAbsolutePath().toString()),
            Map.of(),
            Map.of(),
            offline,
            !offline,
            false,
            GradleConsoleMode.RICH
        );

        GradleTaskExecutionHandle handle;
        try {
            handle = execService.runTask(request);
        } catch (Exception exception) {
            deleteIfExists(initScriptPath);
            completion.completeExceptionally(exception);
            return completion;
        }

        handle.completionFuture().whenComplete((result, throwable) -> {
            deleteIfExists(initScriptPath);
            if (throwable != null) {
                completion.completeExceptionally(throwable);
            } else {
                completion.complete(null);
            }
        });

        return completion;
    }

    private void ensureIsGradleProject() {
        Path path = project.getPath();
        if (!isGradleProject())
            throw new IllegalStateException("Project at " + path + " is not a Gradle project.");
    }

    public boolean isGradleProject() {
        Path path = project.getPath();
        Path groovyBuildFile = path.resolve("build.gradle");
        Path kotlinBuildFile = path.resolve("build.gradle.kts");
        boolean hasGroovyBuild = Files.isRegularFile(groovyBuildFile) && Files.isReadable(groovyBuildFile);
        boolean hasKotlinBuild = Files.isRegularFile(kotlinBuildFile) && Files.isReadable(kotlinBuildFile);
        return hasGradleWrapper() || hasGroovyBuild || hasKotlinBuild;
    }

    private GradleSettings buildGradleSettings() {
        GradleInvocationPreferences prefs = loadGradleInvocationPreferences();

        boolean useWrapper = hasGradleWrapper();
        String wrapperVersion = getGradleVersion();
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

    private String getGradleVersion() {
        Path wrapperProps = project.getPath().resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties");
        if (!Files.isRegularFile(wrapperProps))
            return null;

        try {
            for (String rawLine : Files.readAllLines(wrapperProps)) {
                String line = rawLine.trim();
                if (line.startsWith("distributionUrl=")) {
                    String url = line.substring("distributionUrl=".length()).trim();

                    if ((url.startsWith("\"") && url.endsWith("\"")) || (url.startsWith("'") && url.endsWith("'"))) {
                        url = url.substring(1, url.length() - 1);
                    }

                    int lastSlash = url.lastIndexOf('/');
                    String filename = lastSlash != -1 ? url.substring(lastSlash + 1) : url;

                    Matcher m = Pattern.compile("gradle-([0-9][0-9A-Za-z.-]*)", Pattern.CASE_INSENSITIVE)
                        .matcher(filename);
                    if (m.find())
                        return m.group(1);

                    // Couldn't extract version from filename
                    return null;
                }
            }

            return null;
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error reading gradle-wrapper.properties", exception);
            return null;
        }
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

    private Path extractInitScript(String resourcePath, String prefix) throws IOException {
        try (var stream = AppResources.getResourceAsStream(resourcePath)) {
            if (stream == null)
                throw new IOException("Missing init script resource: " + resourcePath);

            Path tempFile = Files.createTempFile(prefix, ".gradle");
            Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();
            return tempFile;
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null)
            return;

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public void saveSettings() {
        synchronized (lock) {
            project.getDataStore().writeJson(
                "gradle/settings.json",
                new GradleInvocationPreferences(
                    gradleSettings.isOfflineMode(),
                    gradleSettings.isEnableBuildCache(),
                    gradleSettings.isParallelExecution(),
                    gradleSettings.isDaemonEnabled(),
                    gradleSettings.getDaemonIdleTimeout(),
                    gradleSettings.getMaxWorkerCount(),
                    gradleSettings.getCustomGradleHome(),
                    gradleSettings.getGradleUserHome()
                )
            );

            this.environment = null; // Invalidate cached environment
        }
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
