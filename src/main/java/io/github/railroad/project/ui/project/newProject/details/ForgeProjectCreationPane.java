package io.github.railroad.project.ui.project.newProject.details;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.text.StreamingTemplateEngine;
import io.github.palexdev.materialfx.controls.MFXProgressBar;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import io.github.railroad.Railroad;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.minecraft.mapping.MappingChannel;
import io.github.railroad.project.Project;
import io.github.railroad.project.data.FabricProjectData;
import io.github.railroad.project.data.ForgeProjectData;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.ExceptionlessRunnable;
import io.github.railroad.utility.FileHandler;
import io.github.railroad.utility.TextAreaOutputStream;
import javafx.application.Platform;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
import org.codehaus.groovy.runtime.StringBufferWriter;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ForgeProjectCreationPane extends RRBorderPane {
    private static final Pattern TOML_COMMENT_PATTERN = Pattern.compile("^#(\\w+=)|(\\[.+\\])");
    private static final String TEMPLATE_BUILD_GRADLE_URL = "https://raw.githubusercontent.com/Railroad-Team/Railroad/main/templates/forge/%s/template_build.gradle";
    private static final String TEMPLATE_SETTINGS_GRADLE_URL = "https://raw.githubusercontent.com/Railroad-Team/Railroad/main/templates/forge/%s/template_settings.gradle";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ForgeProjectData data;
    private final MFXProgressSpinner progressSpinner = new MFXProgressSpinner();
    private final RRVBox progressBox = new RRVBox(10), centerBox = new RRVBox(10);
    private final Label timeElapsedLabel = new Label("");
    private final Label taskLabel = new Label();
    private final long startTime = System.currentTimeMillis();
    private final TextArea outputArea = new TextArea();

    public ForgeProjectCreationPane(ForgeProjectData data) {
        this.data = data;

        centerBox.setAlignment(Pos.CENTER);
        centerBox.getChildren().addAll(progressSpinner);
        progressSpinner.setRadius(50);
        setCenter(centerBox);

        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setStyle("-fx-font-family: 'Consolas', monospace;");
        outputArea.textProperty().addListener((observable, oldValue, newValue) -> {
            outputArea.setScrollTop(Double.MAX_VALUE);
        });

        progressBox.setAlignment(Pos.CENTER);
        progressBox.getChildren().addAll(timeElapsedLabel, taskLabel);
        setBottom(progressBox);

        setTop(new Label("Creating project..."));
        setAlignment(getTop(), Pos.CENTER);
        progressSpinner.setProgress(0);

        var task = new ProjectCreationTask(data);
        progressSpinner.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(event -> {
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS))
                    executor.shutdownNow();
            } catch (InterruptedException exception) {
                System.err.println("An error occurred while waiting for the executor to terminate.");
                exception.printStackTrace(); // TODO: Replace with logger
            }

            // Open project in IDE
        });

        new Thread(task).start();

        executor.scheduleAtFixedRate(() -> {
            long timeElapsed = System.currentTimeMillis() - startTime;
            long seconds = timeElapsed / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            seconds %= 60;

            String timeElapsedString = "%d seconds".formatted(seconds);
            if (minutes > 0) {
                timeElapsedString = "%d minutes, ".formatted(minutes) + timeElapsedString;
            }

            if (hours > 0) {
                timeElapsedString = "%d hours, ".formatted(hours) + timeElapsedString;
            }

            final String finalTimeElapsedString = timeElapsedString;
            Platform.runLater(() -> timeElapsedLabel.setText("Time elapsed: " + finalTimeElapsedString));
        }, 1, 1, TimeUnit.SECONDS);
    }

    private static void showErrorAlert(String title, String header, String content) {
        Platform.runLater(() -> {
            var alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);

            alert.showAndWait();
        });
    }

    private class ProjectCreationTask extends Task<Void> {
        private final ForgeProjectData data;

        public ProjectCreationTask(ForgeProjectData data) {
            this.data = data;
        }

        @Override
        protected Void call() {
            try {
                updateLabel("Creating project directory...");
                String fileName = data.minecraftVersion().id() + "-" + data.forgeVersion().id();
                Path projectPath = data.projectPath().resolve(data.projectName());
                Files.createDirectories(projectPath);
                updateProgress(1, 16);
                System.out.println("Project directory created successfully.");

                updateLabel("Downloading Forge MDK...");
                FileHandler.copyUrlToFile("https://maven.minecraftforge.net/net/minecraftforge/forge/" + fileName + "/forge-" + fileName + "-mdk.zip",
                        Path.of(projectPath.resolve(fileName) + ".zip"));
                updateProgress(2, 16);
                System.out.println("Forge MDK downloaded successfully.");

                updateLabel("Unzipping Forge MDK...");
                FileHandler.unzipFile(Path.of(projectPath.resolve(fileName) + ".zip").toString(), projectPath.toString());
                updateProgress(3, 16);
                System.out.println("Forge MDK unzipped successfully.");

                updateLabel("Deleting Forge MDK zip...");
                Files.deleteIfExists(Path.of(projectPath.resolve(fileName) + ".zip"));
                updateProgress(4, 16);
                System.out.println("Forge MDK zip deleted successfully.");

                updateLabel("Deleting unnecessary files...");
                Files.deleteIfExists(projectPath.resolve("changelog.txt"));
                Files.deleteIfExists(projectPath.resolve("CREDITS.txt"));
                Files.deleteIfExists(projectPath.resolve("LICENSE.txt"));
                Files.deleteIfExists(projectPath.resolve("README.txt"));
                updateProgress(5, 16);
                System.out.println("Unnecessary files deleted successfully.");

                Path gradlePropertiesFile = projectPath.resolve("gradle.properties");
                FileHandler.updateKeyValuePairByLine("mapping_channel", data.mappingChannel().getName().toLowerCase(Locale.ROOT), gradlePropertiesFile);
                String mappingVersion = data.mappingVersion().getId();
                if (data.mappingChannel() == MappingChannel.YARN) {
                    mappingVersion = data.minecraftVersion().id() + "+" + mappingVersion;
                } else if (data.mappingChannel() == MappingChannel.PARCHMENT) {
                    mappingVersion = mappingVersion + "-" + data.minecraftVersion().id();
                }

                FileHandler.updateKeyValuePairByLine("mapping_version", mappingVersion, gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_id", data.modId(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_name", data.modName(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_license", data.license().getName(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_version", data.version(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_group_id", data.groupId() + "." + data.modId(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_authors", data.author().orElse(""), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_description", data.description().map(s -> "'''" + s + "'''").orElse(""), gradlePropertiesFile);
                updateProgress(6, 16);
                System.out.println("gradle.properties updated successfully.");

                updateLabel("Updating package name...");
                // rename "com/example/examplemod/" to data.groupId() + "/" + data.modId()
                Path mainJava = projectPath.resolve("src/main/java/");

                Path oldPath = mainJava.resolve("com/example/examplemod/");
                String newFolderPath = data.groupId().replace(".", "/") + "/" + data.modId();
                Path newPath = mainJava.resolve(newFolderPath);
                Files.createDirectories(newPath.getParent());
                Files.move(oldPath, newPath);

                // Delete 'com' directory if it's empty
                final Path comDir = mainJava.resolve("com");
                FileHandler.isDirectoryEmpty(comDir, (ExceptionlessRunnable) () -> FileHandler.deleteFolder(comDir));

                updateProgress(7, 16);
                System.out.println("Package name updated successfully.");

                updateLabel("Updating mods.toml...");
                Path modsToml = projectPath.resolve("src/main/resources/META-INF/mods.toml");
                List<String> lines = Files.readAllLines(modsToml);
                lines = lines.stream()
                        .filter(line -> line != null && (!line.startsWith("#") || TOML_COMMENT_PATTERN.matcher(line).find()))
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (line.startsWith("#issueTrackerURL=") && data.issues().isPresent()) {
                        lines.set(index, "issueTrackerURL=" + data.issues().get());
                    } else if (line.startsWith("#updateJSONURL=") && data.updateJsonUrl().isPresent()) {
                        lines.set(index, "updateJSONURL=" + data.updateJsonUrl().get());
                    }
//                    else if (line.startsWith("#displayURL=") && data.updateJsonUrl().isPresent()) {
//                      lines.set(index, "displayURL=" + data.updateJsonUrl().get());
//                  }
                }

                Files.write(modsToml, lines);
                updateProgress(8, 16);
                System.out.println("mods.toml updated successfully.");

                updateLabel("Refactoring example classes...");
                Path mainClass = newPath.resolve("ExampleMod.java");
                Path configClass = newPath.resolve("Config.java");

                Files.move(mainClass, newPath.resolve(data.mainClass() + ".java"));
                String mainClassContent = Files.readString(newPath.resolve(data.mainClass() + ".java"));
                mainClassContent = mainClassContent.replace("com.example.examplemod", data.groupId() + "." + data.modId());
                mainClassContent = mainClassContent.replace("ExampleMod", data.mainClass());
                mainClassContent = mainClassContent.replace("examplemod", data.modId());
                Files.writeString(newPath.resolve(data.mainClass() + ".java"), mainClassContent);

                String configClassContent = Files.readString(configClass);
                configClassContent = configClassContent.replace("com.example.examplemod", data.groupId() + "." + data.modId());
                configClassContent = configClassContent.replace("ExampleMod", data.mainClass());
                Files.writeString(configClass, configClassContent);
                updateProgress(9, 16);
                System.out.println("Example classes refactored successfully.");

                updateLabel("Updating build.gradle...");
                Path buildGradle = projectPath.resolve("build.gradle");
                String templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(data.minecraftVersion().id().substring(2));
                FileHandler.copyUrlToFile(templateBuildGradleUrl, buildGradle);
                String buildGradleContent = Files.readString(buildGradle);
                if(!buildGradleContent.startsWith("// fileName:")) {
                    showErrorAlert("Error", "An error occurred while creating the project.", "An error occurred while creating the project. Please try again.");
                    return null;
                }
                
                int newLineIndex = buildGradleContent.indexOf("\n");

                Map<String, Object> args = createArgs(data);
                var binding = new Binding(args);
                binding.setVariable("defaultName", projectPath.relativize(buildGradle.toAbsolutePath()).toString());

                var shell = new GroovyShell();
                Object result = shell.parse(buildGradleContent.substring("// fileName:".length() + 1, newLineIndex), binding).run();
                if(result == null) {
                    showErrorAlert("Error", "An error occurred while creating the project.", "An error occurred while creating the project. Please try again.");
                    return null;
                }

                var buffer = new StringBuffer();
                var templateEngine = new StreamingTemplateEngine();
                templateEngine.createTemplate(new StringReader(buildGradleContent))
                        .make(args)
                        .writeTo(new StringBufferWriter(buffer));
                Files.writeString(buildGradle, buffer);
                updateProgress(10, 16);
                System.out.println("build.gradle updated successfully.");

                updateLabel("Updating settings.gradle...");
                Path settingsGradle = projectPath.resolve("settings.gradle");
                String templateSettingsGradleUrl = TEMPLATE_SETTINGS_GRADLE_URL.formatted(data.minecraftVersion().id().substring(2));
                FileHandler.copyUrlToFile(templateSettingsGradleUrl, settingsGradle);
                String settingsGradleContent = Files.readString(settingsGradle);
                if(!settingsGradleContent.startsWith("// fileName:")) {
                    showErrorAlert("Error", "An error occurred while creating the project.", "An error occurred while creating the project. Please try again.");
                    return null;
                }

                newLineIndex = settingsGradleContent.indexOf("\n");

                binding = new Binding(args);
                binding.setVariable("defaultName", projectPath.relativize(settingsGradle.toAbsolutePath()).toString());

                shell = new GroovyShell();
                result = shell.parse(settingsGradleContent.substring("// fileName:".length() + 1, newLineIndex), binding).run();
                if(result == null) {
                    showErrorAlert("Error", "An error occurred while creating the project.", "An error occurred while creating the project. Please try again.");
                    return null;
                }

                buffer = new StringBuffer();
                templateEngine.createTemplate(new StringReader(settingsGradleContent))
                        .make(args)
                        .writeTo(new StringBufferWriter(buffer));
                Files.writeString(settingsGradle, buffer);
                updateProgress(11, 16);
                System.out.println("settings.gradle updated successfully.");

                updateLabel("Creating project...");
                Railroad.PROJECT_MANAGER.newProject(new Project(projectPath, this.data.projectName()));
                updateProgress(13, 16);
                System.out.println("Project created successfully.");

                updateLabel("Running Gradle tasks...");
                var connector = GradleConnector.newConnector();
                connector.forProjectDirectory(projectPath.toFile());
                try(ProjectConnection connection = connector.connect()) {
                    Platform.runLater(() -> centerBox.getChildren().add(outputArea));

                    var outputStream = new TextAreaOutputStream(outputArea);
                    connection.newBuild()
                            .forTasks("jar")
                            .setStandardOutput(outputStream)
                            .setStandardError(outputStream)
                            .run();

                    updateProgress(14, 16);
                    System.out.println("Gradle tasks run successfully.");
                    Platform.runLater(() -> centerBox.getChildren().remove(outputArea));
                } catch (BuildException exception) {
                    showErrorAlert("Error", "An error occurred while creating the project.", exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    exception.printStackTrace();
                }

                // Create git repository
                if(data.createGit()) {
                    updateLabel("Creating git repository...");
                    try {
                        var processBuilder = new ProcessBuilder("git", "init");
                        processBuilder.directory(projectPath.toFile());
                        var process = processBuilder.start();
                        process.waitFor();

                        updateProgress(15, 16);
                        System.out.println("Git repository created successfully.");
                    } catch (IOException | InterruptedException exception) {
                        showErrorAlert("Error", "An error occurred while creating the project.", exception.getClass().getSimpleName() + ": " + exception.getMessage());
                        exception.printStackTrace();
                    }
                }

                updateProgress(16, 16);
                System.out.println("Project created successfully.");
                updateLabel("Project created successfully.");
            } catch (Exception exception) {
                // Handle errors
                Platform.runLater(() -> Railroad.showErrorAlert("Error", "An error occurred while creating the project.", exception.getMessage()));
                exception.printStackTrace(); // TODO: Replace with logger
                // Railroad.LOGGER.error("An error occurred while creating the project.", exception);
            }

            return null;
        }

        public void updateLabel(String text) {
            Platform.runLater(() -> taskLabel.setText(text));
        }
    }

    private static Map<String, Object> createArgs(ForgeProjectData data) {
        final Map<String, Object> args = new HashMap<>();
        args.put("mappings", Map.of(
                "channel", data.mappingChannel().getName().toLowerCase(Locale.ROOT),
                "version", data.mappingVersion().getId()
        ));

        args.put("props", Map.of(
                "useMixins", data.useMixins(),
                "useAccessTransformer", data.useAccessTransformer(),
                "genRunFolders", data.genRunFolders()
        ));

        return args;
    }
}
