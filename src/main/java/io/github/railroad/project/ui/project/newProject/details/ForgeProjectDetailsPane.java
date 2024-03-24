package io.github.railroad.project.ui.project.newProject.details;

import io.github.railroad.minecraft.ForgeVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.project.License;
import io.github.railroad.project.ProjectType;
import io.github.railroad.project.ui.BrowseButton;
import io.github.railroad.utility.ClassNameValidator;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.javafx.FontIcon;

public class ForgeProjectDetailsPane extends VBox {
    private final TextField projectNameField = new TextField();
    private final TextField projectPathField = new TextField();
    private final BrowseButton browseButton = new BrowseButton(this.projectPathField, BrowseButton.BrowseType.DIRECTORY, BrowseButton.BrowseSelectionMode.SINGLE);
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

    private final TextField authorField = new TextField(); // optional
    private final TextArea descriptionArea = new TextArea(); // optional
    private final TextField issuesField = new TextField(); // optional
    private final TextField updateJsonUrlField = new TextField(); // optional

    private final TextField groupIdField = new TextField();
    private final TextField artifactIdField = new TextField();
    private final TextField versionField = new TextField();

    public ForgeProjectDetailsPane() {
        // Project Section
        var projectSection = new VBox(10);

        var projectNameBox = new HBox(10);
        var projectNameLabel = new Label("Name:");
        projectNameLabel.setLabelFor(projectNameField);
        projectNameBox.getChildren().addAll(projectNameLabel, projectNameField);

        var projectPathBox = new HBox(10);
        var projectPathLabel = new Label("Location:");
        projectPathLabel.setLabelFor(projectPathField);
        projectPathField.setPrefWidth(300);
        projectPathBox.getChildren().addAll(projectPathLabel, projectPathField, browseButton);

        var gitBox = new HBox(10);
        var createGitLabel = new Label("Create Git Repository:");
        createGitLabel.setLabelFor(createGitCheckBox);
        gitBox.getChildren().addAll(createGitLabel, createGitCheckBox);

        var licenseVBox = new VBox(10);
        var licenseBox = new HBox(10);
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

        projectSection.getChildren().addAll(projectNameBox, projectPathBox, gitBox, licenseVBox);

        // Minecraft Section
        var minecraftSection = new VBox(10);

        var minecraftVersionBox = new HBox(10);
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
        var forgeVersionLabel = new Label("Forge Version:");
        forgeVersionLabel.setLabelFor(forgeVersionComboBox);
        minecraftVersionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            forgeVersionComboBox.getItems().setAll(ForgeVersion.getVersions(newValue));
            forgeVersionComboBox.setValue(ForgeVersion.getLatestVersion(newValue));
        });
        forgeVersionComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ForgeVersion object) {
                return object.id();
            }

            @Override
            public ForgeVersion fromString(String string) {
                return ForgeVersion.fromId(string).orElse(null);
            }
        });
        forgeVersionComboBox.setCellFactory(param -> new ForgeVersionListCell());
        forgeVersionComboBox.getItems().addAll(ForgeVersion.getVersions(MinecraftVersion.getLatestStableVersion())); // TODO: Figure out why recommended is not showing
        forgeVersionComboBox.setValue(ForgeVersion.getLatestVersion(MinecraftVersion.getLatestStableVersion()));
        forgeVersionBox.getChildren().addAll(forgeVersionLabel, forgeVersionComboBox);

        var modIdBox = new HBox(10);
        var modIdLabel = new Label("Mod ID:");
        modIdLabel.setLabelFor(modIdField);
        modIdField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("[a-z0-9_-]*")) { // Only allow [a-z0-9_-]
                modIdField.setText(oldValue);
            }
        });
        modIdBox.getChildren().addAll(modIdLabel, modIdField);

        var modNameBox = new HBox(10);
        var modNameLabel = new Label("Mod Name:");
        modNameLabel.setLabelFor(modNameField);
        modNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 256) {
                modNameField.setText(newValue.substring(0, 256));
            }
        });
        modNameBox.getChildren().addAll(modNameLabel, modNameField);

        var mainClassBox = new HBox(10);
        var mainClassLabel = new Label("Main Class:");
        mainClassLabel.setLabelFor(mainClassField);
        mainClassField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!ClassNameValidator.isValid(newValue)) {
                mainClassField.setText(oldValue);
            }
        });
        mainClassBox.getChildren().addAll(mainClassLabel, mainClassField);

        var useMixinsBox = new HBox(10);
        var useMixinsLabel = new Label("Use Mixins:");
        useMixinsLabel.setLabelFor(useMixinsCheckBox);
        useMixinsBox.getChildren().addAll(useMixinsLabel, useMixinsCheckBox);

        var useAccessTransformerBox = new HBox(10);
        var useAccessTransformerLabel = new Label("Use Access Transformer:");
        useAccessTransformerLabel.setLabelFor(useAccessTransformerCheckBox);
        useAccessTransformerBox.getChildren().addAll(useAccessTransformerLabel, useAccessTransformerCheckBox);

        minecraftSection.getChildren().addAll(minecraftVersionBox, forgeVersionBox,
                modIdBox, modNameBox, mainClassBox, useMixinsBox, useAccessTransformerBox);

        // Optional Section
        var optionalSection = new VBox(10);

        var authorBox = new HBox(10);
        var authorLabel = new Label("Author:");
        authorLabel.setLabelFor(authorField);
        authorBox.getChildren().addAll(authorLabel, authorField);

        var descriptionBox = new HBox(10);
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
        var issuesLabel = new Label("Issues:");
        issuesLabel.setLabelFor(issuesField);
        issuesBox.getChildren().addAll(issuesLabel, issuesField);

        var updateJsonUrlBox = new HBox(10);
        var updateJsonUrlLabel = new Label("Update JSON URL:");
        updateJsonUrlLabel.setLabelFor(updateJsonUrlField);
        updateJsonUrlBox.getChildren().addAll(updateJsonUrlLabel, updateJsonUrlField);

        optionalSection.getChildren().addAll(authorBox, descriptionBox, issuesBox, updateJsonUrlBox);

        // Maven Section
        var mavenSection = new VBox(10);

        var groupIdBox = new HBox(10);
        var groupIdLabel = new Label("Group ID:");
        groupIdLabel.setLabelFor(groupIdField);
        groupIdBox.getChildren().addAll(groupIdLabel, groupIdField);

        var artifactIdBox = new HBox(10);
        var artifactIdLabel = new Label("Artifact ID:");
        artifactIdLabel.setLabelFor(artifactIdField);
        artifactIdBox.getChildren().addAll(artifactIdLabel, artifactIdField);

        var versionBox = new HBox(10);
        var versionLabel = new Label("Version:");
        versionLabel.setLabelFor(versionField);
        versionBox.getChildren().addAll(versionLabel, versionField);

        mavenSection.getChildren().addAll(groupIdBox, artifactIdBox, versionBox);

        getChildren().addAll(projectSection,
                new Separator(), minecraftSection,
                new Separator(), optionalSection,
                new Separator(), mavenSection);
        setSpacing(20);
        setPadding(new Insets(10));
    }

    public static class ForgeVersionListCell extends ListCell<ForgeVersion> {
        private final FontIcon starIcon = new FontIcon(FontAwesomeRegular.STAR);

        public ForgeVersionListCell() {
            this.starIcon.setFill(Color.GOLD);
            this.starIcon.setIconSize(16);
        }

        @Override
        protected void updateItem(ForgeVersion item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.id());
                setGraphic(item.recommended() ? starIcon : null);
            }
        }
    }
}
