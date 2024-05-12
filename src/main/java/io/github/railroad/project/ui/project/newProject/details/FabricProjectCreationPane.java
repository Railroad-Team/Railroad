package io.github.railroad.project.ui.project.newProject.details;

import io.github.palexdev.materialfx.controls.MFXProgressBar;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import io.github.railroad.Railroad;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.project.Project;
import io.github.railroad.project.data.FabricProjectData;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRVBox;
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
                        // find version with closest release date
                        MinecraftVersion closest = null;
                        for (MinecraftVersion v : versions) {
                            if (v.type() == MinecraftVersion.VersionType.RELEASE) {
                                if (closest == null || Math.abs(v.releaseTime().toEpochSecond(ZoneOffset.UTC) - releaseDate.toEpochSecond(ZoneOffset.UTC)) < Math.abs(closest.releaseTime().toEpochSecond(ZoneOffset.UTC) - releaseDate.toEpochSecond(ZoneOffset.UTC))) {
                                    closest = v;
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
                updateProgress(1, 10);
                Thread.sleep(500);

                FileHandler.copyUrlToFile(modUrl, Path.of(projectPath.resolve("example-mod.zip").toString()));
                updateProgress(3, 10);
                Thread.sleep(500);

                FileHandler.unzipFile(Path.of(projectPath.resolve("example-mod.zip").toString()).toString(), projectPath.toString());
                updateProgress(5, 10);
                Thread.sleep(500);

                Files.delete(Path.of(projectPath.resolve("example-mod.zip").toString()));
                updateProgress(6, 10);
                Thread.sleep(500);

                Railroad.PROJECT_MANAGER.newProject(new Project(projectPath, this.data.projectName()));
                updateProgress(7, 10);
                Thread.sleep(500);

                Path folder = projectPath.resolve("fabric-example-mod-" + minecraftId);
                FileHandler.copyFolder(folder, projectPath);
                FileHandler.deleteFolder(folder);
                updateProgress(8, 10);
                Thread.sleep(500);

                Path gradlePropertiesFile = projectPath.resolve("gradle.properties");
                FileHandler.updateKeyValuePairByLine("org.gradle.jvmargs", "-Xmx4G", gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("minecraft_version", version.id(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("loader_version", data.fabricVersion().loaderVersion().version(), gradlePropertiesFile);
                // TODO: Mappings and api version
                FileHandler.updateKeyValuePairByLine("mod_version", data.version(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("maven_group", data.groupId(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("archives_base_name", data.modId(), gradlePropertiesFile);
                System.out.println("gradle.properties updated successfully.");
                updateProgress(9, 10);
                Thread.sleep(500);

                Path mainExample = projectPath
                        .resolve("src")
                        .resolve("main")
                        .resolve("java")
                        .resolve("com")
                        .resolve("example");
                Path clientExample = projectPath
                        .resolve("src")
                        .resolve("client")
                        .resolve("java")
                        .resolve("com")
                        .resolve("example");

                Path exampleModClass = mainExample.resolve("ExampleMod.java");
                Path mainMixin = clientExample.resolve("mixin");
                Path clientMixin = clientExample.resolve("mixin");
                Path exampleModClientClass = clientExample.resolve("ExampleModClient.java");

                String groupId = data.groupId().replace(".", "/");
                Path mainGroupPath = mainExample.resolve("../../").resolve(groupId);
                Path clientGroupPath = clientExample.resolve("../../").resolve(groupId);

                Files.createDirectories(mainGroupPath);
                Files.createDirectories(clientGroupPath);

                Files.copy(exampleModClass, mainGroupPath.resolve("ExampleMod.java"));
                Files.copy(mainMixin, mainGroupPath.resolve("mixin"));
                Files.copy(clientMixin, clientGroupPath.resolve("mixin"));
                Files.copy(exampleModClientClass, clientGroupPath.resolve("ExampleModClient.java"));

                FileHandler.deleteFolder(mainExample);
                FileHandler.deleteFolder(clientExample);

                updateProgress(10, 10);
            } catch (IOException exception) {
                // Handle errors
                Platform.runLater(() -> showErrorAlert("Error", "An error occurred while creating the project.", exception.getMessage()));
                exception.printStackTrace();
            }

            return null;
        }
    }
}
