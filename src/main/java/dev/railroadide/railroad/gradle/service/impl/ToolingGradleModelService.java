package dev.railroadide.railroad.gradle.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.gradle.GradleEnvironment;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.GradleModelListener;
import dev.railroadide.railroad.gradle.model.GradleModelMapper;
import dev.railroadide.railroad.gradle.model.GradleProjectModel;
import dev.railroadide.railroad.gradle.model.task.GradleTaskArgType;
import dev.railroadide.railroad.gradle.model.task.GradleTaskArgument;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.utility.function.ThrowingSupplier;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Implementation of {@link GradleModelService} that uses the Gradle Tooling API to load
 * the Gradle build model.
 */
public class ToolingGradleModelService implements GradleModelService {
    private static final String TASK_ARGS_PROPERTY = "railroad.taskArgs.output";
    private final Project project;
    private final GradleEnvironment environment;
    private final Executor executor;

    private final Object lock = new Object();
    private final AtomicReference<GradleBuildModel> cachedModel = new AtomicReference<>();
    private volatile CompletableFuture<GradleBuildModel> ongoingRefresh = null;

    private final Duration modelTimeout = Duration.ofMinutes(3);

    private final List<GradleModelListener> listeners = new CopyOnWriteArrayList<>();

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

    /**
     * Adds a listener to be notified of model loading events.
     *
     * @param listener the listener to add
     */
    public void addListener(GradleModelListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(GradleModelListener listener) {
        listeners.remove(listener);
    }

    @Override
    public CompletableFuture<GradleBuildModel> refreshModel(boolean force) {
        synchronized (lock) {
            if (!force) {
                GradleBuildModel existingModel = cachedModel.get();
                if (existingModel != null)
                    return CompletableFuture.completedFuture(existingModel);
            }

            if (ongoingRefresh != null && !ongoingRefresh.isDone())
                return ongoingRefresh;

            listeners.forEach(GradleModelListener::modelReloadStarted);

            ongoingRefresh = CompletableFuture.supplyAsync(safely(this::loadModel), executor)
                .orTimeout(modelTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((model, throwable) -> {
                    synchronized (lock) {
                        if (throwable == null && model != null) {
                            cachedModel.set(model);
                            listeners.forEach(listener -> listener.modelReloadSucceeded(model));
                        } else {
                            listeners.forEach(listener ->
                                listener.modelReloadFailed(throwable != null ?
                                    throwable :
                                    new IllegalStateException("Failed to load model"))
                            );
                        }

                        ongoingRefresh = null;
                    }
                });

            return ongoingRefresh;
        }
    }

    @Override
    public Optional<GradleBuildModel> getCachedModel() {
        return Optional.ofNullable(cachedModel.get());
    }

    private GradleBuildModel loadModel() {
        GradleConnector connector = GradleConnector.newConnector()
            .forProjectDirectory(project.getPath().toFile());
        configureConnector(connector, environment);

        Path initScriptPath = null;
        Path taskArgumentsPath = null;

        try (ProjectConnection connection = connector.connect()) {
            BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
            GradleBuild gradleBuild = connection.getModel(GradleBuild.class);

            String gradleVersion = buildEnvironment.getGradle().getGradleVersion();
            Path rootDir = gradleBuild.getRootProject().getProjectDirectory().toPath();

            GradleProject rootGradleProject;
            Map<String, List<GradleTaskArgument>> taskArguments = Collections.emptyMap();

            try {
                taskArgumentsPath = Files.createTempFile("railroad-task-args", ".json");
                initScriptPath = writeTaskArgumentsInitScript();

                ModelBuilder<GradleProject> projectModelBuilder = connection.model(GradleProject.class)
                    .withArguments(
                        "--init-script", initScriptPath.toAbsolutePath().toString(),
                        ("-D" + TASK_ARGS_PROPERTY + "=" + taskArgumentsPath.toAbsolutePath())
                    );

                rootGradleProject = projectModelBuilder.get();
                taskArguments = readTaskArguments(taskArgumentsPath);
            } catch (Exception exception) {
                rootGradleProject = connection.getModel(GradleProject.class);
            } finally {
                deleteIfExists(initScriptPath);
                deleteIfExists(taskArgumentsPath);
            }

            List<GradleProjectModel> projects = new ArrayList<>();
            if (rootGradleProject != null) {
                GradleModelMapper.collectProjects(rootGradleProject, projects, taskArguments);
            }

            return new GradleBuildModel(gradleVersion, rootDir, projects);
        }
    }

    /**
     * Configures the given GradleConnector based on the provided GradleEnvironment.
     *
     * @param connector   the GradleConnector to configure
     * @param environment the GradleEnvironment containing configuration settings
     */
    public static void configureConnector(GradleConnector connector, GradleEnvironment environment) {
        if (environment.useWrapper()) {
            connector.useBuildDistribution();
        } else {
            environment.installationPath().ifPresent(path -> connector.useInstallation(path.toFile()));
            environment.userHomePath().ifPresent(path -> connector.useGradleUserHomeDir(path.toFile()));
        }

        // TODO: Enable setting Java home via environment.jvm() when custom JVM support is implemented.
        // environment.jvm().ifPresent(jvm -> connector.setJavaHome(jvm.javaHome().toFile()));
    }

    private Path writeTaskArgumentsInitScript() throws IOException {
        Path scriptFile = Files.createTempFile("railroad-task-args-init", ".gradle");
        Files.writeString(scriptFile, loadTaskArgsInitScript(), StandardCharsets.UTF_8);
        return scriptFile;
    }

    private Map<String, List<GradleTaskArgument>> readTaskArguments(Path outputFile) {
        if (outputFile == null || !Files.isReadable(outputFile))
            return Collections.emptyMap();

        try {
            String json = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (json.isBlank())
                return Collections.emptyMap();

            var typeToken = new TypeToken<Map<String, List<TaskArgumentEntry>>>() {
            }.getType();
            Map<String, List<TaskArgumentEntry>> parsed = new Gson().fromJson(json, typeToken);
            if (parsed == null || parsed.isEmpty())
                return Collections.emptyMap();

            Map<String, List<GradleTaskArgument>> mapped = new HashMap<>();
            parsed.forEach((taskPath, entries) -> {
                if (entries == null)
                    return;

                List<GradleTaskArgument> arguments = entries.stream()
                    .map(this::mapArgument)
                    .filter(Objects::nonNull)
                    .toList();

                mapped.put(taskPath, arguments);
            });

            return mapped;
        } catch (IOException | JsonParseException exception) {
            return Collections.emptyMap();
        }
    }

    private GradleTaskArgument mapArgument(TaskArgumentEntry entry) {
        if (entry == null || entry.name == null || entry.type == null)
            return null;

        GradleTaskArgType argType = parseArgType(entry.type);
        List<String> enumValues = entry.enumValues != null ? entry.enumValues : List.of();
        String defaultValue = entry.defaultValue != null ? entry.defaultValue : "";
        String displayName = entry.displayName != null ? entry.displayName : entry.name;
        String description = entry.description != null ? entry.description : "";

        return new GradleTaskArgument(entry.name, displayName, argType, defaultValue, description, enumValues);
    }

    private GradleTaskArgType parseArgType(String type) {
        try {
            return GradleTaskArgType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            return GradleTaskArgType.STRING;
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

    private String loadTaskArgsInitScript() throws IOException {
        try (var stream = AppResources.getResourceAsStream("scripts/init-task-args.gradle")) {
            if (stream == null)
                throw new IOException("Missing init-task-args.gradle resource");

            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return content.replace("__TASK_ARGS_PROPERTY__", TASK_ARGS_PROPERTY);
        }
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

    private static final class TaskArgumentEntry {
        String name;
        String displayName;
        String type;
        String defaultValue;
        String description;
        List<String> enumValues;
    }
}
