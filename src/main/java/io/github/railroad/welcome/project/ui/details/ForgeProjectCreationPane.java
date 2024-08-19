package io.github.railroad.welcome.project.ui.details;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.text.StreamingTemplateEngine;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import io.github.railroad.Railroad;
import io.github.railroad.project.ForgeProjectData;
import io.github.railroad.project.Project;
import io.github.railroad.project.minecraft.mapping.MappingChannel;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.FileHandler;
import io.github.railroad.utility.ShutdownHooks;
import io.github.railroad.utility.function.ExceptionlessRunnable;
import io.github.railroad.utility.javafx.TextAreaOutputStream;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import org.codehaus.groovy.runtime.StringBufferWriter;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class ForgeProjectCreationPane extends RRBorderPane {
    private static final Pattern TOML_COMMENT_PATTERN = Pattern.compile("^#(\\w+=)|(\\[.+\\])");
    private static final String TEMPLATE_BUILD_GRADLE_URL = "https://raw.githubusercontent.com/Railroad-Team/Railroad/main/templates/forge/%s/template_build.gradle";
    private static final String TEMPLATE_SETTINGS_GRADLE_URL = "https://raw.githubusercontent.com/Railroad-Team/Railroad/main/templates/forge/%s/template_settings.gradle";

    private final AtomicReference<Project> newProject = new AtomicReference<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final RRVBox centerBox = new RRVBox(10);
    private final Label timeElapsedLabel = new Label("");
    private final Label taskLabel = new Label();
    private final long startTime = System.currentTimeMillis();
    private final TextArea outputArea = new TextArea();

    public ForgeProjectCreationPane(ForgeProjectData data) {
        centerBox.setAlignment(Pos.CENTER);
        var progressSpinner = new MFXProgressSpinner();
        centerBox.getChildren().addAll(progressSpinner);
        progressSpinner.setRadius(50);
        setCenter(centerBox);

        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setStyle("-fx-font-family: 'Consolas', monospace;");
        outputArea.textProperty().addListener((observable, oldValue, newValue) -> {
            outputArea.setScrollTop(Double.MAX_VALUE);
        });

        var progressBox = new RRVBox(10);
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
                Railroad.LOGGER.error("An error occurred while waiting for the executor to terminate.", exception);
            }

            newProject.get().open();
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

        ShutdownHooks.addHook(() -> {
            if (!executor.isShutdown())
                executor.shutdownNow();
        });
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

    private class ProjectCreationTask extends Task<Void> {
        private final ForgeProjectData data;

        public ProjectCreationTask(ForgeProjectData data) {
            this.data = data;
        }

        @Override
        protected Void call() {
            try {
                updateLabel("Creating project directory...");
                Path projectPath = data.projectPath().resolve(data.projectName());
                Files.createDirectories(projectPath);
                updateProgress(1, 17);
                Railroad.LOGGER.info("Project directory created successfully.");

                downloadExampleMod(projectPath);
                updateGradleProperties(projectPath);

                Path mainJava = projectPath.resolve("src/main/java/");
                Path oldPath = mainJava.resolve("com/example/examplemod/");
                String newFolderPath = data.groupId().replace(".", "/") + "/" + data.modId();
                Path newPath = mainJava.resolve(newFolderPath);
                renamePackages(oldPath, newPath, mainJava);

                updateModsToml(projectPath);
                refactorExampleClasses(newPath);

                var shell = new GroovyShell();
                var templateEngine = new StreamingTemplateEngine();
                Map<String, Object> args = createArgs(data);
                if (!updateBuildGradle(projectPath, args, shell, templateEngine))
                    return null;

                if (!updateSettingsGradle(projectPath, args, shell, templateEngine))
                    return null;

                createMixinsJson(projectPath);
                createAccessTransformer(projectPath);

                updateLabel("Creating project...");
                newProject.set(new Project(projectPath, data.projectName()));
                Railroad.PROJECT_MANAGER.newProject(newProject.get());
                updateProgress(14, 17);
                Railroad.LOGGER.info("Project created successfully.");

                setupGradle(projectPath);
                createGitRepository(projectPath);

                updateProgress(17, 17);
                Railroad.LOGGER.info("Project created successfully.");
                updateLabel("Project created successfully.");
            } catch (Exception exception) {
                // Handle errors
                Platform.runLater(() -> Railroad.showErrorAlert("Error", "An error occurred while creating the project.", exception.getClass().getSimpleName() + ": " + exception.getMessage()));
                Railroad.LOGGER.error("An error occurred while creating the project.", exception);
            }

            return null;
        }

        public void updateLabel(String text) {
            Platform.runLater(() -> taskLabel.setText(text));
        }

        private void downloadExampleMod(Path projectPath) throws IOException {
            updateLabel("Downloading Forge MDK...");
            String fileName = data.minecraftVersion().id() + "-" + data.forgeVersion().id();
            FileHandler.copyUrlToFile("https://maven.minecraftforge.net/net/minecraftforge/forge/" + fileName + "/forge-" + fileName + "-mdk.zip",
                    Path.of(projectPath.resolve(fileName) + ".zip"));
            updateProgress(2, 17);
            Railroad.LOGGER.info("Forge MDK downloaded successfully.");

            updateLabel("Unzipping Forge MDK...");
            FileHandler.unzipFile(projectPath.resolve(fileName + ".zip"), projectPath);
            updateProgress(3, 17);
            Railroad.LOGGER.info("Forge MDK unzipped successfully.");

            updateLabel("Deleting Forge MDK zip...");
            Files.deleteIfExists(Path.of(projectPath.resolve(fileName) + ".zip"));
            updateProgress(4, 17);
            Railroad.LOGGER.info("Forge MDK zip deleted successfully.");

            updateLabel("Deleting unnecessary files...");
            Files.deleteIfExists(projectPath.resolve("changelog.txt"));
            Files.deleteIfExists(projectPath.resolve("CREDITS.txt"));
            Files.deleteIfExists(projectPath.resolve("LICENSE.txt"));
            Files.deleteIfExists(projectPath.resolve("README.txt"));
            updateProgress(5, 17);
            Railroad.LOGGER.info("Unnecessary files deleted successfully.");
        }

        private void updateGradleProperties(Path projectPath) throws IOException {
            Path gradlePropertiesFile = projectPath.resolve("gradle.properties");
            String mappingChannel = data.mappingChannel().getName().toLowerCase(Locale.ROOT);
            if (data.mappingChannel() == MappingChannel.MOJMAP) {
                mappingChannel = "official";
            }

            FileHandler.updateKeyValuePairByLine("mapping_channel", mappingChannel, gradlePropertiesFile);
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
            updateProgress(6, 17);
            Railroad.LOGGER.info("gradle.properties updated successfully.");
        }

        private void renamePackages(Path oldPath, Path newPath, Path mainJava) throws IOException {
            updateLabel("Updating package name...");
            Files.createDirectories(newPath.getParent());
            Files.move(oldPath, newPath);

            // Delete 'com' directory if it's empty
            final Path comDir = mainJava.resolve("com");
            FileHandler.isDirectoryEmpty(comDir, (ExceptionlessRunnable) () -> FileHandler.deleteFolder(comDir));

            updateProgress(7, 17);
            Railroad.LOGGER.info("Package name updated successfully.");
        }

        private void updateModsToml(Path projectPath) throws IOException {
            updateLabel("Updating mods.toml...");
            Path modsToml = projectPath.resolve("src/main/resources/META-INF/mods.toml");
            List<String> lines = Files.readAllLines(modsToml);
            lines = lines.stream()
                    .filter(line -> line != null && (!line.startsWith("#") || TOML_COMMENT_PATTERN.matcher(line).find()))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.startsWith("#issueTrackerURL=") && data.issues().isPresent()) {
                    lines.set(index, "issueTrackerURL=\"" + data.issues().get() + "\"");
                } else if (line.startsWith("#updateJSONURL=") && data.updateJsonUrl().isPresent()) {
                    lines.set(index, "updateJSONURL=\"" + data.updateJsonUrl().get() + "\"");
                } else if (line.startsWith("#displayURL=") && data.updateJsonUrl().isPresent()) {
                    lines.set(index, "displayURL=\"" + data.updateJsonUrl().get() + "\"");
                } else if (line.startsWith("#displayTest=")) {
                    lines.set(index, "displayTest=\"" + data.displayTest().name() + "\"");
                } else if (line.startsWith("#credits=") && data.credits().isPresent()) {
                    lines.set(index, "credits=\"" + data.credits().orElse("") + "\"");
                } else if (line.startsWith("#clientSideOnly=")) {
                    lines.set(index, "clientSideOnly=" + data.clientSideOnly());
                }
            }

            Files.write(modsToml, lines);
            updateProgress(8, 17);
            Railroad.LOGGER.info("mods.toml updated successfully.");
        }

        private void refactorExampleClasses(Path newPath) throws IOException {
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
            updateProgress(9, 17);
            Railroad.LOGGER.info("Example classes refactored successfully.");
        }

        private boolean updateBuildGradle(Path projectPath, Map<String, Object> args, GroovyShell shell, StreamingTemplateEngine templateEngine) throws IOException, ClassNotFoundException {
            updateLabel("Updating build.gradle...");
            Path buildGradle = projectPath.resolve("build.gradle");
            String templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(data.minecraftVersion().id().substring(2));
            if(FileHandler.is404(templateBuildGradleUrl)) {
                templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(data.minecraftVersion().id().split("\\.")[1]);
            }

            if(FileHandler.is404(templateBuildGradleUrl)) {
                showErrorAlert("Error", "An error occurred while creating the project.", "No build.gradle template found for the specified Minecraft version.");
                return false;
            }

            FileHandler.copyUrlToFile(templateBuildGradleUrl, buildGradle);
            String buildGradleContent = Files.readString(buildGradle);
            if (!buildGradleContent.startsWith("// fileName:")) {
                showErrorAlert("Error", "An error occurred while creating the project.", "build.gradle template is invalid.");
                return false;
            }

            int newLineIndex = buildGradleContent.indexOf("\n");

            var binding = new Binding(args);
            binding.setVariable("defaultName", projectPath.relativize(buildGradle.toAbsolutePath()).toString());

            Object result = shell.parse(buildGradleContent.substring("// fileName:".length() + 1, newLineIndex), binding).run();
            if (result == null) {
                showErrorAlert("Error", "An error occurred while creating the project.", "build.gradle template is invalid.");
                return false;
            }

            var buffer = new StringBuffer();
            templateEngine.createTemplate(new StringReader(buildGradleContent))
                    .make(args)
                    .writeTo(new StringBufferWriter(buffer));
            Files.writeString(buildGradle, buffer);
            updateProgress(10, 17);
            Railroad.LOGGER.info("build.gradle updated successfully.");
            return true;
        }

        private boolean updateSettingsGradle(Path projectPath, Map<String, Object> args, GroovyShell shell, StreamingTemplateEngine templateEngine) throws IOException, ClassNotFoundException {
            updateLabel("Updating settings.gradle...");
            Path settingsGradle = projectPath.resolve("settings.gradle");
            String templateSettingsGradleUrl = TEMPLATE_SETTINGS_GRADLE_URL.formatted(data.minecraftVersion().id().substring(2));
            if(FileHandler.is404(templateSettingsGradleUrl)) {
                templateSettingsGradleUrl = TEMPLATE_SETTINGS_GRADLE_URL.formatted(data.minecraftVersion().id().split("\\.")[1]);
            }

            if(FileHandler.is404(templateSettingsGradleUrl)) {
                showErrorAlert("Error", "An error occurred while creating the project.", "No settings.gradle template found for the specified Minecraft version.");
                return false;
            }

            FileHandler.copyUrlToFile(templateSettingsGradleUrl, settingsGradle);
            String settingsGradleContent = Files.readString(settingsGradle);
            if (!settingsGradleContent.startsWith("// fileName:")) {
                showErrorAlert("Error", "An error occurred while creating the project.", "settings.gradle template is invalid.");
                return false;
            }

            int newLineIndex = settingsGradleContent.indexOf("\n");

            var binding = new Binding(args);
            binding.setVariable("defaultName", projectPath.relativize(settingsGradle.toAbsolutePath()).toString());

            Object result = shell.parse(settingsGradleContent.substring("// fileName:".length() + 1, newLineIndex), binding).run();
            if (result == null) {
                showErrorAlert("Error", "An error occurred while creating the project.", "settings.gradle template is invalid.");
                return false;
            }

            var buffer = new StringBuffer();
            templateEngine.createTemplate(new StringReader(settingsGradleContent))
                    .make(args)
                    .writeTo(new StringBufferWriter(buffer));
            Files.writeString(settingsGradle, buffer);
            updateProgress(11, 17);
            Railroad.LOGGER.info("settings.gradle updated successfully.");
            return true;
        }

        private void createMixinsJson(Path projectPath) throws IOException {
            if (data.useMixins()) {
                updateLabel("Creating " + data.modId() + ".mixins.json...");
                Path mixinsJson = projectPath.resolve("src/main/resources/" + data.modId() + ".mixins.json");

                var mixins = new JsonObject();
                mixins.addProperty("required", true);
                mixins.addProperty("minVersion", "0.8");
                mixins.addProperty("package", data.groupId() + "." + data.modId() + ".mixin");
                mixins.addProperty("compatibilityLevel", "JAVA_21"); // TODO: Determine java version
                mixins.add("mixins", new JsonArray());
                mixins.add("client", new JsonArray());
                mixins.add("server", new JsonArray());
                var injectors = new JsonObject();
                injectors.addProperty("defaultRequire", 1);
                mixins.add("injectors", injectors);

                Files.writeString(mixinsJson, Railroad.GSON.toJson(mixins));
                updateProgress(12, 17);
                Railroad.LOGGER.info("{}.mixins.json created successfully.", data.modId());
            }
        }

        private void createAccessTransformer(Path projectPath) throws IOException {
            if (data.useAccessTransformer()) {
                updateLabel("Creating accesstransformer.cfg...");
                Path accessTransformer = projectPath.resolve("src/main/resources/META-INF/accesstransformer.cfg");
                Files.createFile(accessTransformer);
                updateProgress(13, 17);
                Railroad.LOGGER.info("accesstransformer.cfg created successfully.");
            }
        }

        private void setupGradle(Path projectPath) {
            updateLabel("Running Gradle tasks...");
            var connector = GradleConnector.newConnector();
            connector.forProjectDirectory(projectPath.toFile());
            try (ProjectConnection connection = connector.connect()) {
                Platform.runLater(() -> centerBox.getChildren().add(outputArea));

                var outputStream = new TextAreaOutputStream(outputArea);
                connection.newBuild()
                        .forTasks("jar")
                        .setStandardOutput(outputStream)
                        .setStandardError(outputStream)
                        .run();

                updateProgress(15, 17);
                Railroad.LOGGER.info("Gradle tasks run successfully.");
                Platform.runLater(() -> centerBox.getChildren().remove(outputArea));
            } catch (BuildException exception) {
                showErrorAlert("Error", "An error occurred while creating the project.", exception.getClass().getSimpleName() + ": " + exception.getMessage());
                Railroad.LOGGER.error("An error occurred while running Gradle tasks.", exception);
            }
        }

        private void createGitRepository(Path projectPath) {
            // Create git repository
            if (data.createGit()) {
                updateLabel("Creating git repository...");
                try {
                    var processBuilder = new ProcessBuilder("git", "init");
                    processBuilder.directory(projectPath.toFile());
                    var process = processBuilder.start();
                    process.waitFor();

                    updateProgress(16, 17);
                    Railroad.LOGGER.info("Git repository created successfully.");
                } catch (IOException | InterruptedException exception) {
                    showErrorAlert("Error", "An error occurred while creating the project.", exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    Railroad.LOGGER.error("An error occurred while creating the git repository.", exception);
                }
            }
        }
    }
}
