package dev.railroadide.railroad.gradle;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.Railroad;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.model.GradleProject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GradleHelper {
    public static Map<String, String> findGradleTasks(Path gradleProjectPath) {
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(gradleProjectPath.toFile());
        try (var connection = connector.connect()) {
            var model = connection.getModel(GradleProject.class);
            return model.getTasks().stream()
                .map(task -> Map.entry(task.getName(), task.getDescription() != null ? task.getDescription() : ""))
                .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
        } catch (Exception exception) {
            Railroad.LOGGER.warn("Failed to fetch Gradle tasks", exception);
            return Map.of();
        }
    }

    public static Map<String, String> supplyTaskOptions(Path gradleProjectPath) {
        ProcessBuilder builder = new ProcessBuilder(buildGradleHelpCommand(gradleProjectPath));
        builder.directory(gradleProjectPath.toFile());
        builder.redirectErrorStream(true);

        try {
            Railroad.LOGGER.debug("Running '{}' to discover Gradle CLI options", String.join(" ", builder.command()));
            Process process = builder.start();
            byte[] outputBytes = readAllBytes(process.getInputStream());
            int exitCode = process.waitFor();

            Map<String, String> parsed = GradleHelpParser.parse(outputBytes);
            if (!parsed.isEmpty())
                return parsed;

            if (exitCode != 0)
                Railroad.LOGGER.warn("Gradle --help exited with status {} for {}", exitCode, gradleProjectPath);
            else
                Railroad.LOGGER.debug("Gradle --help for {} produced no parsable options", gradleProjectPath);
        } catch (Exception exception) {
            Railroad.LOGGER.warn("Failed to fetch Gradle arguments for autocomplete", exception);
        }

        return Map.of();
    }

    private static String[] buildGradleHelpCommand(Path gradleProjectPath) {
        Path gradleWrapper = gradleProjectPath.resolve(OperatingSystem.isWindows() ? "gradlew.bat" : "gradlew");
        if (Files.isRegularFile(gradleWrapper) && Files.isExecutable(gradleWrapper))
            return new String[]{gradleWrapper.toAbsolutePath().toString(), "--help"};

        String gradleCommand = OperatingSystem.isWindows() ? "gradle.bat" : "gradle";
        return new String[]{gradleCommand, "--help"};
    }

    private static byte[] readAllBytes(InputStream stream) throws IOException {
        try (stream; var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }

            return builder.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    public static List<GradleTask> loadGradleTasks(Path gradleProjectPath) {
        Map<String, String> tasksMap = findGradleTasks(gradleProjectPath);
        Map<String, String> taskOptions = supplyTaskOptions(gradleProjectPath);

        List<GradleTask> tasks = new ArrayList<>();
        for (Map.Entry<String, String> entry : tasksMap.entrySet()) {
            String taskName = entry.getKey();
            String taskDescription = entry.getValue();
            tasks.add(new GradleTask(taskName, taskDescription, taskOptions));
        }

        return tasks;
    }
}
