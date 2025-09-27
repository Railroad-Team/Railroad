package dev.railroadide.railroad.project.creation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.railroadide.core.ui.RRBorderPane;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.data.ForgeProjectData;
import dev.railroadide.railroad.project.minecraft.mappings.channels.MappingChannelRegistry;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroad.utility.UrlUtils;
import dev.railroadide.railroad.utility.function.ExceptionlessRunnable;
import dev.railroadide.railroad.utility.javafx.TextAreaOutputStream;
import dev.railroadide.railroad.welcome.WelcomePane;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.text.StreamingTemplateEngine;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
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
import java.util.regex.Pattern;

public class ForgeProjectCreationPane extends RRBorderPane {
    private static final Pattern TOML_COMMENT_PATTERN = Pattern.compile("^#(\\w+=)|(\\[.+\\])");
    private static final String TEMPLATE_BUILD_GRADLE_URL = "https://raw.githubusercontent.com/Railroad-Team/Railroad/dev/templates/forge/%s/template_build.gradle";
    private static final String TEMPLATE_SETTINGS_GRADLE_URL = "https://raw.githubusercontent.com/Railroad-Team/Railroad/dev/templates/forge/%s/template_settings.gradle";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final RRVBox centerBox = new RRVBox(20);
    private final LocalizedLabel timeElapsedLabel = new LocalizedLabel("railroad.project.creation.status.time_elapsed", "");
    private final LocalizedLabel taskLabel = new LocalizedLabel("railroad.project.creation.status.task", "");
    private final long startTime = System.currentTimeMillis();
    private final TextArea outputArea = new TextArea();
    private final MFXProgressSpinner progressSpinner = new MFXProgressSpinner();
    private final RRButton cancelButton = new RRButton("railroad.generic.cancel");
    private final ForgeProjectData data;

    public ForgeProjectCreationPane(ForgeProjectData data) {
        this.data = data;
        setupUI();
        startProjectCreation();
    }

    private static Map<String, Object> createArgs(ForgeProjectData data) {
        final Map<String, Object> args = new HashMap<>();
        args.put("mappings", Map.of(
                "channel", data.mappingChannel().id().toLowerCase(Locale.ROOT),
                "version", data.mappingVersion()
        ));

        args.put("props", Map.of(
                "useMixins", data.useMixins(),
                "useAccessTransformer", data.useAccessTransformer(),
                "genRunFolders", data.genRunFolders()
        ));

        return args;
    }

    private void setupUI() {
        setPadding(new Insets(24));

        var headerBox = new RRVBox(8);
        headerBox.setAlignment(Pos.CENTER);
        var titleLabel = new LocalizedLabel("railroad.project.creation.status.creating_forge");
        titleLabel.getStyleClass().add("project-creation-title");
        var subtitleLabel = new LocalizedLabel("railroad.project.creation.status.creating_forge.subtitle", data.projectName());
        subtitleLabel.getStyleClass().add("project-creation-subtitle");
        headerBox.getChildren().addAll(titleLabel, subtitleLabel);
        setTop(headerBox);

        centerBox.setAlignment(Pos.CENTER);
        centerBox.setMaxWidth(600);

        progressSpinner.setRadius(60);
        progressSpinner.setProgress(0);

        var progressInfoBox = new RRVBox(12);
        progressInfoBox.setAlignment(Pos.CENTER);
        progressInfoBox.getChildren().addAll(taskLabel, timeElapsedLabel);

        centerBox.getChildren().addAll(progressSpinner, progressInfoBox);
        setCenter(centerBox);

        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setPrefRowCount(8);
        outputArea.getStyleClass().add("project-creation-output");
        outputArea.textProperty().addListener((observable, oldValue, newValue) -> {
            outputArea.setScrollTop(Double.MAX_VALUE);
        });

        var outputScrollPane = new ScrollPane(outputArea);
        outputScrollPane.setFitToWidth(true);
        outputScrollPane.setFitToHeight(true);
        outputScrollPane.setPrefHeight(200);
        outputScrollPane.getStyleClass().add("project-creation-output-scroll");

        var bottomBox = new RRVBox(16);
        bottomBox.setAlignment(Pos.CENTER);

        var buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER);

        cancelButton.setVariant(RRButton.ButtonVariant.SECONDARY);
        cancelButton.setOnAction(e -> handleCancel());

        buttonBox.getChildren().add(cancelButton);
        bottomBox.getChildren().addAll(outputScrollPane, buttonBox);
        setBottom(bottomBox);

        ShutdownHooks.addHook(() -> {
            if (!executor.isShutdown())
                executor.shutdownNow();
        });
    }

    private void startProjectCreation() {
        var task = new ProjectCreationTask(data);
        progressSpinner.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(event -> {
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS))
                    executor.shutdownNow();
            } catch (InterruptedException exception) {
                Railroad.LOGGER.error("An error occurred while waiting for the executor to terminate.", exception);
            }

            // Project created successfully - open in IDE
            Platform.runLater(() -> {
                try {
                    Path projectPath = data.projectPath().resolve(data.projectName());
                    Project project = new Project(projectPath, data.projectName());
                    Railroad.switchToIDE(project);
                } catch (Exception e) {
                    Railroad.LOGGER.error("Failed to open project in IDE", e);
                    showErrorAndReturnToWelcome("railroad.project.creation.error.open_ide.title",
                            "railroad.project.creation.error.open_ide.header",
                            "railroad.project.creation.error.open_ide.content");
                }
            });
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            Railroad.LOGGER.error("Project creation failed", exception);

            String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
            showErrorAndReturnToWelcome("railroad.project.creation.error.title",
                    "railroad.project.creation.error.forge.header",
                    "railroad.project.creation.error.content", errorMessage);
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
            Platform.runLater(() -> timeElapsedLabel.setKey("railroad.project.creation.status.time_elapsed", finalTimeElapsedString));
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void handleCancel() {
        Railroad.showErrorAlert("railroad.project.creation.cancel.title",
                "railroad.project.creation.cancel.header",
                "railroad.project.creation.cancel.content",
                buttonType -> {
                    if (buttonType == ButtonType.OK) {
                        executor.shutdownNow();
                        returnToWelcome();
                    }
                });
    }

    private void showErrorAndReturnToWelcome(String titleKey, String headerKey, String contentKey) {
        showErrorAndReturnToWelcome(titleKey, headerKey, contentKey, null);
    }

    private void showErrorAndReturnToWelcome(String titleKey, String headerKey, String contentKey, String additionalInfo) {
        Platform.runLater(() -> {
            String title = L18n.localize(titleKey);
            String header = L18n.localize(headerKey);
            String content = L18n.localize(contentKey);

            if (additionalInfo != null) {
                content += "\n\n" + additionalInfo;
            }

            Railroad.showErrorAlert(title, header, content, buttonType -> {
                if (buttonType == ButtonType.OK) {
                    returnToWelcome();
                }
            });
        });
    }

    private void returnToWelcome() {
        Platform.runLater(() -> {
            getScene().setRoot(new WelcomePane());
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
                updateLabel("railroad.project.creation.task.creating_directory");
                Path projectPath = data.projectPath().resolve(data.projectName());
                Files.createDirectories(projectPath);
                updateProgress(1, 17);
                Railroad.LOGGER.info("Project directory created successfully.");

                downloadExampleMod(projectPath);
                updateGradleProperties(projectPath);

                Path mainJava = projectPath.resolve("src/main/java");
                String newFolderPath = data.groupId().replace(".", "/") + "/" + data.modId();
                Path oldPath = mainJava.resolve("com/example/examplemod");
                Path newPath = mainJava.resolve(newFolderPath);

                renamePackages(oldPath, newPath, mainJava);
                updateModsToml(projectPath);
                refactorExampleClasses(newPath);

                Map<String, Object> args = createArgs(data);
                var shell = new GroovyShell();
                var templateEngine = new StreamingTemplateEngine();

                if (!updateBuildGradle(projectPath, args, shell, templateEngine)) {
                    throw new RuntimeException("Failed to update build.gradle");
                }

                if (!updateSettingsGradle(projectPath, args, shell, templateEngine)) {
                    throw new RuntimeException("Failed to update settings.gradle");
                }

                createMixinsJson(projectPath);
                createAccessTransformer(projectPath);

                updateLabel("railroad.project.creation.task.creating_project");
                Railroad.PROJECT_MANAGER.newProject(new Project(projectPath, this.data.projectName()));
                updateProgress(14, 17);
                Railroad.LOGGER.info("Project created successfully.");

                setupGradle(projectPath);
                createGitRepository(projectPath);

                updateProgress(17, 17);
                Railroad.LOGGER.info("Project created successfully.");
                updateLabel("railroad.project.creation.task.project_created");
            } catch (Exception exception) {
                Railroad.LOGGER.error("An error occurred while creating the project.", exception);
                throw new RuntimeException("Project creation failed: " + exception.getMessage(), exception);
            }
            return null;
        }

        public void updateLabel(String translationKey) {
            Platform.runLater(() -> taskLabel.setKey(translationKey));
        }

        public void updateLabel(String translationKey, Object... args) {
            Platform.runLater(() -> taskLabel.setKey(translationKey, args));
        }

        private void downloadExampleMod(Path projectPath) throws IOException {
            updateLabel("railroad.project.creation.task.downloading_mdk", "Forge");
            String forgeVersion = data.forgeVersion();
            FileUtils.copyUrlToFile("https://maven.minecraftforge.net/net/minecraftforge/forge/" + forgeVersion + "/forge-" + forgeVersion + "-mdk.zip",
                    Path.of(projectPath.resolve(forgeVersion) + ".zip"));
            updateProgress(2, 17);
            Railroad.LOGGER.info("Forge MDK downloaded successfully.");

            updateLabel("railroad.project.creation.task.unzipping_mdk", "Forge");
            FileUtils.unzipFile(projectPath.resolve(forgeVersion + ".zip"), projectPath);
            updateProgress(3, 17);
            Railroad.LOGGER.info("Forge MDK unzipped successfully.");

            updateLabel("railroad.project.creation.task.deleting_zip", "Forge");
            Files.deleteIfExists(Path.of(projectPath.resolve(forgeVersion) + ".zip"));
            updateProgress(4, 17);
            Railroad.LOGGER.info("Forge MDK zip deleted successfully.");

            updateLabel("railroad.project.creation.task.deleting_files");
            Files.deleteIfExists(projectPath.resolve("changelog.txt"));
            Files.deleteIfExists(projectPath.resolve("CREDITS.txt"));
            Files.deleteIfExists(projectPath.resolve("LICENSE.txt"));
            Files.deleteIfExists(projectPath.resolve("README.txt"));
            updateProgress(5, 17);
            Railroad.LOGGER.info("Unnecessary files deleted successfully.");
        }

        private void updateGradleProperties(Path projectPath) throws IOException {
            updateLabel("railroad.project.creation.task.updating_gradle");
            Path gradlePropertiesFile = projectPath.resolve("gradle.properties");
            String mappingChannel = data.mappingChannel().id().toLowerCase(Locale.ROOT);
            if (data.mappingChannel() == MappingChannelRegistry.MOJMAP) {
                mappingChannel = "official";
            }

            FileUtils.updateKeyValuePair("mapping_channel", mappingChannel, gradlePropertiesFile);
            String mappingVersion = data.mappingVersion();
            if (data.mappingChannel() == MappingChannelRegistry.PARCHMENT) {
                mappingVersion = mappingVersion + "-" + data.minecraftVersion().id();
            }

            FileUtils.updateKeyValuePair("mapping_version", mappingVersion, gradlePropertiesFile);
            FileUtils.updateKeyValuePair("mod_id", data.modId(), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("mod_name", data.modName(), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("mod_license", data.license().getName(), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("mod_version", data.version(), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("mod_group_id", data.groupId() + "." + data.modId(), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("mod_authors", data.author().orElse(""), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("mod_description", data.description().map(s -> "'''" + s + "'''").orElse(""), gradlePropertiesFile);
            updateProgress(6, 17);
            Railroad.LOGGER.info("gradle.properties updated successfully.");
        }

        private void renamePackages(Path oldPath, Path newPath, Path mainJava) throws IOException {
            updateLabel("railroad.project.creation.task.updating_package");
            Files.createDirectories(newPath.getParent());
            Files.move(oldPath, newPath);

            // Delete 'com' directory if it's empty
            final Path comDir = mainJava.resolve("com");
            FileUtils.isDirectoryEmpty(comDir, (ExceptionlessRunnable) () -> FileUtils.deleteFolder(comDir));

            updateProgress(7, 17);
            Railroad.LOGGER.info("Package name updated successfully.");
        }

        private void updateModsToml(Path projectPath) throws IOException {
            updateLabel("railroad.project.creation.task.updating_mods_toml");
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
            updateLabel("railroad.project.creation.task.refactoring_classes");
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
            updateLabel("railroad.project.creation.task.updating_build_gradle");
            Path buildGradle = projectPath.resolve("build.gradle");
            String templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(data.minecraftVersion().id().substring(2));
            if (UrlUtils.is404(templateBuildGradleUrl)) {
                templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(data.minecraftVersion().id().split("\\.")[1]);
            }

            if (UrlUtils.is404(templateBuildGradleUrl)) {
                throw new RuntimeException("No build.gradle template found for the specified Minecraft version.");
            }

            FileUtils.copyUrlToFile(templateBuildGradleUrl, buildGradle);
            String buildGradleContent = Files.readString(buildGradle);
            if (!buildGradleContent.startsWith("// fileName:")) {
                throw new RuntimeException("build.gradle template is invalid.");
            }

            int newLineIndex = buildGradleContent.indexOf("\n");

            var binding = new Binding(args);
            binding.setVariable("defaultName", projectPath.relativize(buildGradle.toAbsolutePath()).toString());

            Object result = shell.parse(buildGradleContent.substring("// fileName:".length() + 1, newLineIndex), binding).run();
            if (result == null) {
                throw new RuntimeException("build.gradle template is invalid.");
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
            updateLabel("railroad.project.creation.task.updating_settings_gradle");
            Path settingsGradle = projectPath.resolve("settings.gradle");
            String templateSettingsGradleUrl = TEMPLATE_SETTINGS_GRADLE_URL.formatted(data.minecraftVersion().id().substring(2));
            if (UrlUtils.is404(templateSettingsGradleUrl)) {
                templateSettingsGradleUrl = TEMPLATE_SETTINGS_GRADLE_URL.formatted(data.minecraftVersion().id().split("\\.")[1]);
            }

            if (UrlUtils.is404(templateSettingsGradleUrl)) {
                throw new RuntimeException("No settings.gradle template found for the specified Minecraft version.");
            }

            FileUtils.copyUrlToFile(templateSettingsGradleUrl, settingsGradle);
            String settingsGradleContent = Files.readString(settingsGradle);
            if (!settingsGradleContent.startsWith("// fileName:")) {
                throw new RuntimeException("settings.gradle template is invalid.");
            }

            int newLineIndex = settingsGradleContent.indexOf("\n");

            var binding = new Binding(args);
            binding.setVariable("defaultName", projectPath.relativize(settingsGradle.toAbsolutePath()).toString());

            Object result = shell.parse(settingsGradleContent.substring("// fileName:".length() + 1, newLineIndex), binding).run();
            if (result == null) {
                throw new RuntimeException("settings.gradle template is invalid.");
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
                updateLabel("railroad.project.creation.task.creating_mixins");
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
                updateLabel("railroad.project.creation.task.creating_access_transformer");
                Path accessTransformer = projectPath.resolve("src/main/resources/META-INF/accesstransformer.cfg");
                Files.createFile(accessTransformer);
                updateProgress(13, 17);
                Railroad.LOGGER.info("accesstransformer.cfg created successfully.");
            }
        }

        private void setupGradle(Path projectPath) {
            updateLabel("railroad.project.creation.task.setup_gradle");
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
                throw new RuntimeException("Failed to run Gradle tasks: " + exception.getMessage(), exception);
            }
        }

        private void createGitRepository(Path projectPath) {
            // Create git repository
            if (data.createGit()) {
                updateLabel("railroad.project.creation.task.creating_git");
                try {
                    var processBuilder = new ProcessBuilder("git", "init");
                    processBuilder.directory(projectPath.toFile());
                    var process = processBuilder.start();
                    process.waitFor();

                    updateProgress(16, 17);
                    Railroad.LOGGER.info("Git repository created successfully.");
                } catch (IOException | InterruptedException exception) {
                    throw new RuntimeException("Failed to create git repository: " + exception.getMessage(), exception);
                }
            }
        }
    }
}
