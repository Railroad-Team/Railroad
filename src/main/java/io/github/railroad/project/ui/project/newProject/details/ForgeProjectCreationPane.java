package io.github.railroad.project.ui.project.newProject.details;

import io.github.palexdev.materialfx.controls.MFXProgressBar;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import io.github.railroad.Railroad;
import io.github.railroad.project.Project;
import io.github.railroad.project.data.ForgeProjectData;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.FileHandler;
import javafx.application.Platform;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForgeProjectCreationPane extends RRBorderPane {
    private final ForgeProjectData data;
    private final MFXProgressSpinner progressSpinner = new MFXProgressSpinner();
    private final Label progressPercentageLabel = new Label("0%");
    private final MFXProgressBar progressBar = new MFXProgressBar();
    private final Label progressLabel = new Label("");
    private final Label taskLabel = new Label();
    private final Label errorLabel = new Label();

    private final ListProperty<Throwable> errors = new SimpleListProperty<>(FXCollections.observableArrayList());

    private static class ProjectCreationTask extends Task<Void> {
        private final ForgeProjectData data;

        public ProjectCreationTask(ForgeProjectData data) {
            this.data = data;
        }

        @Override
        protected Void call() throws Exception {
            try {
                String fileName = data.minecraftVersion().id() + "-" + data.forgeVersion().id();
                Path projectPath = data.projectPath().resolve(data.projectName());
                Files.createDirectories(projectPath);
                updateProgress(1, 10);
                Thread.sleep(500);
                FileHandler.copyUrlToFile("https://maven.minecraftforge.net/net/minecraftforge/forge/" + fileName + "/forge-" + fileName + "-mdk.zip",
                        Path.of(projectPath.resolve(fileName) + ".zip"));
                updateProgress(3, 10);
                Thread.sleep(500);
                FileHandler.unzipFile(Path.of(projectPath.resolve(fileName) + ".zip").toString(), projectPath.toString());
                updateProgress(5, 10);
                Thread.sleep(500);
                Railroad.PROJECT_MANAGER.newProject(new Project(projectPath, this.data.projectName()));
                updateProgress(6, 10);
                Thread.sleep(500);
                Path gradlePropertiesFile = projectPath.resolve("gradle.properties");

                FileHandler.updateKeyValuePairByLine("mod_id", this.data.modId(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_name", this.data.modName(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_version", this.data.version(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_group_id", this.data.groupId(), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_authors", this.data.author().orElse(""), gradlePropertiesFile);
                FileHandler.updateKeyValuePairByLine("mod_description", this.data.description().orElse(""), gradlePropertiesFile);
                System.out.println("gradle.properties updated successfully.");
                updateProgress(7, 10);
                Thread.sleep(500);

                Path com = projectPath
                        .resolve("src")
                        .resolve("main")
                        .resolve("java")
                        .resolve("com")
                        .resolve(this.data.author().orElse(this.data.projectName()))
                        .resolve(this.data.modId());
                Files.createDirectories(com);
                Files.copy(projectPath
                                .resolve("src")
                                .resolve("main")
                                .resolve("java")
                                .resolve("com")
                                .resolve("example")
                                .resolve("examplemod")
                                .resolve("Config.java").toAbsolutePath(),
                        com.resolve("Config.java").toAbsolutePath());
                updateProgress(8, 10);
                Thread.sleep(500);
                Files.copy(projectPath
                                .resolve("src")
                                .resolve("main")
                                .resolve("java")
                                .resolve("com")
                                .resolve("example")
                                .resolve("examplemod")
                                .resolve("ExampleMod.java").toAbsolutePath(),
                        com.resolve(this.data.modId() + ".java").toAbsolutePath());
                updateProgress(9, 10);
                Thread.sleep(500);
                updateProgress(10, 10);
            } catch (IOException exception) {
                // Handle errors
                Platform.runLater(() -> Railroad.showErrorAlert("Error", "An error occurred while creating the project.", exception.getMessage()));
            }

            return null;
        }
    }

    public ForgeProjectCreationPane(ForgeProjectData data) {
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
}
