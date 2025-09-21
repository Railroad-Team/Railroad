package dev.railroadide.railroad.welcome.project.ui.creation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.railroadide.core.ui.RRBorderPane;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.data.FabricProjectData;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
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
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FabricProjectCreationPane extends RRBorderPane {
    private static final String EXAMPLE_MOD_URL = "https://github.com/FabricMC/fabric-example-mod/archive/refs/heads/%s.zip";
    private static final String TEMPLATE_BUILD_GRADLE_URL = "https://raw.githubusercontent.com/Railroad-Team/Railroad/main/templates/fabric/%s/template_build.gradle";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final RRVBox centerBox = new RRVBox(20);
    private final LocalizedLabel timeElapsedLabel = new LocalizedLabel("railroad.project.creation.status.time_elapsed", "");
    private final LocalizedLabel taskLabel = new LocalizedLabel("railroad.project.creation.status.task", "");
    private final long startTime = System.currentTimeMillis();
    private final TextArea outputArea = new TextArea();
    private final MFXProgressSpinner progressSpinner = new MFXProgressSpinner();
    private final RRButton cancelButton = new RRButton("railroad.generic.cancel");
    private final FabricProjectData data;

    public FabricProjectCreationPane(FabricProjectData data) {
        this.data = data;
        setupUI();
        startProjectCreation();
    }

    private static Map<String, Object> createArgs(FabricProjectData data) {
        final Map<String, Object> args = new HashMap<>();
        args.put("mappings", Map.of(
                "channel", data.mappingChannel().id().toLowerCase(Locale.ROOT),
                "version", data.mappingVersion()
        ));

        args.put("props", Map.of(
                "splitSourceSets", data.splitSources(),
                "includeFabricApi", data.fapiVersion().isPresent(),
                "useAccessWidener", data.useAccessWidener(),
                "modId", data.modId()
        ));

        return args;
    }

    private void setupUI() {
        setPadding(new Insets(24));

        var headerBox = new RRVBox(8);
        headerBox.setAlignment(Pos.CENTER);
        var titleLabel = new LocalizedLabel("railroad.project.creation.status.creating_fabric");
        titleLabel.getStyleClass().add("project-creation-title");
        var subtitleLabel = new LocalizedLabel("railroad.project.creation.status.creating_fabric.subtitle", data.projectName());
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
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Failed to open project in IDE", exception);
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
                    "railroad.project.creation.error.fabric.header",
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
        Platform.runLater(() -> getScene().setRoot(new WelcomePane()));
    }

    private class ProjectCreationTask extends Task<Void> {
        private final FabricProjectData data;

        public ProjectCreationTask(FabricProjectData data) {
            this.data = data;
        }

        @Override
        protected Void call() {
            try {
                updateLabel("railroad.project.creation.task.creating_directory");
                Path projectPath = data.projectPath().resolve(data.projectName());
                Files.createDirectories(projectPath);
                updateProgress(1, 16);
                Railroad.LOGGER.info("Project directory created successfully.");

                MinecraftVersion mdkVersion = determineMdkVersion(data.minecraftVersion());
                if (mdkVersion == null)
                    throw new RuntimeException("Failed to determine MDK version for Minecraft " + data.minecraftVersion().id());

                downloadExampleMod(projectPath, mdkVersion.getMajorVersion());
                updateGradleProperties(projectPath, mdkVersion);

                Path mainJava = projectPath.resolve("src/main/java");
                Path clientJava = projectPath.resolve("src/client/java");
                String newFolderPath = data.groupId().replace(".", "/") + "/" + data.modId();

                renamePackages(projectPath, mainJava, clientJava, newFolderPath);
                updateFabricModJson(projectPath.resolve("src/main/resources"), mdkVersion);
                renameMixins(projectPath, projectPath.resolve("src/main/resources"));
                renameMainClasses(newFolderPath, mainJava, clientJava);
                refactorMixins(newFolderPath, mainJava, clientJava);

                if (!updateBuildGradle(projectPath, mdkVersion))
                    throw new RuntimeException("Failed to update build.gradle");

                updateLabel("railroad.project.creation.task.creating_project");
                Railroad.PROJECT_MANAGER.newProject(new Project(projectPath, data.projectName()));
                updateProgress(13, 16);
                Railroad.LOGGER.info("Project created successfully.");

                runGenSources(projectPath);
                createGitRepository(projectPath);

                updateProgress(16, 16);
                Railroad.LOGGER.info("Project creation completed successfully.");
                updateLabel("railroad.project.creation.task.fabric_completed");
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

        private MinecraftVersion determineMdkVersion(MinecraftVersion version) {
            MinecraftVersion mdkVersion = version;
            if (version.type() == MinecraftVersion.VersionType.OLD_ALPHA || version.type() == MinecraftVersion.VersionType.OLD_BETA) {
                throw new RuntimeException("Fabric does not support Minecraft versions older than 1.14.");
            }

            if (version.type() == MinecraftVersion.VersionType.SNAPSHOT) {
                String id = version.id();
                if (id.contains("pre") || id.contains("rc")) {
                    mdkVersion = MinecraftVersion.fromId(id.substring(0, id.indexOf("-")))
                            .orElse(null);
                    if (mdkVersion == null) {
                        throw new RuntimeException("Fabric does not support Minecraft versions older than 1.14.");
                    }
                } else {
                    LocalDateTime releaseDate = version.releaseTime();

                    List<MinecraftVersion> versions = MinecraftVersion.getVersions();
                    // find version with the closest release date
                    MinecraftVersion closest = null;
                    for (MinecraftVersion mcVersion : versions) {
                        if (mcVersion.type() == MinecraftVersion.VersionType.RELEASE) {
                            long releaseTime = releaseDate.toEpochSecond(ZoneOffset.UTC);
                            if (closest == null ||
                                    Math.abs(mcVersion.releaseTime().toEpochSecond(ZoneOffset.UTC) - releaseTime) <
                                            Math.abs(closest.releaseTime().toEpochSecond(ZoneOffset.UTC) - releaseTime)) {
                                closest = mcVersion;
                            }
                        }
                    }

                    mdkVersion = closest;
                }
            }

            if (mdkVersion == null) {
                throw new RuntimeException("Fabric does not support Minecraft versions older than 1.14.");
            }

            return mdkVersion;
        }

        private void downloadExampleMod(Path projectPath, String minecraftId) throws IOException {
            Files.createDirectories(projectPath);
            updateProgress(1, 16);
            Railroad.LOGGER.info("Project directory created successfully.");

            updateLabel("railroad.project.creation.task.downloading_example_mod");
            String modUrl = String.format(EXAMPLE_MOD_URL, minecraftId);
            FileUtils.copyUrlToFile(modUrl, Path.of(projectPath.resolve("example-mod.zip").toString()));
            updateProgress(2, 16);
            Railroad.LOGGER.info("Example mod downloaded successfully.");

            updateLabel("railroad.project.creation.task.extracting_example_mod");
            FileUtils.unzipFile(projectPath.resolve("example-mod.zip"), projectPath);
            updateProgress(3, 16);
            Railroad.LOGGER.info("Example mod extracted successfully.");

            updateLabel("railroad.project.creation.task.deleting_example_zip");
            Files.delete(Path.of(projectPath.resolve("example-mod.zip").toString()));
            updateProgress(4, 16);
            Railroad.LOGGER.info("Example mod zip deleted successfully.");

            updateLabel("railroad.project.creation.task.copying_project");
            Path folder = projectPath.resolve("fabric-example-mod-" + minecraftId);
            FileUtils.copyFolder(folder, projectPath);
            FileUtils.deleteFolder(folder);
            updateProgress(5, 16);
            Railroad.LOGGER.info("Project copied successfully.");
        }

        private void updateGradleProperties(Path projectPath, MinecraftVersion version) throws IOException {
            updateLabel("railroad.project.creation.task.updating_gradle");
            Path gradlePropertiesFile = projectPath.resolve("gradle.properties");
            FileUtils.updateKeyValuePair("org.gradle.jvmargs", "-Xmx4G", gradlePropertiesFile);
            FileUtils.updateKeyValuePair("minecraft_version", version.id(), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("loader_version", data.fabricLoaderVersion().loaderVersion().version(), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("fabric_version", data.fapiVersion().orElse(""), gradlePropertiesFile);
            if (data.mappingChannel() == MappingChannelRegistry.YARN) {
                FileUtils.updateKeyValuePair("yarn_mappings", version.id() + "+" + data.mappingVersion(), gradlePropertiesFile);
            } else if (data.mappingChannel() == MappingChannelRegistry.PARCHMENT) {
                Files.writeString(gradlePropertiesFile, "parchment_version=" + data.mappingVersion() + "\n", StandardOpenOption.APPEND);
            }

            FileUtils.updateKeyValuePair("mod_version", data.version(), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("maven_group", data.groupId(), gradlePropertiesFile);
            FileUtils.updateKeyValuePair("archives_base_name", data.modId(), gradlePropertiesFile);

            updateProgress(6, 16);
            Railroad.LOGGER.info("gradle.properties updated successfully.");
        }

        private void renamePackages(Path projectPath, Path mainJava, Path clientJava, String newFolderPath) throws IOException {
            updateLabel("railroad.project.creation.task.renaming_packages");
            if (!data.splitSources()) {
                FileUtils.deleteFolder(projectPath.resolve("src/client/"));
            }

            // rename "com/example" to data.groupId() + "/" + data.modId()
            Path newPath = mainJava.resolve(newFolderPath);
            Files.createDirectories(newPath.getParent());

            Path oldPath = mainJava.resolve("com/example/");
            Files.move(oldPath, newPath);

            // Delete 'com' directory if it's empty
            final Path comDir = mainJava.resolve("com");
            FileUtils.isDirectoryEmpty(comDir, (ExceptionlessRunnable) () -> FileUtils.deleteFolder(comDir));

            if (data.splitSources()) {
                oldPath = clientJava.resolve("com/example/");
                newPath = clientJava.resolve(newFolderPath);
                Files.createDirectories(newPath.getParent());
                Files.move(oldPath, newPath);

                // Delete 'com' directory if it's empty
                final Path comDir2 = clientJava.resolve("com");
                FileUtils.isDirectoryEmpty(comDir, (ExceptionlessRunnable) () -> FileUtils.deleteFolder(comDir2));
            }

            updateProgress(7, 16);
            Railroad.LOGGER.info("Package renamed successfully.");
        }

        private void updateFabricModJson(Path resources, MinecraftVersion version) throws IOException {
            updateLabel("railroad.project.creation.task.updating_fabric_mod_json");
            FileUtils.deleteFolder(resources.resolve("assets"));

            // TODO: Change based on schema version
            Path fabricModJson = resources.resolve("fabric.mod.json");
            String fabricModJsonContent = Files.readString(fabricModJson);
            JsonObject fabricModJsonObj = Railroad.GSON.fromJson(fabricModJsonContent, JsonObject.class);
            fabricModJsonObj.addProperty("id", data.modId());
            fabricModJsonObj.addProperty("name", data.modName());
            data.description().ifPresent(description -> fabricModJsonObj.addProperty("description", description));
            data.author().ifPresent(author -> {
                var authors = new JsonArray();
                String[] split = author.split(",");
                for (String s : split) {
                    authors.add(s.trim());
                }

                if (split.length == 0) {
                    authors.add(author);
                }

                fabricModJsonObj.add("authors", authors);
            });
            fabricModJsonObj.addProperty("license", data.license().getName());

            JsonObject entrypoints = fabricModJsonObj.getAsJsonObject("entrypoints");
            var mainEntrypoints = new JsonArray();
            mainEntrypoints.add(data.groupId() + "." + data.modId() + "." + data.mainClass());
            entrypoints.add("main", mainEntrypoints);
            if (data.splitSources()) {
                var clientEntrypoints = new JsonArray();
                clientEntrypoints.add(data.groupId() + "." + data.modId() + "." + data.mainClass() + "Client");
                entrypoints.add("client", clientEntrypoints);
            } else {
                entrypoints.remove("client");
            }

            var mixins = new JsonArray();
            mixins.add(data.modId() + ".mixins.json");
            if (data.splitSources()) {
                var clientMixin = new JsonObject();
                clientMixin.addProperty("config", data.modId() + ".client.mixins.json");
                clientMixin.addProperty("environment", "client");
                mixins.add(clientMixin);
            }
            fabricModJsonObj.add("mixins", mixins);

            var depends = new JsonObject();
            depends.addProperty("fabricloader", ">=" + data.fabricLoaderVersion().loaderVersion().version());
            depends.addProperty("minecraft", "~" + version.id());
            depends.addProperty("java", fabricModJsonObj.get("depends").getAsJsonObject().get("java").getAsString());
            data.fapiVersion().ifPresentOrElse(
                    fabricApiVersion -> depends.addProperty("fabric-api", ">=" + fabricApiVersion),
                    () -> depends.addProperty("fabric-api", "*")
            );
            fabricModJsonObj.add("depends", depends);

            fabricModJsonObj.remove("suggests");
            if (data.useAccessWidener()) {
                fabricModJsonObj.addProperty("accessWidener", data.modId() + ".accesswidener");

                // Create access widener file
                Files.writeString(resources.resolve(data.modId() + ".accesswidener"), "accessWidener v2 named");
            }

            JsonObject contact = fabricModJsonObj.getAsJsonObject("contact");
            data.issues().ifPresent(issues -> contact.addProperty("issues", issues));
            data.homepage().ifPresent(homepage -> contact.addProperty("homepage", homepage));
            data.sources().ifPresent(sources -> contact.addProperty("sources", sources));

            Files.writeString(fabricModJson, Railroad.GSON.toJson(fabricModJsonObj));

            updateProgress(8, 16);
            Railroad.LOGGER.info("fabric.mod.json updated successfully.");
        }

        private void renameMixins(Path projectPath, Path resources) throws IOException {
            updateLabel("railroad.project.creation.task.renaming_mixins");
            Files.move(resources.resolve("modid.mixins.json"), resources.resolve(data.modId() + ".mixins.json"));
            String content = Files.readString(resources.resolve(data.modId() + ".mixins.json"));
            content = content.replace("com.example", data.groupId() + "." + data.modId());
            Files.writeString(resources.resolve(data.modId() + ".mixins.json"), content);

            if (data.splitSources()) {
                Path clientResources = projectPath.resolve("src/client/resources");
                Files.move(clientResources.resolve("modid.client.mixins.json"), clientResources.resolve(data.modId() + ".client.mixins.json"));
                content = Files.readString(clientResources.resolve(data.modId() + ".client.mixins.json"));
                content = content.replace("com.example", data.groupId() + "." + data.modId());
                Files.writeString(clientResources.resolve(data.modId() + ".client.mixins.json"), content);
            }

            updateProgress(9, 16);
            Railroad.LOGGER.info("Mixins files renamed successfully.");
        }

        private void renameMainClasses(String newFolderPath, Path mainJava, Path clientJava) throws IOException {
            updateLabel("railroad.project.creation.task.renaming_main_classes");
            // Rename ExampleMod.java and ExampleModClient.java
            Path mainClass = mainJava.resolve(newFolderPath).resolve("ExampleMod.java");
            Files.move(mainClass, mainClass.resolveSibling(data.mainClass() + ".java"));
            String mainClassContent = Files.readString(mainClass.resolveSibling(data.mainClass() + ".java"));
            mainClassContent = mainClassContent.replace("com.example", data.groupId() + "." + data.modId());
            mainClassContent = mainClassContent.replace("ExampleMod", data.mainClass());
            Files.writeString(mainClass.resolveSibling(data.mainClass() + ".java"), mainClassContent);

            if (data.splitSources()) {
                mainClass = clientJava.resolve(newFolderPath).resolve("ExampleModClient.java");
                Files.move(mainClass, mainClass.resolveSibling(data.mainClass() + "Client.java"));
                mainClassContent = Files.readString(mainClass.resolveSibling(data.mainClass() + "Client.java"));
                mainClassContent = mainClassContent.replace("com.example", data.groupId() + "." + data.modId());
                mainClassContent = mainClassContent.replace("ExampleMod", data.mainClass());
                Files.writeString(mainClass.resolveSibling(data.mainClass() + "Client.java"), mainClassContent);
            }

            updateProgress(10, 16);
            Railroad.LOGGER.info("Main classes renamed successfully.");
        }

        private void refactorMixins(String newFolderPath, Path mainJava, Path clientJava) throws IOException {
            updateLabel("railroad.project.creation.task.refactoring_mixins");
            // Refactor ExampleMixin.java and ExampleClientMixin.java to be in the correct package
            Path mainMixin = mainJava.resolve(newFolderPath).resolve("mixin").resolve("ExampleMixin.java");
            String mainMixinContent = Files.readString(mainMixin);
            mainMixinContent = mainMixinContent.replace("com.example", data.groupId() + "." + data.modId());
            Files.writeString(mainMixin, mainMixinContent);

            if (data.splitSources()) {
                Path clientMixin = clientJava.resolve(newFolderPath).resolve("mixin/client").resolve("ExampleClientMixin.java");
                String clientMixinContent = Files.readString(clientMixin);
                clientMixinContent = clientMixinContent.replace("com.example", data.groupId() + "." + data.modId());
                Files.writeString(clientMixin, clientMixinContent);
            }

            updateProgress(11, 16);
            Railroad.LOGGER.info("Mixins refactored successfully.");
        }

        private boolean updateBuildGradle(Path projectPath, MinecraftVersion mdkVersion) throws IOException, ClassNotFoundException {
            updateLabel("railroad.project.creation.task.updating_build_gradle");
            // Download template build.gradle
            Path buildGradle = projectPath.resolve("build.gradle");
            String templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(mdkVersion.id().split("\\.")[1]);
            if (UrlUtils.is404(templateBuildGradleUrl)) {
                templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(data.minecraftVersion().id().split("\\.")[1]);
            }

            if (UrlUtils.is404(templateBuildGradleUrl)) {
                throw new RuntimeException("build.gradle template not found.");
            }

            FileUtils.copyUrlToFile(templateBuildGradleUrl, buildGradle);
            String buildGradleContent = Files.readString(buildGradle);
            if (!buildGradleContent.startsWith("// fileName:")) {
                throw new RuntimeException("build.gradle template is invalid.");
            }

            int newLineIndex = buildGradleContent.indexOf('\n');

            Map<String, Object> args = createArgs(data);
            var binding = new Binding(args);
            binding.setVariable("defaultName", projectPath.relativize(buildGradle.toAbsolutePath()).toString());

            var shell = new GroovyShell();
            Object result = shell.parse(buildGradleContent.substring("// fileName:".length() + 1, newLineIndex), binding).run();
            if (result == null) {
                throw new RuntimeException("build.gradle template is invalid.");
            }

            var buffer = new StringBuffer();
            var templateEngine = new StreamingTemplateEngine();
            templateEngine.createTemplate(new StringReader(buildGradleContent))
                    .make(args)
                    .writeTo(new StringBufferWriter(buffer));
            Files.writeString(buildGradle, buffer);

            updateProgress(12, 16);
            Railroad.LOGGER.info("build.gradle updated successfully.");
            return true;
        }

        private void runGenSources(Path projectPath) {
            updateLabel("railroad.project.creation.task.running_gen_sources");
            // Run gradlew genSources
            var connector = GradleConnector.newConnector();
            connector.forProjectDirectory(projectPath.toFile());
            try (ProjectConnection connection = connector.connect()) {
                Platform.runLater(() -> centerBox.getChildren().add(outputArea));

                var outputStream = new TextAreaOutputStream(outputArea);
                connection.newBuild()
                        .forTasks("genSources")
                        .setStandardOutput(outputStream)
                        .setStandardError(outputStream)
                        .run();

                updateProgress(14, 16);
                Railroad.LOGGER.info("genSources task completed successfully.");
                Platform.runLater(() -> centerBox.getChildren().remove(outputArea));
            } catch (BuildException exception) {
                throw new RuntimeException("Failed to run genSources task: " + exception.getMessage(), exception);
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

                    updateProgress(15, 16);
                    Railroad.LOGGER.info("Git repository created successfully.");
                } catch (IOException | InterruptedException exception) {
                    throw new RuntimeException("Failed to create git repository: " + exception.getMessage(), exception);
                }
            }
        }
    }
}
