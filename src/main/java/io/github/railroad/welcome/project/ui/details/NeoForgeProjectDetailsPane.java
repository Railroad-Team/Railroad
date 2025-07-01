package io.github.railroad.welcome.project.ui.details;

import io.github.railroad.localization.L18n;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.project.DisplayTest;
import io.github.railroad.project.License;
import io.github.railroad.project.data.NeoForgeProjectData;
import io.github.railroad.project.minecraft.MinecraftVersion;
import io.github.railroad.project.minecraft.NeoForgeVersion;
import io.github.railroad.project.minecraft.RecommendableVersion;
import io.github.railroad.project.minecraft.mapping.MappingChannel;
import io.github.railroad.project.minecraft.mapping.MappingHelper;
import io.github.railroad.project.minecraft.mapping.MappingVersion;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.nodes.BrowseButton;
import io.github.railroad.utility.ClassNameValidator;
import io.github.railroad.utility.StringUtils;
import io.github.railroad.welcome.project.ui.widget.StarableListCell;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Border;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.StringConverter;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class NeoForgeProjectDetailsPane extends RRVBox {
    private final TextField projectNameField = new TextField();
    private final TextField projectPathField = new TextField();
    private final CheckBox createGitCheckBox = new CheckBox();
    private final ComboBox<License> licenseComboBox = new ComboBox<>();
    private final TextField licenseCustomField = new TextField();

    private final ComboBox<MinecraftVersion> minecraftVersionComboBox = new ComboBox<>();
    private final ComboBox<NeoForgeVersion> neoForgeVersionComboBox = new ComboBox<>();
    private final TextField modIdField = new TextField();
    private final TextField modNameField = new TextField();
    private final TextField mainClassField = new TextField();
    private final CheckBox useMixinsCheckBox = new CheckBox();
    private final CheckBox useAccessTransformerCheckBox = new CheckBox();
    private final CheckBox genRunFoldersCheckBox = new CheckBox();

    private final ComboBox<MappingChannel> mappingChannelComboBox = new ComboBox<>();
    private final ComboBox<MappingVersion> mappingVersionComboBox = new ComboBox<>();

    private final TextField authorField = new TextField(System.getProperty("user.name")); // optional
    private final TextField creditsField = new TextField(); // optional
    private final TextArea descriptionArea = new TextArea(); // optional
    private final TextField issuesField = new TextField(); // optional
    private final TextField updateJsonUrlField = new TextField(); // optional
    private final TextField displayUrlField = new TextField(); // optional
    private final ComboBox<DisplayTest> displayTestComboBox = new ComboBox<>(); // optional
    private final CheckBox clientSideOnlyCheckBox = new CheckBox(); // optional

    private final TextField groupIdField = new TextField();
    private final TextField artifactIdField = new TextField();
    private final TextField versionField = new TextField();

    private final AtomicBoolean hasOneDriveWarning = new AtomicBoolean(false);
    private final AtomicBoolean hasModidWarning = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModid = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInMainClass = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInArtifactId = new AtomicBoolean(false);

    public NeoForgeProjectDetailsPane() {
        var projectSection = new RRVBox(10);

        var projectNameBox = new RRHBox(10);
        projectNameBox.setAlignment(Pos.CENTER_LEFT);
        var projectNameLabel = new LocalizedLabel("railroad.project.creation.name");
        projectNameLabel.setLabelFor(projectNameField);
        projectNameBox.getChildren().addAll(projectNameLabel, createAsterisk(), projectNameField);

        var projectPathVBox = new RRVBox(10);
        projectPathVBox.setAlignment(Pos.CENTER_LEFT);

        var createdAtLabel = new LocalizedLabel("railroad.project.creation.location.info", System.getProperty("user.home"));
        createdAtLabel.setGraphic(new FontIcon(FontAwesomeSolid.INFO_CIRCLE));
        createdAtLabel.setTooltip(new Tooltip(L18n.localize("railroad.tooltip.project.location.info")));
        createdAtLabel.setTextFill(Color.SLATEGRAY);

        var projectPathBox = new RRHBox(10);
        projectPathBox.setAlignment(Pos.CENTER_LEFT);
        var projectPathLabel = new LocalizedLabel("railroad.project.creation.location");
        projectPathLabel.setLabelFor(projectPathField);
        projectPathField.setPrefWidth(300);
        projectPathField.setText(System.getProperty("user.home"));
        projectPathField.setEditable(false);
        projectPathField.textProperty().addListener((observable, oldValue, newValue) -> {
            // Validate the project path - only check if it's a directory if it exists
            Path path = Path.of(newValue);
            if (Files.exists(path) && !Files.isDirectory(path))
                setValidationError(projectPathField);
            else
                clearValidation(projectPathField);

            // Update the created at label
            String fullPath = fixPath(projectPathField.getText().trim() + "/" + projectNameField.getText().trim());
            createdAtLabel.setText(L18n.localize("railroad.project.creation.location.info", fullPath));

            // If the project is in OneDrive, warn the user
            if (fullPath.contains("OneDrive") && hasOneDriveWarning.compareAndSet(false, true)) {
                setValidationWarning(projectPathField);

                var tooltip = new Tooltip(L18n.localize("railroad.tooltip.project.location.onedrive_warning"));
                Tooltip.install(projectPathField, tooltip);

                var warningIcon = new FontIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
                warningIcon.setIconSize(16);
                warningIcon.setIconColor(Color.ORANGE);
                projectPathBox.getChildren().add(warningIcon);
            } else if (!fullPath.contains("OneDrive") && hasOneDriveWarning.compareAndSet(true, false)) {
                clearValidation(projectPathField);

                Tooltip.uninstall(projectPathField, projectPathField.getTooltip());

                projectPathBox.getChildren().removeLast();
            } else if (fullPath.contains("OneDrive")) {
                setValidationWarning(projectPathField);
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
        browseButton.setTooltip(new Tooltip(L18n.localize("railroad.tooltip.browse")));
        projectPathBox.getChildren().addAll(projectPathLabel, createAsterisk(), projectPathField, browseButton);

        projectPathVBox.getChildren().addAll(projectPathBox, createdAtLabel);

        var gitBox = new RRHBox(10);
        gitBox.setAlignment(Pos.CENTER_LEFT);
        var createGitLabel = new LocalizedLabel("railroad.project.creation.git");
        createGitLabel.setLabelFor(createGitCheckBox);
        gitBox.getChildren().addAll(createGitLabel, createGitCheckBox);

        var licenseVBox = new RRVBox(10);
        licenseVBox.setAlignment(Pos.CENTER_LEFT);
        var licenseBox = new RRHBox(10);
        licenseBox.setAlignment(Pos.CENTER_LEFT);
        var licenseLabel = new LocalizedLabel("railroad.project.creation.license");
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
        licenseBox.getChildren().addAll(licenseLabel, createAsterisk(), licenseComboBox);
        licenseVBox.getChildren().add(licenseBox);

        projectSection.getChildren().addAll(createTitle("Project"), projectNameBox, projectPathVBox, gitBox, licenseVBox);

        // Minecraft Section
        var minecraftSection = new RRVBox(10);
        minecraftSection.setAlignment(Pos.CENTER_LEFT);

        var minecraftVersionBox = new RRHBox(10);
        minecraftVersionBox.setAlignment(Pos.CENTER_LEFT);
        var minecraftVersionLabel = new LocalizedLabel("railroad.project.creation.minecraft_version");
        minecraftVersionLabel.setLabelFor(minecraftVersionComboBox);
        //minecraftVersionComboBox.getItems().addAll(MinecraftVersion.getSupportedVersions(ProjectType.NEOFORGED));
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
        minecraftVersionBox.getChildren().addAll(minecraftVersionLabel, createAsterisk(), minecraftVersionComboBox);

        var neoForgeVersionBox = new RRHBox(10);
        neoForgeVersionBox.setAlignment(Pos.CENTER_LEFT);
        var neoForgeVersionLabel = new LocalizedLabel("railroad.project.creation.neo_forge_version");
        neoForgeVersionLabel.setLabelFor(neoForgeVersionComboBox);
        minecraftVersionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            neoForgeVersionComboBox.getItems().setAll(NeoForgeVersion.getVersions(newValue));
            neoForgeVersionComboBox.setValue(NeoForgeVersion.getLatestVersion(newValue));

            MappingHelper.loadMappings(mappingChannelComboBox.getItems(), newValue);
            mappingChannelComboBox.setValue(mappingChannelComboBox.getItems().getFirst());
        });
        neoForgeVersionComboBox.setCellFactory(param -> new StarableListCell<>(
                version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                version -> Objects.equals(version.id(), NeoForgeVersion.getLatestVersion(minecraftVersionComboBox.getValue()).id()),
                NeoForgeVersion::id));
        neoForgeVersionComboBox.setButtonCell(new StarableListCell<>(
                version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                version -> Objects.equals(version.id(), NeoForgeVersion.getLatestVersion(minecraftVersionComboBox.getValue()).id()),
                NeoForgeVersion::id));
        neoForgeVersionComboBox.getItems().addAll(NeoForgeVersion.getVersions(MinecraftVersion.getLatestStableVersion()));
        neoForgeVersionComboBox.setValue(NeoForgeVersion.getLatestVersion(MinecraftVersion.getLatestStableVersion()));
        neoForgeVersionBox.getChildren().addAll(neoForgeVersionLabel, createAsterisk(), neoForgeVersionComboBox);

        var modIdBox = new RRHBox(10);
        modIdBox.setAlignment(Pos.CENTER_LEFT);
        var modIdLabel = new LocalizedLabel("railroad.project.creation.mod_id");
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
                setValidationError(modIdField);
            else
                clearValidation(modIdField);

            // If the mod ID is not valid, show a warning
            if ((newValue.length() < 5 && newValue.length() > 2) && hasModidWarning.compareAndSet(false, true)) {
                setValidationWarning(modIdField);

                var tooltip = new Tooltip(L18n.localize("railroad.tooltip.mod_id.short_warning"));
                Tooltip.install(modIdField, tooltip);

                var warningIcon = new FontIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
                warningIcon.setIconSize(16);
                warningIcon.setIconColor(Color.ORANGE);
                modIdBox.getChildren().add(warningIcon);
            } else if (newValue.length() >= 5 && hasModidWarning.compareAndSet(true, false)) {
                clearValidation(modIdField);

                Tooltip.uninstall(modIdField, modIdField.getTooltip());
                modIdBox.getChildren().removeLast();
            } else if (newValue.length() < 3 && hasModidWarning.compareAndSet(true, false)) {
                clearValidation(modIdField);
                modIdBox.getChildren().removeLast();
            }

            // update the artifact ID field if it is empty
            if (!hasTypedInArtifactId.get() || artifactIdField.getText().isBlank())
                artifactIdField.setText(newValue.replaceAll("[^a-z0-9-]", ""));
        });
        modIdField.setOnKeyTyped(event -> {
            // If the user has typed in the mod ID, and it is not empty, set the hasTypedInModid flag to true
            if (!hasTypedInModid.get() && !modIdField.getText().isBlank())
                hasTypedInModid.set(true);
            else if (hasTypedInModid.get() && modIdField.getText().isBlank())
                hasTypedInModid.set(false);
        });
        modIdBox.getChildren().addAll(modIdLabel, createAsterisk(), modIdField);

        var modNameBox = new RRHBox(10);
        modNameBox.setAlignment(Pos.CENTER_LEFT);
        var modNameLabel = new LocalizedLabel("railroad.project.creation.mod_name");
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
        modNameBox.getChildren().addAll(modNameLabel, createAsterisk(), modNameField);

        var mainClassBox = new RRHBox(10);
        mainClassBox.setAlignment(Pos.CENTER_LEFT);
        var mainClassLabel = new LocalizedLabel("railroad.project.creation.main_class");
        mainClassLabel.setLabelFor(mainClassField);
        mainClassField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!ClassNameValidator.isValid(newValue)) {
                mainClassField.setText(oldValue);
            }
        });
        mainClassField.setOnKeyTyped(event -> {
            // If the user has typed in the main class, and it is not empty, set the hasTypedInMainClass flag to true
            if (!hasTypedInMainClass.get() && !mainClassField.getText().isBlank())
                hasTypedInMainClass.set(true);
            else if (hasTypedInMainClass.get() && mainClassField.getText().isBlank())
                hasTypedInMainClass.set(false);
        });
        mainClassBox.getChildren().addAll(mainClassLabel, createAsterisk(), mainClassField);

        var useMixinsBox = new RRHBox(10);
        useMixinsBox.setAlignment(Pos.CENTER_LEFT);
        var useMixinsLabel = new LocalizedLabel("railroad.project.creation.use_mixins");
        useMixinsLabel.setLabelFor(useMixinsCheckBox);
        useMixinsBox.getChildren().addAll(useMixinsLabel, useMixinsCheckBox);

        var useAccessTransformerBox = new RRHBox(10);
        useAccessTransformerBox.setAlignment(Pos.CENTER_LEFT);
        var useAccessTransformerLabel = new LocalizedLabel("railroad.project.creation.use_access_transformer");
        useAccessTransformerLabel.setLabelFor(useAccessTransformerCheckBox);
        useAccessTransformerBox.getChildren().addAll(useAccessTransformerLabel, useAccessTransformerCheckBox);

        var genRunFoldersBox = new RRHBox(10);
        genRunFoldersBox.setAlignment(Pos.CENTER_LEFT);
        var genRunFoldersLabel = new LocalizedLabel("railroad.project.creation.gen_run_folders");
        genRunFoldersLabel.setLabelFor(genRunFoldersCheckBox);
        genRunFoldersBox.getChildren().addAll(genRunFoldersLabel, genRunFoldersCheckBox);

        minecraftSection.getChildren().addAll(createTitle("Minecraft"), minecraftVersionBox, neoForgeVersionBox,
                modIdBox, modNameBox, mainClassBox, useMixinsBox, useAccessTransformerBox, genRunFoldersBox);

        // Mapping Section
        var mappingSection = new RRVBox(10);
        mappingSection.setAlignment(Pos.CENTER_LEFT);

        var mappingChannelBox = new RRHBox(10);
        mappingChannelBox.setAlignment(Pos.CENTER_LEFT);
        var mappingChannelLabel = new LocalizedLabel("railroad.project.creation.mapping_channel");
        mappingChannelLabel.setLabelFor(mappingChannelComboBox);
        MappingHelper.loadMappings(mappingChannelComboBox.getItems(), minecraftVersionComboBox.getValue());
        mappingChannelComboBox.setValue(MappingChannel.PARCHMENT);
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
        mappingChannelBox.getChildren().addAll(mappingChannelLabel, createAsterisk(), mappingChannelComboBox);

        var mappingVersionBox = new RRHBox(10);
        mappingVersionBox.setAlignment(Pos.CENTER_LEFT);
        var mappingVersionLabel = new LocalizedLabel("railroad.project.creation.mapping_version");
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
        mappingVersionBox.getChildren().addAll(mappingVersionLabel, createAsterisk(), mappingVersionComboBox);

        mappingSection.getChildren().addAll(createTitle("Mappings"), mappingChannelBox, mappingVersionBox);

        // Optional Section
        var optionalSection = new RRVBox(10);
        optionalSection.setAlignment(Pos.CENTER_LEFT);

        var authorBox = new RRHBox(10);
        authorBox.setAlignment(Pos.CENTER_LEFT);
        var authorLabel = new LocalizedLabel("railroad.project.creation.author");
        authorLabel.setLabelFor(authorField);
        authorField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 256) {
                authorField.setText(newValue.substring(0, 256));
            }
        });
        authorBox.getChildren().addAll(authorLabel, authorField);

        var creditsBox = new RRHBox(10);
        creditsBox.setAlignment(Pos.CENTER_LEFT);
        var creditsLabel = new LocalizedLabel("railroad.project.creation.credits");
        creditsLabel.setLabelFor(creditsField);
        creditsField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 256) {
                creditsField.setText(newValue.substring(0, 256));
            }
        });
        creditsBox.getChildren().addAll(creditsLabel, creditsField);

        var descriptionBox = new RRHBox(10);
        descriptionBox.setAlignment(Pos.CENTER_LEFT);
        var descriptionLabel = new LocalizedLabel("railroad.project.creation.description");
        descriptionLabel.setLabelFor(descriptionArea);
        descriptionArea.setWrapText(true);
        descriptionArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 1028) {
                descriptionArea.setText(newValue.substring(0, 1028));
            }

            descriptionArea.setMinHeight(newValue.lines().count() * 20 + 40);
        });
        descriptionBox.getChildren().addAll(descriptionLabel, descriptionArea);

        var issuesBox = new RRHBox(10);
        issuesBox.setAlignment(Pos.CENTER_LEFT);
        var issuesLabel = new LocalizedLabel("railroad.project.creation.issues");
        issuesLabel.setLabelFor(issuesField);
        issuesBox.getChildren().addAll(issuesLabel, issuesField);

        var updateJsonUrlBox = new RRHBox(10);
        updateJsonUrlBox.setAlignment(Pos.CENTER_LEFT);
        var updateJsonUrlLabel = new LocalizedLabel("railroad.project.creation.update_json_url");
        updateJsonUrlLabel.setLabelFor(updateJsonUrlField);
        updateJsonUrlBox.getChildren().addAll(updateJsonUrlLabel, updateJsonUrlField);

        var displayUrlBox = new RRHBox(10);
        displayUrlBox.setAlignment(Pos.CENTER_LEFT);
        var displayUrlLabel = new LocalizedLabel("railroad.project.creation.display_url");
        displayUrlLabel.setLabelFor(displayUrlField);
        displayUrlBox.getChildren().addAll(displayUrlLabel, displayUrlField);

        var displayTestBox = new RRHBox(10);
        displayTestBox.setAlignment(Pos.CENTER_LEFT);
        var displayTestLabel = new LocalizedLabel("railroad.project.creation.display_test");
        displayTestLabel.setLabelFor(displayTestComboBox);
        displayTestComboBox.getItems().addAll(DisplayTest.values());
        displayTestComboBox.setValue(DisplayTest.MATCH_VERSION);
        displayTestComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DisplayTest object) {
                return object.name();
            }

            @Override
            public DisplayTest fromString(String string) {
                return DisplayTest.valueOf(string);
            }
        });
        displayTestBox.getChildren().addAll(displayTestLabel, createAsterisk(), displayTestComboBox);

        var clientSideOnlyBox = new RRHBox(10);
        clientSideOnlyBox.setAlignment(Pos.CENTER_LEFT);
        var clientSideOnlyLabel = new LocalizedLabel("railroad.project.creation.client_side_only");
        clientSideOnlyLabel.setLabelFor(clientSideOnlyCheckBox);
        clientSideOnlyCheckBox.setSelected(false);
        clientSideOnlyCheckBox.setTooltip(new Tooltip(L18n.localize("railroad.tooltip.client_side_only")));
        clientSideOnlyCheckBox.setOnAction(event -> {
            displayTestComboBox.setValue(clientSideOnlyCheckBox.isSelected() ?
                    DisplayTest.IGNORE_ALL_VERSION : DisplayTest.MATCH_VERSION);
        });
        clientSideOnlyBox.getChildren().addAll(clientSideOnlyLabel, clientSideOnlyCheckBox);

        optionalSection.getChildren().addAll(createTitle("Details (Optional)"), authorBox, creditsBox, descriptionBox, issuesBox, updateJsonUrlBox,
                displayUrlBox, displayTestBox, clientSideOnlyBox);

        // Maven Section
        var mavenSection = new RRVBox(10);
        mavenSection.setAlignment(Pos.CENTER_LEFT);

        var groupIdBox = new RRHBox(10);
        groupIdBox.setAlignment(Pos.CENTER_LEFT);
        var groupIdLabel = new LocalizedLabel("railroad.project.creation.group_id");
        groupIdLabel.setLabelFor(groupIdField);
        groupIdBox.getChildren().addAll(groupIdLabel, createAsterisk(), groupIdField);

        var artifactIdBox = new RRHBox(10);
        artifactIdBox.setAlignment(Pos.CENTER_LEFT);
        var artifactIdLabel = new LocalizedLabel("railroad.project.creation.artifact_id");
        artifactIdLabel.setLabelFor(artifactIdField);
        artifactIdBox.getChildren().addAll(artifactIdLabel, createAsterisk(), artifactIdField);

        var versionBox = new RRHBox(10);
        versionBox.setAlignment(Pos.CENTER_LEFT);
        var versionLabel = new LocalizedLabel("railroad.project.creation.version");
        versionLabel.setLabelFor(versionField);
        versionBox.getChildren().addAll(versionLabel, createAsterisk(), versionField);

        mavenSection.getChildren().addAll(createTitle("Maven"), groupIdBox, artifactIdBox, versionBox);

        getChildren().addAll(projectSection,
                new Separator(), minecraftSection,
                new Separator(), mappingSection,
                new Separator(), optionalSection,
                new Separator(), mavenSection);
        setSpacing(20);
        setPadding(new Insets(20));

        Border projectNameFieldBorder = projectNameField.getBorder();
        projectNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            // Remove any .<>:"/\|?* characters from the project name
            newValue = newValue.replaceAll("[.<>:\"/\\\\|?*]", "");

            // Check that the name does not exceed 256 characters
            if (newValue.length() > 256)
                projectNameField.setText(newValue.substring(0, 256));

            // Validate the project name
            if (newValue.isBlank())
                setValidationError(projectNameField);
            else
                clearValidation(projectNameField);

            // Update the created at label
            String path = fixPath(projectPathField.getText().trim() + "/" + projectNameField.getText().trim());
            createdAtLabel.setText(L18n.localize("railroad.project.creation.location.info", path));

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
                    if (word.isBlank())
                        continue;

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

                NeoForgeProjectData data = createData();
                scene.setRoot(new NeoForgeProjectCreationPane(data));
                event.consume();
            }
        });
    }

    private static Text createAsterisk() {
        var asterisk = new Text("*");
        asterisk.setFill(Color.RED);
        Tooltip.install(asterisk, new Tooltip(L18n.localize("railroad.tooltip.required")));
        return asterisk;
    }

    private static Text createTitle(String title) {
        var text = new Text(title);
        text.getStyleClass().add("form-section-title");
        return text;
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
            setValidationError(projectNameField);
            projectNameField.requestFocus();

            createAlert("Project Name is Required", "Please enter a name for your project.");
            return false;
        }

        // Validate the project path
        if (projectPathField.getText().isBlank()) {
            setValidationError(projectPathField);
            projectPathField.requestFocus();

            createAlert("Project Path is Required", "Please enter a path for your project.");
            return false;
        }

        Path path = Path.of(projectPathField.getText());
        if (Files.notExists(path)) {
            // Create the directory if it doesn't exist
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                setValidationError(projectPathField);
                projectPathField.requestFocus();

                createAlert("Cannot Create Directory", "Unable to create the specified directory. Please check the path and permissions.");
                return false;
            }
        }

        if (!Files.isDirectory(path)) {
            setValidationError(projectPathField);
            projectPathField.requestFocus();

            createAlert("Invalid Project Path", "The specified path is not a directory.");
            return false;
        }

        // Validate the mod ID
        if (modIdField.getText().isBlank()) {
            setValidationError(modIdField);
            modIdField.requestFocus();

            createAlert("Mod ID is Required", "Please enter a mod ID for your project.");
            return false;
        }

        if (modIdField.getText().length() < 2) {
            setValidationError(modIdField);
            modIdField.requestFocus();

            createAlert("Invalid Mod ID", "The mod ID must be at least 2 characters long.");
            return false;
        }

        if (!modIdField.getText().matches("^[a-z][a-z0-9_]{1,63}$")) {
            setValidationError(modIdField);
            modIdField.requestFocus();

            createAlert("Invalid Mod ID", "The mod ID must start with a lowercase letter and contain only lowercase letters, numbers, and underscores.");
            return false;
        }

        // Validate the mod name
        if (modNameField.getText().isBlank()) {
            setValidationError(modNameField);
            modNameField.requestFocus();

            createAlert("Mod Name is Required", "Please enter a mod name for your project.");
            return false;
        }

        if (modNameField.getText().length() < 2) {
            setValidationError(modNameField);
            modNameField.requestFocus();

            createAlert("Invalid Mod Name", "The mod name must be at least 2 characters long.");
            return false;
        }

        if (modNameField.getText().length() > 256) {
            setValidationError(modNameField);
            modNameField.requestFocus();

            createAlert("Invalid Mod Name", "The mod name must be at most 256 characters long.");
            return false;
        }

        // Validate the main class
        if (mainClassField.getText().isBlank()) {
            setValidationError(mainClassField);
            mainClassField.requestFocus();

            createAlert("Main Class is Required", "Please enter a main class for your project.");
            return false;
        }

        if (!ClassNameValidator.isValid(mainClassField.getText())) {
            setValidationError(mainClassField);
            mainClassField.requestFocus();

            createAlert("Invalid Main Class", "The main class must be a valid Java class name.");
            return false;
        }

        // Validate the group ID
        if (groupIdField.getText().isBlank()) {
            setValidationError(groupIdField);
            groupIdField.requestFocus();

            createAlert("Group ID is Required", "Please enter a group ID for your project.");
            return false;
        }

        if (!groupIdField.getText().matches("[a-zA-Z0-9.]+")) {
            setValidationError(groupIdField);
            groupIdField.requestFocus();

            createAlert("Invalid Group ID", "The group ID must contain only letters, numbers, and periods.");
            return false;
        }

        // Validate the artifact ID
        if (artifactIdField.getText().isBlank()) {
            setValidationError(artifactIdField);
            artifactIdField.requestFocus();

            createAlert("Artifact ID is Required", "Please enter an artifact ID for your project.");
            return false;
        }

        if (!artifactIdField.getText().matches("[a-z0-9-]+")) {
            setValidationError(artifactIdField);
            artifactIdField.requestFocus();

            createAlert("Invalid Artifact ID", "The artifact ID must contain only lowercase letters, numbers, and hyphens.");
            return false;
        }

        // Validate the version
        if (versionField.getText().isBlank()) {
            setValidationError(versionField);
            versionField.requestFocus();

            createAlert("Version is Required", "Please enter a version for your project.");
            return false;
        }

        if (!versionField.getText().matches("[0-9]+(\\.[0-9]+){0,2}(-[a-zA-Z0-9]+)?")) {
            setValidationError(versionField);
            versionField.requestFocus();

            createAlert("Invalid Version", "The version must be in the format of x.y.z or x.y.z-tag.");
            return false;
        }

        // Validate the license
        if (licenseComboBox.getValue() == License.CUSTOM && licenseCustomField.getText().isBlank()) {
            setValidationError(licenseCustomField);
            licenseCustomField.requestFocus();

            createAlert("Custom License is Required", "Please enter a custom license for your project.");
            return false;
        }

        // Validate issues, update json and display URL
        if (!issuesField.getText().isBlank() && !issuesField.getText().matches(StringUtils.URL_REGEX)) {
            setValidationError(issuesField);
            issuesField.requestFocus();

            createAlert("Invalid Issues URL", "The issues URL must be a valid URL.");
            return false;
        }

        if (!updateJsonUrlField.getText().isBlank() && !updateJsonUrlField.getText().matches(StringUtils.URL_REGEX) && !updateJsonUrlField.getText().endsWith(".json")) {
            setValidationError(updateJsonUrlField);
            updateJsonUrlField.requestFocus();

            createAlert("Invalid Update JSON URL", "The update JSON URL must be a valid URL.");
            return false;
        }

        if (!displayUrlField.getText().isBlank() && !displayUrlField.getText().matches(StringUtils.URL_REGEX)) {
            setValidationError(displayUrlField);
            displayUrlField.requestFocus();

            createAlert("Invalid Display URL", "The display URL must be a valid URL.");
            return false;
        }

        return true;
    }

    protected NeoForgeProjectData createData() {
        String projectName = projectNameField.getText().trim();
        var projectPath = Path.of(projectPathField.getText().trim());
        boolean createGit = createGitCheckBox.isSelected();
        License license = licenseComboBox.getValue();
        String licenseCustom = license == License.CUSTOM ? licenseCustomField.getText().trim() : null;
        MinecraftVersion minecraftVersion = minecraftVersionComboBox.getValue();
        NeoForgeVersion neoForgeVersion = neoForgeVersionComboBox.getValue();
        String modId = modIdField.getText().trim();
        String modName = modNameField.getText().trim();
        String mainClass = mainClassField.getText().trim();
        boolean useMixins = useMixinsCheckBox.isSelected();
        boolean useAccessTransformer = useAccessTransformerCheckBox.isSelected();
        boolean genRunFolders = genRunFoldersCheckBox.isSelected();
        MappingChannel mappingChannel = mappingChannelComboBox.getValue();
        MappingVersion mappingVersion = mappingVersionComboBox.getValue();
        Optional<String> author = Optional.of(authorField.getText().trim()).filter(s -> !s.isBlank());
        Optional<String> credits = Optional.of(creditsField.getText().trim()).filter(s -> !s.isBlank());
        Optional<String> description = Optional.of(descriptionArea.getText().trim()).filter(s -> !s.isBlank());
        Optional<String> issues = Optional.of(issuesField.getText().trim()).filter(s -> !s.isBlank());
        Optional<String> updateJsonUrl = Optional.of(updateJsonUrlField.getText().trim()).filter(s -> !s.isBlank());
        Optional<String> displayUrl = Optional.of(displayUrlField.getText().trim()).filter(s -> !s.isBlank());
        DisplayTest displayTest = displayTestComboBox.getValue();
        boolean clientSideOnly = clientSideOnlyCheckBox.isSelected();
        String groupId = groupIdField.getText().trim();
        String artifactId = artifactIdField.getText().trim();
        String version = versionField.getText().trim();

        return new NeoForgeProjectData(projectName, projectPath, createGit, license, licenseCustom,
                minecraftVersion, neoForgeVersion, modId, modName, mainClass, useMixins, useAccessTransformer, genRunFolders,
                mappingChannel, mappingVersion,
                author, credits, description, issues, updateJsonUrl, displayUrl, displayTest, clientSideOnly,
                groupId, artifactId, version);
    }

    private void setValidationError(TextField field) {
        field.getStyleClass().add("validation-error");
    }

    private void setValidationWarning(TextField field) {
        field.getStyleClass().add("validation-warning");
    }

    private void clearValidation(TextField field) {
        field.getStyleClass().remove("validation-error");
        field.getStyleClass().remove("validation-warning");
    }
}
