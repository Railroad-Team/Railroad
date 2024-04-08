package io.github.railroad.project.ui.project.newProject.details;

import io.github.railroad.minecraft.ForgeVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.minecraft.RecommendableVersion;
import io.github.railroad.minecraft.mapping.MappingChannel;
import io.github.railroad.minecraft.mapping.MappingHelper;
import io.github.railroad.minecraft.mapping.MappingVersion;
import io.github.railroad.project.License;
import io.github.railroad.project.ProjectType;
import io.github.railroad.project.data.ForgeProjectData;
import io.github.railroad.project.ui.BrowseButton;
import io.github.railroad.utility.ClassNameValidator;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

public class ForgeProjectDetailsPane extends VBox {
    private final TextField projectNameField = new TextField();
    private final TextField projectPathField = new TextField();
    private final CheckBox createGitCheckBox = new CheckBox();
    private final ComboBox<License> licenseComboBox = new ComboBox<>();
    private final TextField licenseCustomField = new TextField();

    private final ComboBox<MinecraftVersion> minecraftVersionComboBox = new ComboBox<>();
    private final ComboBox<ForgeVersion> forgeVersionComboBox = new ComboBox<>();
    private final TextField modIdField = new TextField();
    private final TextField modNameField = new TextField();
    private final TextField mainClassField = new TextField();
    private final CheckBox useMixinsCheckBox = new CheckBox();
    private final CheckBox useAccessTransformerCheckBox = new CheckBox();

    private final ComboBox<MappingChannel> mappingChannelComboBox = new ComboBox<>();
    private final ComboBox<MappingVersion> mappingVersionComboBox = new ComboBox<>();

    private final TextField authorField = new TextField(System.getProperty("user.name")); // optional
    private final TextArea descriptionArea = new TextArea(); // optional
    private final TextField issuesField = new TextField(); // optional
    private final TextField updateJsonUrlField = new TextField(); // optional

    private final TextField groupIdField = new TextField();
    private final TextField artifactIdField = new TextField();
    private final TextField versionField = new TextField();

    private final AtomicBoolean hasOneDriveWarning = new AtomicBoolean(false);
    private final AtomicBoolean hasModidWarning = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModid = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInMainClass = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInArtifactId = new AtomicBoolean(false);

    public ForgeProjectDetailsPane() {
        // Project Section
        var projectSection = new VBox(10);

        var projectNameBox = new HBox(10);
        projectNameBox.setAlignment(Pos.CENTER_LEFT);
        var projectNameLabel = new Label("Name:");
        projectNameLabel.setLabelFor(projectNameField);
        projectNameBox.getChildren().addAll(projectNameLabel, projectNameField);

        var projectPathVBox = new VBox(10);
        projectPathVBox.setAlignment(Pos.CENTER_LEFT);

        var createdAtLabel = new Label("This will be created at: " + System.getProperty("user.home"));
        createdAtLabel.setGraphic(new FontIcon(FontAwesomeSolid.INFO_CIRCLE));
        createdAtLabel.setTooltip(new Tooltip("The project will be created in this directory."));
        createdAtLabel.setTextFill(Color.SLATEGRAY);

        var projectPathBox = new HBox(10);
        projectPathBox.setAlignment(Pos.CENTER_LEFT);
        var projectPathLabel = new Label("Location:");
        projectPathLabel.setLabelFor(projectPathField);
        projectPathField.setPrefWidth(300);
        projectPathField.setText(System.getProperty("user.home"));
        projectPathField.setEditable(false);
        Border projectPathFieldBorder = projectPathField.getBorder();
        projectPathField.textProperty().addListener((observable, oldValue, newValue) -> {
            // Validate the project path
            Path path = Path.of(newValue);
            if (Files.notExists(path) || !Files.isDirectory(path))
                projectPathField.setStyle("-fx-border-color: red;");
            else
                projectPathField.setBorder(projectPathFieldBorder);

            // Update the created at label
            String fullPath = fixPath(projectPathField.getText().trim() + "/" + projectNameField.getText().trim());
            createdAtLabel.setText("This will be created at: " + fullPath);

            // If the project is in OneDrive, warn the user
            if (fullPath.contains("OneDrive") && hasOneDriveWarning.compareAndSet(false, true)) {
                projectPathField.setStyle("-fx-border-color: orange;");

                var tooltip = new Tooltip("It is not recommended to create projects in OneDrive as it has a tendency to cause problems.");
                Tooltip.install(projectPathField, tooltip);

                var warningIcon = new FontIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
                warningIcon.setIconSize(16);
                warningIcon.setIconColor(Color.ORANGE);
                projectPathBox.getChildren().add(warningIcon);
            } else if (!fullPath.contains("OneDrive") && hasOneDriveWarning.compareAndSet(true, false)) {
                projectPathField.setBorder(projectPathFieldBorder);

                Tooltip.uninstall(projectPathField, projectPathField.getTooltip());

                projectPathBox.getChildren().removeLast();
            } else if (fullPath.contains("OneDrive")) {
                projectPathField.setStyle("-fx-border-color: orange;");
            } else {
                projectPathField.setBorder(projectPathFieldBorder);
            }
        });

        var browseButtonIcon = new FontIcon(FontAwesomeSolid.FOLDER_OPEN);
        browseButtonIcon.setIconSize(16);
        browseButtonIcon.setIconColor(Color.CADETBLUE);
        var browseButton = new BrowseButton();
        browseButton.parentWindowProperty().bind(sceneProperty().map(Scene::getWindow));
        browseButton.textFieldProperty().set(projectPathField);
        browseButton.browseTypeProperty().set(BrowseButton.BrowseType.DIRECTORY);
        browseButton.setGraphic(browseButtonIcon);
        browseButton.setTooltip(new Tooltip("Browse"));
        projectPathBox.getChildren().addAll(projectPathLabel, projectPathField, browseButton);

        projectPathVBox.getChildren().addAll(projectPathBox, createdAtLabel);

        var gitBox = new HBox(10);
        gitBox.setAlignment(Pos.CENTER_LEFT);
        var createGitLabel = new Label("Create Git Repository:");
        createGitLabel.setLabelFor(createGitCheckBox);
        gitBox.getChildren().addAll(createGitLabel, createGitCheckBox);

        var licenseVBox = new VBox(10);
        licenseVBox.setAlignment(Pos.CENTER_LEFT);
        var licenseBox = new HBox(10);
        licenseBox.setAlignment(Pos.CENTER_LEFT);
        var licenseLabel = new Label("License:");
        licenseLabel.setLabelFor(licenseComboBox);
        licenseComboBox.getItems().addAll(License.values());
        licenseComboBox.setValue(License.MIT);
        licenseComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(License object) {
                return object.getName();
            }

            @Override
            public License fromString(String string) {
                return License.fromName(string);
            }
        });
        licenseComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == License.CUSTOM) {
                licenseVBox.getChildren().add(licenseCustomField);
            } else {
                licenseVBox.getChildren().remove(licenseCustomField);
            }
        });
        licenseBox.getChildren().addAll(licenseLabel, licenseComboBox);
        licenseVBox.getChildren().add(licenseBox);

        projectSection.getChildren().addAll(projectNameBox, projectPathVBox, gitBox, licenseVBox);

        // Minecraft Section
        var minecraftSection = new VBox(10);
        minecraftSection.setAlignment(Pos.CENTER_LEFT);

        var minecraftVersionBox = new HBox(10);
        minecraftVersionBox.setAlignment(Pos.CENTER_LEFT);
        var minecraftVersionLabel = new Label("Minecraft Version:");
        minecraftVersionLabel.setLabelFor(minecraftVersionComboBox);
        minecraftVersionComboBox.getItems().addAll(MinecraftVersion.getSupportedVersions(ProjectType.FORGE));
        minecraftVersionComboBox.setValue(MinecraftVersion.getLatestStableVersion());
        minecraftVersionComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(MinecraftVersion object) {
                return object.id();
            }

            @Override
            public MinecraftVersion fromString(String string) {
                return MinecraftVersion.fromId(string).orElse(null);
            }
        });
        minecraftVersionBox.getChildren().addAll(minecraftVersionLabel, minecraftVersionComboBox);

        var forgeVersionBox = new HBox(10);
        forgeVersionBox.setAlignment(Pos.CENTER_LEFT);
        var forgeVersionLabel = new Label("Forge Version:");
        forgeVersionLabel.setLabelFor(forgeVersionComboBox);
        minecraftVersionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            forgeVersionComboBox.getItems().setAll(ForgeVersion.getVersions(newValue));
            forgeVersionComboBox.setValue(ForgeVersion.getLatestVersion(newValue));

            MappingHelper.loadMappings(mappingChannelComboBox.getItems(), newValue);
            mappingChannelComboBox.setValue(mappingChannelComboBox.getItems().getFirst());
        });
        forgeVersionComboBox.setCellFactory(param -> new StarableListCell<>(
                version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                version -> Objects.equals(version.id(), ForgeVersion.getLatestVersion(minecraftVersionComboBox.getValue()).id()),
                ForgeVersion::id));
        forgeVersionComboBox.setButtonCell(new StarableListCell<>(
                version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                version -> Objects.equals(version.id(), ForgeVersion.getLatestVersion(minecraftVersionComboBox.getValue()).id()),
                ForgeVersion::id));
        forgeVersionComboBox.getItems().addAll(ForgeVersion.getVersions(MinecraftVersion.getLatestStableVersion()));
        forgeVersionComboBox.setValue(ForgeVersion.getLatestVersion(MinecraftVersion.getLatestStableVersion()));
        forgeVersionBox.getChildren().addAll(forgeVersionLabel, forgeVersionComboBox);

        var modIdBox = new HBox(10);
        modIdBox.setAlignment(Pos.CENTER_LEFT);
        var modIdLabel = new Label("Mod ID:");
        modIdLabel.setLabelFor(modIdField);
        Border modidFieldBorder = modIdField.getBorder();
        modIdField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue.equals(newValue))
                return;

            if (!newValue.matches("[a-z][a-z0-9_]")) {
                modIdField.setText(newValue = newValue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", ""));
            }

            // Only allow a maximum of 64 characters
            if (newValue.length() > 64) {
                modIdField.setText(newValue = newValue.substring(0, 64));
            }

            // Validate the mod ID
            if (newValue.length() < 3 || !newValue.matches("^[a-z][a-z0-9_]{1,63}$"))
                modIdField.setStyle("-fx-border-color: red;");
            else
                modIdField.setBorder(modidFieldBorder);

            // If the mod ID is not valid, show a warning
            if ((newValue.length() < 5 && newValue.length() > 2) && hasModidWarning.compareAndSet(false, true)) {
                modIdField.setStyle("-fx-border-color: orange;");

                var tooltip = new Tooltip("Short mod IDs are discouraged as they may conflict with other mods.");
                Tooltip.install(modIdField, tooltip);

                var warningIcon = new FontIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
                warningIcon.setIconSize(16);
                warningIcon.setIconColor(Color.ORANGE);
                modIdBox.getChildren().add(warningIcon);
            } else if (newValue.length() >= 5 && hasModidWarning.compareAndSet(true, false)) {
                modIdField.setBorder(modidFieldBorder);

                Tooltip.uninstall(modIdField, modIdField.getTooltip());
                modIdBox.getChildren().removeLast();
            } else if (newValue.length() < 3 && hasModidWarning.compareAndSet(true, false)) {
                Tooltip.uninstall(modIdField, modIdField.getTooltip());
                modIdBox.getChildren().removeLast();
            }

            // update the artifact ID field if it is empty
            if (!hasTypedInArtifactId.get() || artifactIdField.getText().isBlank())
                artifactIdField.setText(newValue.replaceAll("[^a-z0-9-]", ""));
        });
        modIdField.setOnKeyTyped(event -> {
            // If the user has typed in the mod ID and it is not empty, set the hasTypedInModid flag to true
            if (!hasTypedInModid.get() && !modIdField.getText().isBlank())
                hasTypedInModid.set(true);
            else if (hasTypedInModid.get() && modIdField.getText().isBlank())
                hasTypedInModid.set(false);
        });
        modIdBox.getChildren().addAll(modIdLabel, modIdField);

        var modNameBox = new HBox(10);
        modNameBox.setAlignment(Pos.CENTER_LEFT);
        var modNameLabel = new Label("Mod Name:");
        modNameLabel.setLabelFor(modNameField);
        modNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 256) {
                modNameField.setText(newValue.substring(0, 256));
            }
        });
        modNameField.setOnKeyTyped(event -> {
            // If the user has typed in the mod name and it is not empty, set the hasTypedInModName flag to true
            if (!hasTypedInModName.get() && !modNameField.getText().isBlank())
                hasTypedInModName.set(true);
            else if (hasTypedInModName.get() && modNameField.getText().isBlank())
                hasTypedInModName.set(false);
        });
        modNameBox.getChildren().addAll(modNameLabel, modNameField);

        var mainClassBox = new HBox(10);
        mainClassBox.setAlignment(Pos.CENTER_LEFT);
        var mainClassLabel = new Label("Main Class:");
        mainClassLabel.setLabelFor(mainClassField);
        mainClassField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!ClassNameValidator.isValid(newValue)) {
                mainClassField.setText(oldValue);
            }
        });
        mainClassField.setOnKeyTyped(event -> {
            // If the user has typed in the main class and it is not empty, set the hasTypedInMainClass flag to true
            if (!hasTypedInMainClass.get() && !mainClassField.getText().isBlank())
                hasTypedInMainClass.set(true);
            else if (hasTypedInMainClass.get() && mainClassField.getText().isBlank())
                hasTypedInMainClass.set(false);
        });
        mainClassBox.getChildren().addAll(mainClassLabel, mainClassField);

        var useMixinsBox = new HBox(10);
        useMixinsBox.setAlignment(Pos.CENTER_LEFT);
        var useMixinsLabel = new Label("Use Mixins:");
        useMixinsLabel.setLabelFor(useMixinsCheckBox);
        useMixinsBox.getChildren().addAll(useMixinsLabel, useMixinsCheckBox);

        var useAccessTransformerBox = new HBox(10);
        useAccessTransformerBox.setAlignment(Pos.CENTER_LEFT);
        var useAccessTransformerLabel = new Label("Use Access Transformer:");
        useAccessTransformerLabel.setLabelFor(useAccessTransformerCheckBox);
        useAccessTransformerBox.getChildren().addAll(useAccessTransformerLabel, useAccessTransformerCheckBox);

        minecraftSection.getChildren().addAll(minecraftVersionBox, forgeVersionBox,
                modIdBox, modNameBox, mainClassBox, useMixinsBox, useAccessTransformerBox);

        // Mapping Section
        var mappingSection = new VBox(10);
        mappingSection.setAlignment(Pos.CENTER_LEFT);

        var mappingChannelBox = new HBox(10);
        mappingChannelBox.setAlignment(Pos.CENTER_LEFT);
        var mappingChannelLabel = new Label("Mapping Channel:");
        mappingChannelLabel.setLabelFor(mappingChannelComboBox);
        MappingHelper.loadMappings(mappingChannelComboBox.getItems(), minecraftVersionComboBox.getValue());
        mappingChannelComboBox.setValue(MappingChannel.MOJMAP);
        mappingChannelComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(MappingChannel object) {
                return object == null ? "" : object.getName();
            }

            @Override
            public MappingChannel fromString(String string) {
                return MappingChannel.valueOf(string.toUpperCase(Locale.ROOT));
            }
        });
        mappingChannelComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null)
                return;

            MappingHelper.loadMappingsVersions(mappingVersionComboBox.getItems(), minecraftVersionComboBox.getValue(), newValue);
            mappingVersionComboBox.setValue(mappingVersionComboBox.getItems().getFirst());
        });
        mappingChannelBox.getChildren().addAll(mappingChannelLabel, mappingChannelComboBox);

        var mappingVersionBox = new HBox(10);
        mappingVersionBox.setAlignment(Pos.CENTER_LEFT);
        var mappingVersionLabel = new Label("Mapping Version:");
        mappingVersionLabel.setLabelFor(mappingVersionComboBox);
        mappingVersionComboBox.setCellFactory(param -> new StarableListCell<>(
                version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                MappingVersion::isLatest,
                MappingVersion::getId));
        mappingVersionComboBox.setButtonCell(new StarableListCell<>(
                version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                MappingVersion::isLatest,
                MappingVersion::getId));
        MappingHelper.loadMappingsVersions(mappingVersionComboBox.getItems(), minecraftVersionComboBox.getValue(), mappingChannelComboBox.getValue());
        mappingVersionComboBox.setValue(mappingVersionComboBox.getItems().getFirst());
        mappingVersionBox.getChildren().addAll(mappingVersionLabel, mappingVersionComboBox);

        mappingSection.getChildren().addAll(mappingChannelBox, mappingVersionBox);

        // Optional Section
        var optionalSection = new VBox(10);
        optionalSection.setAlignment(Pos.CENTER_LEFT);

        var authorBox = new HBox(10);
        authorBox.setAlignment(Pos.CENTER_LEFT);
        var authorLabel = new Label("Author:");
        authorLabel.setLabelFor(authorField);
        authorField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 256) {
                authorField.setText(newValue.substring(0, 256));
            }
        });
        authorBox.getChildren().addAll(authorLabel, authorField);

        var descriptionBox = new HBox(10);
        descriptionBox.setAlignment(Pos.CENTER_LEFT);
        var descriptionLabel = new Label("Description:");
        descriptionLabel.setLabelFor(descriptionArea);
        descriptionArea.setPrefHeight(100);
        descriptionArea.setWrapText(true);
        descriptionArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 1028) {
                descriptionArea.setText(newValue.substring(0, 1028));
            }
        });
        descriptionBox.getChildren().addAll(descriptionLabel, descriptionArea);

        var issuesBox = new HBox(10);
        issuesBox.setAlignment(Pos.CENTER_LEFT);
        var issuesLabel = new Label("Issues:");
        issuesLabel.setLabelFor(issuesField);
        issuesBox.getChildren().addAll(issuesLabel, issuesField);

        var updateJsonUrlBox = new HBox(10);
        updateJsonUrlBox.setAlignment(Pos.CENTER_LEFT);
        var updateJsonUrlLabel = new Label("Update JSON URL:");
        updateJsonUrlLabel.setLabelFor(updateJsonUrlField);
        updateJsonUrlBox.getChildren().addAll(updateJsonUrlLabel, updateJsonUrlField);

        optionalSection.getChildren().addAll(authorBox, descriptionBox, issuesBox, updateJsonUrlBox);

        // Maven Section
        var mavenSection = new VBox(10);
        mavenSection.setAlignment(Pos.CENTER_LEFT);

        var groupIdBox = new HBox(10);
        groupIdBox.setAlignment(Pos.CENTER_LEFT);
        var groupIdLabel = new Label("Group ID:");
        groupIdLabel.setLabelFor(groupIdField);
        groupIdBox.getChildren().addAll(groupIdLabel, groupIdField);

        var artifactIdBox = new HBox(10);
        artifactIdBox.setAlignment(Pos.CENTER_LEFT);
        var artifactIdLabel = new Label("Artifact ID:");
        artifactIdLabel.setLabelFor(artifactIdField);
        artifactIdBox.getChildren().addAll(artifactIdLabel, artifactIdField);

        var versionBox = new HBox(10);
        versionBox.setAlignment(Pos.CENTER_LEFT);
        var versionLabel = new Label("Version:");
        versionLabel.setLabelFor(versionField);
        versionBox.getChildren().addAll(versionLabel, versionField);

        mavenSection.getChildren().addAll(groupIdBox, artifactIdBox, versionBox);

        getChildren().addAll(projectSection,
                new Separator(), minecraftSection,
                new Separator(), mappingSection,
                new Separator(), optionalSection,
                new Separator(), mavenSection);
        setSpacing(20);
        setPadding(new Insets(10));

        Border projectNameFieldBorder = projectNameField.getBorder();
        projectNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            // Remove any .<>:"/\|?* characters from the project name
            newValue = newValue.replaceAll("[.<>:\"/\\\\|?*]", "");

            // Check that the name does not exceed 256 characters
            if (newValue.length() > 256)
                projectNameField.setText(newValue.substring(0, 256));

            // Validate the project name
            if (newValue.isBlank())
                projectNameField.setStyle("-fx-border-color: red;");
            else
                projectNameField.setBorder(projectNameFieldBorder);

            // Update the created at label
            String path = fixPath(projectPathField.getText().trim() + "/" + projectNameField.getText().trim());
            createdAtLabel.setText("This will be created at: " + path);

            // Update the mod ID field if it is empty
            if (!hasTypedInModid.get() || modIdField.getText().isBlank())
                modIdField.setText(newValue.toLowerCase(Locale.ROOT).replace(" ", "_").replaceAll("[^a-z0-9_-]", ""));

            // Update the mod name field if it is empty
            if (!hasTypedInModName.get() || modNameField.getText().isBlank())
                modNameField.setText(newValue);

            // Update the main class field if it is empty
            if (!hasTypedInMainClass.get() || mainClassField.getText().isBlank()) {
                // convert to pascal case
                String[] words = newValue.split("[ _-]+");
                var pascalCase = new StringBuilder();
                for (String word : words) {
                    pascalCase.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
                }

                mainClassField.setText(pascalCase.toString().replaceAll("[^a-zA-Z0-9]", ""));
            }

            // Update the artifact ID field if it is empty
            if (!hasTypedInArtifactId.get() || artifactIdField.getText().isBlank())
                artifactIdField.setText(newValue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", ""));
        });

        var createButton = new Button("Create");
        createButton.setAlignment(Pos.CENTER_RIGHT);
        getChildren().add(createButton);

//        createButton.disableProperty().bind(
//                isInvalid(projectNameField)
//                        .or(isInvalid(projectPathField))
//                        .or(isInvalid(modIdField))
//                        .or(isInvalid(modNameField))
//                        .or(isInvalid(mainClassField))
//                        .or(isInvalid(groupIdField))
//                        .or(isInvalid(artifactIdField))
//                        .or(isInvalid(versionField))
//                        .or(isInvalid(minecraftVersionComboBox))
//                        .or(isInvalid(forgeVersionComboBox))
//                        .or(isInvalid(mappingChannelComboBox))
//                        .or(isInvalid(mappingVersionComboBox))
//                        .or(isInvalid(licenseComboBox)
//                                .or(licenseComboBox.valueProperty().isEqualTo(License.CUSTOM)
//                                        .and(licenseCustomField.textProperty().isEmpty()))));

        createButton.setOnAction(event -> {
            if (validate()) {
                Scene scene = getScene();

                ForgeProjectData data = createData();
                scene.setRoot(new ForgeProjectCreationPane(data));
                event.consume();
            }
        });
    }

    private static BooleanBinding isInvalid(TextField textField) {
        return textField.textProperty().isEmpty().or(textField.styleProperty().isEqualTo("-fx-border-color: red;"));
    }

    private static BooleanBinding isInvalid(ComboBox<?> comboBox) {
        return comboBox.valueProperty().isNull().or(comboBox.styleProperty().isEqualTo("-fx-border-color: red;"));
    }

    private static void createAlert(String header, String content) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static String fixPath(String path) {
        while (path.endsWith(" "))
            path = path.substring(0, path.length() - 1);

        path = path.replace("/", "\\");

        // Remove trailing backslashes
        while (path.endsWith("\\"))
            path = path.substring(0, path.length() - 1);

        // remove any whitespace before a backslash
        path = path.replaceAll("\\s+\\\\", "\\");

        // remove any whitespace after a backslash
        path = path.replaceAll("\\\\\\\\s+", "\\\\");

        // remove any double backslashes
        path = path.replaceAll("\\\\\\\\", "\\\\");

        // remove any trailing whitespace
        path = path.trim();

        return path;
    }

    protected boolean validate() {
        // Validate the project name
        if (projectNameField.getText().isBlank()) {
            projectNameField.setStyle("-fx-border-color: red;");
            projectNameField.requestFocus();

            createAlert("Project Name is Required", "Please enter a name for your project.");
            return false;
        }

        // Validate the project path
        if (projectPathField.getText().isBlank()) {
            projectPathField.setStyle("-fx-border-color: red;");
            projectPathField.requestFocus();

            createAlert("Project Path is Required", "Please enter a path for your project.");
            return false;
        }

        Path path = Path.of(projectPathField.getText());
        if (Files.notExists(path)) {
            projectPathField.setStyle("-fx-border-color: red;");
            projectPathField.requestFocus();

            createAlert("Invalid Project Path", "The specified path does not exist.");
            return false;
        }

        if (!Files.isDirectory(path)) {
            projectPathField.setStyle("-fx-border-color: red;");
            projectPathField.requestFocus();

            createAlert("Invalid Project Path", "The specified path is not a directory.");
            return false;
        }

        // Validate the mod ID
        if (modIdField.getText().isBlank()) {
            modIdField.setStyle("-fx-border-color: red;");
            modIdField.requestFocus();

            createAlert("Mod ID is Required", "Please enter a mod ID for your project.");
            return false;
        }

        if (modIdField.getText().length() < 2) {
            modIdField.setStyle("-fx-border-color: red;");
            modIdField.requestFocus();

            createAlert("Invalid Mod ID", "The mod ID must be at least 2 characters long.");
            return false;
        }

        if (!modIdField.getText().matches("^[a-z][a-z0-9_]{1,63}$")) {
            modIdField.setStyle("-fx-border-color: red;");
            modIdField.requestFocus();

            createAlert("Invalid Mod ID", "The mod ID must start with a lowercase letter and contain only lowercase letters, numbers, and underscores.");
            return false;
        }

        // Validate the mod name
        if (modNameField.getText().isBlank()) {
            modNameField.setStyle("-fx-border-color: red;");
            modNameField.requestFocus();

            createAlert("Mod Name is Required", "Please enter a mod name for your project.");
            return false;
        }

        if (modNameField.getText().length() < 2) {
            modNameField.setStyle("-fx-border-color: red;");
            modNameField.requestFocus();

            createAlert("Invalid Mod Name", "The mod name must be at least 2 characters long.");
            return false;
        }

        if (modNameField.getText().length() > 256) {
            modNameField.setStyle("-fx-border-color: red;");
            modNameField.requestFocus();

            createAlert("Invalid Mod Name", "The mod name must be at most 256 characters long.");
            return false;
        }

        // Validate the main class
        if (mainClassField.getText().isBlank()) {
            mainClassField.setStyle("-fx-border-color: red;");
            mainClassField.requestFocus();

            createAlert("Main Class is Required", "Please enter a main class for your project.");
            return false;
        }

        if (!ClassNameValidator.isValid(mainClassField.getText())) {
            mainClassField.setStyle("-fx-border-color: red;");
            mainClassField.requestFocus();

            createAlert("Invalid Main Class", "The main class must be a valid Java class name.");
            return false;
        }

        // Validate the group ID
        if (groupIdField.getText().isBlank()) {
            groupIdField.setStyle("-fx-border-color: red;");
            groupIdField.requestFocus();

            createAlert("Group ID is Required", "Please enter a group ID for your project.");
            return false;
        }

        if (!groupIdField.getText().matches("[a-zA-Z0-9.]+")) {
            groupIdField.setStyle("-fx-border-color: red;");
            groupIdField.requestFocus();

            createAlert("Invalid Group ID", "The group ID must contain only letters, numbers, and periods.");
            return false;
        }

        // Validate the artifact ID
        if (artifactIdField.getText().isBlank()) {
            artifactIdField.setStyle("-fx-border-color: red;");
            artifactIdField.requestFocus();

            createAlert("Artifact ID is Required", "Please enter an artifact ID for your project.");
            return false;
        }

        if (!artifactIdField.getText().matches("[a-z0-9-]+")) {
            artifactIdField.setStyle("-fx-border-color: red;");
            artifactIdField.requestFocus();

            createAlert("Invalid Artifact ID", "The artifact ID must contain only lowercase letters, numbers, and hyphens.");
            return false;
        }

        // Validate the version
        if (versionField.getText().isBlank()) {
            versionField.setStyle("-fx-border-color: red;");
            versionField.requestFocus();

            createAlert("Version is Required", "Please enter a version for your project.");
            return false;
        }

        if (!versionField.getText().matches("[0-9]+(\\.[0-9]+){0,2}(-[a-zA-Z0-9]+)?")) {
            versionField.setStyle("-fx-border-color: red;");
            versionField.requestFocus();

            createAlert("Invalid Version", "The version must be in the format of x.y.z or x.y.z-tag.");
            return false;
        }

        // Validate the license
        if (licenseComboBox.getValue() == License.CUSTOM && licenseCustomField.getText().isBlank()) {
            licenseCustomField.setStyle("-fx-border-color: red;");
            licenseCustomField.requestFocus();

            createAlert("Custom License is Required", "Please enter a custom license for your project.");
            return false;
        }

        return true;
    }

    protected ForgeProjectData createData() {
        String projectName = projectNameField.getText().trim();
        Path projectPath = Path.of(projectPathField.getText().trim());
        boolean createGit = createGitCheckBox.isSelected();
        License license = licenseComboBox.getValue();
        String licenseCustom = license == License.CUSTOM ? licenseCustomField.getText().trim() : null;
        MinecraftVersion minecraftVersion = minecraftVersionComboBox.getValue();
        ForgeVersion forgeVersion = forgeVersionComboBox.getValue();
        String modId = modIdField.getText().trim();
        String modName = modNameField.getText().trim();
        String mainClass = mainClassField.getText().trim();
        boolean useMixins = useMixinsCheckBox.isSelected();
        boolean useAccessTransformer = useAccessTransformerCheckBox.isSelected();
        MappingChannel mappingChannel = mappingChannelComboBox.getValue();
        MappingVersion mappingVersion = mappingVersionComboBox.getValue();
        Optional<String> author = Optional.of(authorField.getText().trim()).filter(s -> !s.isBlank());
        Optional<String> description = Optional.of(descriptionArea.getText().trim()).filter(s -> !s.isBlank());
        Optional<String> issues = Optional.of(issuesField.getText().trim()).filter(s -> !s.isBlank());
        Optional<String> updateJsonUrl = Optional.of(updateJsonUrlField.getText().trim()).filter(s -> !s.isBlank());
        String groupId = groupIdField.getText().trim();
        String artifactId = artifactIdField.getText().trim();
        String version = versionField.getText().trim();

        return new ForgeProjectData(projectName, projectPath, createGit, license, licenseCustom,
                minecraftVersion, forgeVersion, modId, modName, mainClass, useMixins, useAccessTransformer,
                mappingChannel, mappingVersion,
                author, description, issues, updateJsonUrl,
                groupId, artifactId, version);
    }

    public static class StarableListCell<T> extends ListCell<T> {
        private final FontIcon starIcon = new FontIcon(FontAwesomeSolid.STAR);
        private final FontIcon halfStarIcon = new FontIcon(FontAwesomeSolid.STAR_HALF_ALT);

        private final Predicate<T> isRecommended;
        private final Predicate<T> isLatest;
        private final Function<T, String> stringConverter;

        public StarableListCell(Predicate<T> isRecommended, Predicate<T> isLatest, Function<T, String> stringConverter) {
            this.isRecommended = isRecommended;
            this.isLatest = isLatest;
            this.stringConverter = stringConverter;

            this.starIcon.setIconSize(16);
            this.starIcon.setIconColor(Color.GOLD);

            this.halfStarIcon.setIconSize(16);
            this.halfStarIcon.setIconColor(Color.GOLD);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(stringConverter.apply(item));
                setGraphic(isRecommended.test(item) ? starIcon : isLatest.test(item) ? halfStarIcon : null);
            }
        }
    }
}
