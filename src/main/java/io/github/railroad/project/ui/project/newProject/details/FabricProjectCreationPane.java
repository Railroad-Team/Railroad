package io.github.railroad.project.ui.project.newProject.details;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.palexdev.materialfx.controls.MFXProgressBar;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import io.github.railroad.Railroad;
import io.github.railroad.minecraft.FabricAPIVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.minecraft.mapping.MappingChannel;
import io.github.railroad.project.Project;
import io.github.railroad.project.data.FabricProjectData;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.ExceptionlessRunnable;
import io.github.railroad.utility.FileHandler;
import javafx.application.Platform;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class FabricProjectCreationPane extends RRBorderPane {
    private static final String EXAMPLE_MOD_URL = "https://github.com/FabricMC/fabric-example-mod/archive/refs/heads/%s.zip";

    private final FabricProjectData data;
    private final MFXProgressSpinner progressSpinner = new MFXProgressSpinner();
    private final Label progressPercentageLabel = new Label("0%");
    private final MFXProgressBar progressBar = new MFXProgressBar();
    private final Label progressLabel = new Label("");
    private final Label taskLabel = new Label();
    private final Label errorLabel = new Label();

    private final ListProperty<Throwable> errors = new SimpleListProperty<>(FXCollections.observableArrayList());

    public FabricProjectCreationPane(FabricProjectData data) {
        this.data = data;

        progressSpinner.progressProperty().bind(progressBar.progressProperty());
        progressSpinner.setRadius(50);
        progressPercentageLabel.textProperty().bind(progressBar.progressProperty().multiply(100).asString("%.0f%%"));
        setCenter(progressSpinner);

        var progressBox = new RRVBox(10);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.getChildren().addAll(progressPercentageLabel, progressBar, progressLabel, taskLabel);
        setBottom(progressBox);

        var errorIcon = new FontIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
        errorIcon.setIconSize(24);
        errorIcon.setIconColor(Color.ORANGERED);
        errorLabel.setGraphic(errorIcon);
        errorLabel.textProperty().bind(errors.sizeProperty().asString().concat(" errors"));
        errorLabel.visibleProperty().bind(errors.sizeProperty().greaterThan(0));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        errors.addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty()) {
                progressBox.getChildren().add(errorLabel);
            } else {
                progressBox.getChildren().remove(errorLabel);
            }
        });

        setTop(new Label("Creating project..."));
        setAlignment(getTop(), Pos.CENTER);
        progressBar.setProgress(0);

        var task = new ProjectCreationTask(data);
        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(event -> {
            // Update UI or perform any final operations
            //progressBar.setProgress(1.0); // Update progress to 100%
        });

        new Thread(task).start();
    }

    private static void showErrorAlert(String title, String header, String content) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Handle OK button action if needed
        }
    }

    private static class ProjectCreationTask extends Task<Void> {
        private final FabricProjectData data;

        public ProjectCreationTask(FabricProjectData data) {
            this.data = data;
        }

        @Override
        protected Void call() throws Exception {
            try {
                Path projectPath = data.projectPath().resolve(data.projectName());

                MinecraftVersion version = data.minecraftVersion();

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

                String minecraftId = mdkVersion.id();
                if (mdkVersion.id().split("\\.").length > 2) {
                    minecraftId = mdkVersion.id().substring(0, mdkVersion.id().lastIndexOf("."));
                }

                String modUrl = String.format(EXAMPLE_MOD_URL, minecraftId);

                Files.createDirectories(projectPath);
                updateProgress(1, 13);
                Thread.sleep(500);
                System.out.println("Project directory created successfully.");

                FileHandler.copyUrlToFile(modUrl, Path.of(projectPath.resolve("example-mod.zip").toString()));
                updateProgress(3, 13);
                Thread.sleep(500);
                System.out.println("Example mod downloaded successfully.");

                FileHandler.unzipFile(Path.of(projectPath.resolve("example-mod.zip").toString()).toString(), projectPath.toString());
                updateProgress(5, 13);
                Thread.sleep(500);
                System.out.println("Example mod extracted successfully.");

                Files.delete(Path.of(projectPath.resolve("example-mod.zip").toString()));
                updateProgress(6, 13);
                Thread.sleep(500);
                System.out.println("Example mod zip deleted successfully.");

                Path folder = projectPath.resolve("fabric-example-mod-" + minecraftId);
                FileHandler.copyFolder(folder, projectPath);
                FileHandler.deleteFolder(folder);
                updateProgress(7, 13);
                Thread.sleep(500);
                System.out.println("Project copied successfully.");

                Path gradlePropertiesFile = projectPath.resolve("gradle.properties");
                FileHandler.updateKeyValuePairByLine("org.gradle.jvmargs", "-Xmx4G", gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("minecraft_version", version.id(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("loader_version", data.fabricLoaderVersion().loaderVersion().version(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("fabric_version", data.fapiVersion().map(FabricAPIVersion::fullVersion).orElse(""), gradlePropertiesFile);
                if (data.mappingChannel() == MappingChannel.YARN) {
                    FileHandler.updateKeyValuePairByLine("yarn_mappings", data.mappingVersion().getId(), gradlePropertiesFile);
                }
                FileHandler.updateKeyValuePairByLine("mod_version", data.version(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("maven_group", data.groupId(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("archives_base_name", data.modId(), gradlePropertiesFile);
                System.out.println("gradle.properties updated successfully.");
                updateProgress(8, 13);
                Thread.sleep(500);

                // rename "com/example" to data.groupId() + "/" + data.modId()
                Path mainJava = projectPath.resolve("src/main/java/");
                Path clientJava = projectPath.resolve("src/client/java/");

                Path oldPath = mainJava.resolve("com/example/");
                String newFolderPath = data.groupId().replace(".", "/") + "/" + data.modId();
                Path newPath = mainJava.resolve(newFolderPath);
                Files.createDirectories(newPath.getParent());
                Files.move(oldPath, newPath);

                // Delete 'com' directory if it's empty
                final Path comDir = mainJava.resolve("com");
                FileHandler.isDirectoryEmpty(comDir, (ExceptionlessRunnable) () -> Files.deleteIfExists(comDir));

                oldPath = clientJava.resolve("com/example/");
                newPath = clientJava.resolve(newFolderPath);
                Files.createDirectories(newPath.getParent());
                Files.move(oldPath, newPath);

                // Delete 'com' directory if it's empty
                final Path comDir2 = clientJava.resolve("com");
                FileHandler.isDirectoryEmpty(comDir, (ExceptionlessRunnable) () -> Files.deleteIfExists(comDir2));

                updateProgress(9, 13);
                Thread.sleep(500);
                System.out.println("Package renamed successfully.");

                Path resources = projectPath.resolve("src/main/resources");
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

                    if(split.length == 0) {
                        authors.add(author);
                    }

                    fabricModJsonObj.add("authors", authors);
                });
                fabricModJsonObj.addProperty("license", data.license().getName());

                JsonObject entrypoints = fabricModJsonObj.getAsJsonObject("entrypoints");
                entrypoints.addProperty("main", data.groupId() + "." + data.modId() + "." + data.mainClass());
                entrypoints.addProperty("client", data.groupId() + "." + data.modId() + "." + data.mainClass() + "Client");

                var mixins = new JsonArray();
                mixins.add(data.modId() + ".mixins.json");
                var clientMixin = new JsonObject();
                clientMixin.addProperty("config", data.modId() + ".client.mixins.json");
                clientMixin.addProperty("environment", "client");
                mixins.add(clientMixin);
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

                Files.writeString(fabricModJson, Railroad.GSON.toJson(fabricModJsonObj));
                updateProgress(10, 13);
                Thread.sleep(500);
                System.out.println("fabric.mod.json updated successfully.");

                Files.move(resources.resolve("modid.mixins.json"), resources.resolve(data.modId() + ".mixins.json"));
                String content = Files.readString(resources.resolve(data.modId() + ".mixins.json"));
                content = content.replace("com.example", data.groupId() + "." + data.modId());
                Files.writeString(resources.resolve(data.modId() + ".mixins.json"), content);

                Path clientResources = projectPath.resolve("src/client/resources");
                Files.move(clientResources.resolve("modid.client.mixins.json"), clientResources.resolve(data.modId() + ".client.mixins.json"));
                content = Files.readString(clientResources.resolve(data.modId() + ".client.mixins.json"));
                content = content.replace("com.example", data.groupId() + "." + data.modId());
                Files.writeString(clientResources.resolve(data.modId() + ".client.mixins.json"), content);
                updateProgress(11, 13);
                Thread.sleep(500);
                System.out.println("Mixins files renamed successfully.");

                // Rename ExampleMod.java and ExampleModClient.java
                Path mainClass = mainJava.resolve(newFolderPath).resolve("ExampleMod.java");
                Files.move(mainClass, mainClass.resolveSibling(data.mainClass() + ".java"));
                String mainClassContent = Files.readString(mainClass.resolveSibling(data.mainClass() + ".java"));
                mainClassContent = mainClassContent.replace("com.example", data.groupId() + "." + data.modId());
                mainClassContent = mainClassContent.replace("ExampleMod", data.mainClass());
                Files.writeString(mainClass.resolveSibling(data.mainClass() + ".java"), mainClassContent);

                mainClass = clientJava.resolve(newFolderPath).resolve("ExampleModClient.java");
                Files.move(mainClass, mainClass.resolveSibling(data.mainClass() + "Client.java"));
                mainClassContent = Files.readString(mainClass.resolveSibling(data.mainClass() + "Client.java"));
                mainClassContent = mainClassContent.replace("com.example", data.groupId() + "." + data.modId());
                mainClassContent = mainClassContent.replace("ExampleMod", data.mainClass());
                Files.writeString(mainClass.resolveSibling(data.mainClass() + "Client.java"), mainClassContent);
                updateProgress(12, 13);
                Thread.sleep(500);
                System.out.println("Main classes renamed successfully.");

                Railroad.PROJECT_MANAGER.newProject(new Project(projectPath, this.data.projectName()));
                updateProgress(13, 13);
                Thread.sleep(500);

                System.out.println("Project created successfully.");
            } catch (IOException exception) {
                // Handle errors
                Platform.runLater(() -> showErrorAlert("Error", "An error occurred while creating the project.", exception.getClass().getSimpleName() + ": " + exception.getMessage()));
                exception.printStackTrace();
            }

            return null;
        }
    }
}
