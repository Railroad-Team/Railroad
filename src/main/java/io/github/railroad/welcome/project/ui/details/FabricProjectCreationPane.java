package io.github.railroad.welcome.project.ui.details;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.text.StreamingTemplateEngine;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import io.github.railroad.Railroad;
import io.github.railroad.project.FabricProjectData;
import io.github.railroad.project.Project;
import io.github.railroad.project.minecraft.FabricAPIVersion;
import io.github.railroad.project.minecraft.MinecraftVersion;
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
    private final RRVBox centerBox = new RRVBox(10);
    private final Label timeElapsedLabel = new Label("");
    private final Label taskLabel = new Label();
    private final long startTime = System.currentTimeMillis();
    private final TextArea outputArea = new TextArea();

    public FabricProjectCreationPane(FabricProjectData data) {
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

            // Open project in IDE
        });

        new Thread(task).start(); // TODO: Don't create a thread in the constructor

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

    private static Map<String, Object> createArgs(FabricProjectData data) {
        final Map<String, Object> args = new HashMap<>();
        args.put("mappings", Map.of(
                "channel", data.mappingChannel().getName().toLowerCase(Locale.ROOT),
                "version", data.mappingVersion().getId()
        ));

        args.put("props", Map.of(
                "splitSourceSets", data.splitSources(),
                "includeFabricApi", data.fapiVersion().isPresent(),
                "useAccessWidener", data.useAccessWidener(),
                "modId", data.modId()
        ));

        return args;
    }

    private class ProjectCreationTask extends Task<Void> {
        private final FabricProjectData data;

        public ProjectCreationTask(FabricProjectData data) {
            this.data = data;
        }

        @Override
        protected Void call() {
            try {
                updateLabel("Creating project directory...");
                Path projectPath = data.projectPath().resolve(data.projectName());

                MinecraftVersion version = data.minecraftVersion();
                MinecraftVersion mdkVersion = determineMdkVersion(version);
                if (mdkVersion == null)
                    return null;

                String minecraftId = mdkVersion.id();
                if (mdkVersion.id().split("\\.").length > 2) {
                    minecraftId = mdkVersion.id().substring(0, mdkVersion.id().lastIndexOf("."));
                }

                downloadExampleMod(projectPath, minecraftId);
                updateGradleProperties(projectPath, version);

                Path mainJava = projectPath.resolve("src/main/java/");
                Path clientJava = projectPath.resolve("src/client/java/");
                String newFolderPath = data.groupId().replace(".", "/") + "/" + data.modId();
                renamePackages(projectPath, mainJava, clientJava, newFolderPath);

                Path resources = projectPath.resolve("src/main/resources");
                updateFabricModJson(resources, version);
                renameMixins(projectPath, resources);
                renameMainClasses(newFolderPath, mainJava, clientJava);
                refactorMixins(newFolderPath, mainJava, clientJava);

                if (!updateBuildGradle(projectPath, mdkVersion))
                    return null;

                updateLabel("Creating project...");
                Railroad.PROJECT_MANAGER.newProject(new Project(projectPath, data.projectName()));
                updateProgress(13, 16);
                Railroad.LOGGER.info("Project created successfully.");

                runGenSources(projectPath);
                createGitRepository(projectPath);

                updateProgress(16, 16);
                Railroad.LOGGER.info("Project creation completed successfully.");
                updateLabel("Project created successfully.");
            } catch (Exception exception) {
                // Handle errors
                Platform.runLater(() -> showErrorAlert("Error", "An error occurred while creating the project.", exception.getClass().getSimpleName() + ": " + exception.getMessage()));
                Railroad.LOGGER.error("An error occurred while creating the project.", exception);
            }

            return null;
        }

        public void updateLabel(String text) {
            Platform.runLater(() -> taskLabel.setText(text));
        }

        private MinecraftVersion determineMdkVersion(MinecraftVersion version) {
            MinecraftVersion mdkVersion = version;
            if (version.type() == MinecraftVersion.VersionType.OLD_ALPHA || version.type() == MinecraftVersion.VersionType.OLD_BETA) {
                showErrorAlert("Error", "Unsupported Minecraft version", "Fabric does not support Minecraft versions older than 1.14.");
                return null;
            }

            if (version.type() == MinecraftVersion.VersionType.SNAPSHOT) {
                String id = version.id();
                if (id.contains("pre") || id.contains("rc")) {
                    mdkVersion = MinecraftVersion.fromId(id.substring(0, id.indexOf("-")))
                            .orElse(null);
                    if (mdkVersion == null) {
                        showErrorAlert("Error", "Unsupported Minecraft version", "Fabric does not support Minecraft versions older than 1.14.");
                        return null;
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
                showErrorAlert("Error", "Unsupported Minecraft version", "Fabric does not support Minecraft versions older than 1.14.");
                return null;
            }

            return mdkVersion;
        }

        private void downloadExampleMod(Path projectPath, String minecraftId) throws IOException {
            Files.createDirectories(projectPath);
            updateProgress(1, 16);
            Railroad.LOGGER.info("Project directory created successfully.");

            updateLabel("Downloading example mod...");
            String modUrl = String.format(EXAMPLE_MOD_URL, minecraftId);
            FileHandler.copyUrlToFile(modUrl, Path.of(projectPath.resolve("example-mod.zip").toString()));
            updateProgress(2, 16);
            Railroad.LOGGER.info("Example mod downloaded successfully.");

            updateLabel("Extracting example mod...");
            FileHandler.unzipFile(projectPath.resolve("example-mod.zip"), projectPath);
            updateProgress(3, 16);
            Railroad.LOGGER.info("Example mod extracted successfully.");

            updateLabel("Deleting example mod zip...");
            Files.delete(Path.of(projectPath.resolve("example-mod.zip").toString()));
            updateProgress(4, 16);
            Railroad.LOGGER.info("Example mod zip deleted successfully.");

            updateLabel("Copying project...");
            Path folder = projectPath.resolve("fabric-example-mod-" + minecraftId);
            FileHandler.copyFolder(folder, projectPath);
            FileHandler.deleteFolder(folder);
            updateProgress(5, 16);
            Railroad.LOGGER.info("Project copied successfully.");
        }

        private void updateGradleProperties(Path projectPath, MinecraftVersion version) throws IOException {
            updateLabel("Updating gradle.properties...");
            Path gradlePropertiesFile = projectPath.resolve("gradle.properties");
            FileHandler.updateKeyValuePairByLine("org.gradle.jvmargs", "-Xmx4G", gradlePropertiesFile);
            FileHandler.updateKeyValuePairByLine("minecraft_version", version.id(), gradlePropertiesFile);
            FileHandler.updateKeyValuePairByLine("loader_version", data.fabricLoaderVersion().loaderVersion().version(), gradlePropertiesFile);
            FileHandler.updateKeyValuePairByLine("fabric_version", data.fapiVersion().map(FabricAPIVersion::fullVersion).orElse(""), gradlePropertiesFile);
            if (data.mappingChannel() == MappingChannel.YARN) {
                FileHandler.updateKeyValuePairByLine("yarn_mappings", version.id() + "+" + data.mappingVersion().getId(), gradlePropertiesFile);
            } else if (data.mappingChannel() == MappingChannel.PARCHMENT) {
                Files.writeString(gradlePropertiesFile, "parchment_version=" + data.mappingVersion().getId() + "\n", StandardOpenOption.APPEND);
            }

            FileHandler.updateKeyValuePairByLine("mod_version", data.version(), gradlePropertiesFile);
            FileHandler.updateKeyValuePairByLine("maven_group", data.groupId(), gradlePropertiesFile);
            FileHandler.updateKeyValuePairByLine("archives_base_name", data.modId(), gradlePropertiesFile);

            updateProgress(6, 16);
            Railroad.LOGGER.info("gradle.properties updated successfully.");
        }

        private void renamePackages(Path projectPath, Path mainJava, Path clientJava, String newFolderPath) throws IOException {
            updateLabel("Renaming packages...");
            if (!data.splitSources()) {
                FileHandler.deleteFolder(projectPath.resolve("src/client/"));
            }

            // rename "com/example" to data.groupId() + "/" + data.modId()
            Path newPath = mainJava.resolve(newFolderPath);
            Files.createDirectories(newPath.getParent());

            Path oldPath = mainJava.resolve("com/example/");
            Files.move(oldPath, newPath);

            // Delete 'com' directory if it's empty
            final Path comDir = mainJava.resolve("com");
            FileHandler.isDirectoryEmpty(comDir, (ExceptionlessRunnable) () -> FileHandler.deleteFolder(comDir));

            if (data.splitSources()) {
                oldPath = clientJava.resolve("com/example/");
                newPath = clientJava.resolve(newFolderPath);
                Files.createDirectories(newPath.getParent());
                Files.move(oldPath, newPath);

                // Delete 'com' directory if it's empty
                final Path comDir2 = clientJava.resolve("com");
                FileHandler.isDirectoryEmpty(comDir, (ExceptionlessRunnable) () -> FileHandler.deleteFolder(comDir2));
            }

            updateProgress(7, 16);
            Railroad.LOGGER.info("Package renamed successfully.");
        }

        private void updateFabricModJson(Path resources, MinecraftVersion version) throws IOException {
            updateLabel("Updating fabric.mod.json...");
            FileHandler.deleteFolder(resources.resolve("assets"));

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
            entrypoints.addProperty("main", data.groupId() + "." + data.modId() + "." + data.mainClass());
            if (data.splitSources()) {
                entrypoints.addProperty("client", data.groupId() + "." + data.modId() + "." + data.mainClass() + "Client");
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
            depends.addProperty("java", ">=" + fabricModJsonObj.get("depends").getAsJsonObject().get("java").getAsString());
            data.fapiVersion().ifPresentOrElse(
                    fabricAPIVersion -> depends.addProperty("fabric-api", ">=" + fabricAPIVersion.fullVersion()),
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
            updateLabel("Renaming mixins files...");
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
            updateLabel("Renaming main classes...");
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
            updateLabel("Refactoring mixins...");
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
            updateLabel("Updating build.gradle...");
            // Download template build.gradle
            Path buildGradle = projectPath.resolve("build.gradle");
            String templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(mdkVersion.id().split("\\.")[1]);
            if(FileHandler.is404(templateBuildGradleUrl)) {
                templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(data.minecraftVersion().id().split("\\.")[1]);
            }

            if (FileHandler.is404(templateBuildGradleUrl)) {
                showErrorAlert("Error", "An error occurred while creating the project.", "build.gradle template not found.");
                return false;
            }

            FileHandler.copyUrlToFile(templateBuildGradleUrl, buildGradle);
            String buildGradleContent = Files.readString(buildGradle);
            if (!buildGradleContent.startsWith("// fileName:")) {
                showErrorAlert("Error", "An error occurred while creating the project.", "build.gradle template is invalid.");
                return false;
            }

            int newLineIndex = buildGradleContent.indexOf('\n');

            Map<String, Object> args = createArgs(data);
            var binding = new Binding(args);
            binding.setVariable("defaultName", projectPath.relativize(buildGradle.toAbsolutePath()).toString());

            var shell = new GroovyShell();
            Object result = shell.parse(buildGradleContent.substring("// fileName:".length() + 1, newLineIndex), binding).run();
            if (result == null) {
                showErrorAlert("Error", "An error occurred while creating the project.", "build.gradle template is invalid.");
                return false;
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
            updateLabel("Running genSources task...");
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
                showErrorAlert("Error", "An error occurred while creating the project.", exception.getClass().getSimpleName() + ": " + exception.getMessage());
                Railroad.LOGGER.error("An error occurred while running the genSources task.", exception);
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

                    updateProgress(15, 16);
                    Railroad.LOGGER.info("Git repository created successfully.");
                } catch (IOException | InterruptedException exception) {
                    showErrorAlert("Error", "An error occurred while creating the project.", exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    Railroad.LOGGER.error("An error occurred while creating the git repository.", exception);
                }
            }
        }
    }
}
