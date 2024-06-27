package io.github.railroad.project.ui.create.details;

import io.github.railroad.minecraft.ForgeVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.minecraft.RecommendableVersion;
import io.github.railroad.minecraft.mapping.MappingChannel;
import io.github.railroad.minecraft.mapping.MappingHelper;
import io.github.railroad.minecraft.mapping.MappingVersion;
import io.github.railroad.project.License;
import io.github.railroad.project.ProjectType;
import io.github.railroad.project.data.DisplayTest;
import io.github.railroad.project.data.ForgeProjectData;
import io.github.railroad.project.ui.create.widget.StarableListCell;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.form.Form;
import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormSection;
import io.github.railroad.ui.form.ValidationResult;
import io.github.railroad.ui.form.impl.*;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ForgeProjectDetailsPane extends RRVBox {
    private final StringProperty createdAtPath = new SimpleStringProperty();

    private final ObjectProperty<TextField> projectNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> projectPathField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> createGitCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<License>> licenseComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> licenseCustomField = new SimpleObjectProperty<>();

    private final ObjectProperty<ComboBox<MinecraftVersion>> minecraftVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<ForgeVersion>> forgeVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> mainClassField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> useMixinsCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> useAccessTransformerCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> genRunFoldersCheckBox = new SimpleObjectProperty<>();

    private final ObjectProperty<ComboBox<MappingChannel>> mappingChannelComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<MappingVersion>> mappingVersionComboBox = new SimpleObjectProperty<>();

    private final ObjectProperty<TextField> authorField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> creditsField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextArea> descriptionArea = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> issuesField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> updateJsonUrlField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> displayUrlField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<ComboBox<DisplayTest>> displayTestComboBox = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<CheckBox> clientSideOnlyCheckBox = new SimpleObjectProperty<>(); // optional

    private final ObjectProperty<TextField> groupIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> artifactIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> versionField = new SimpleObjectProperty<>();

    private final AtomicBoolean hasTypedInProjectName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModid = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInMainClass = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInArtifactId = new AtomicBoolean(false);

    public ForgeProjectDetailsPane() {
        TextFieldComponent projectNameComponent = FormComponent.textField("railroad.project.creation.name")
                .required()
                .bindTextFieldTo(projectNameField)
                .promptText("railroad.project.creation.name.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.name.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.name.error.length_long");

                    if (text.length() < 3)
                        return ValidationResult.error("railroad.project.creation.name.error.length_short");

                    if (text.matches("[.<>:\"/\\\\|?*]"))
                        return ValidationResult.error("railroad.project.creation.name.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .listener((node, observable, oldValue, newValue) -> {
                    String path = fixPath(projectPathField.get().getText().trim() + "\\" + projectNameField.get().getText().trim());
                    createdAtPath.set(path);
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInProjectName.get() && !projectNameField.get().getText().isBlank())
                        hasTypedInProjectName.set(true);
                    else if (hasTypedInProjectName.get() && projectNameField.get().getText().isBlank())
                        hasTypedInProjectName.set(false);
                })
                .addTransformer(projectNameField, modIdField, text -> {
                    if (!hasTypedInModid.get() || modIdField.get().getText().isBlank())
                        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");

                    return text;
                })
                .addTransformer(projectNameField, modNameField, text -> {
                    if (!hasTypedInModName.get() || modNameField.get().getText().isBlank())
                        return text;

                    return text;
                })
                .build();

        DirectoryChooserComponent projectPathComponent = FormComponent.directoryChooser("railroad.project.creation.location")
                .required()
                .defaultPath(System.getProperty("user.home"))
                .bindTextFieldTo(projectPathField)
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.location.error.required");

                    if (!text.matches(".*[a-zA-Z0-9]"))
                        return ValidationResult.error("railroad.project.creation.location.error.invalid_characters");

                    try {
                        Path path = Path.of(text);
                        if (Files.notExists(path))
                            return ValidationResult.error("railroad.project.creation.location.error.not_exists");

                        if (!Files.isDirectory(path))
                            return ValidationResult.error("railroad.project.creation.location.error.not_directory");
                    } catch (InvalidPathException exception) {
                        return ValidationResult.error("railroad.project.creation.location.error.invalid_path");
                    }

                    if (text.contains("OneDrive"))
                        return ValidationResult.warning("railroad.project.creation.location.warning.onedrive");

                    return ValidationResult.ok();
                })
                .listener((node, observable, oldValue, newValue) -> {
                    String path = fixPath(projectPathField.get().getText().trim() + "\\" + projectNameField.get().getText().trim());
                    createdAtPath.set(path);
                })
                .build();

        CheckBoxComponent createGitComponent = FormComponent.checkBox("railroad.project.creation.git")
                .bindCheckBoxTo(createGitCheckBox)
                .build();

        ComboBoxComponent<License> licenseComponent = FormComponent.comboBox("railroad.project.creation.license", License.class)
                .required()
                .bindComboBoxTo(licenseComboBox)
                .keyFunction(License::getName)
                .valueOfFunction(License::fromName)
                .translate(false)
                .items(Arrays.asList(License.values()))
                .defaultValue(() -> License.LGPL)
                .build();

        TextFieldComponent licenseCustomComponent = FormComponent.textField("railroad.project.creation.license.custom")
                .visible(licenseComboBox.get().valueProperty().isEqualTo(License.CUSTOM))
                .bindTextFieldTo(licenseCustomField)
                .promptText("railroad.project.creation.license.custom.prompt")
                .validator(textField -> {
                    if (textField.getText().isBlank())
                        return ValidationResult.error("railroad.project.creation.license.custom.error.required");

                    return ValidationResult.ok();
                })
                .build();

        List<MinecraftVersion> supportedVersions = MinecraftVersion.getSupportedVersions(ProjectType.FORGE);
        MinecraftVersion latestVersion = supportedVersions.getFirst();
        ComboBoxComponent<MinecraftVersion> minecraftVersionComponent = FormComponent.comboBox("railroad.project.creation.minecraft_version", MinecraftVersion.class)
                .required()
                .items(supportedVersions)
                .defaultValue(() -> latestVersion)
                .bindComboBoxTo(minecraftVersionComboBox)
                .keyFunction(MinecraftVersion::id)
                .valueOfFunction(string -> MinecraftVersion.fromId(string).orElse(null))
                .addTransformer(minecraftVersionComboBox, forgeVersionComboBox, ForgeVersion::getVersions)
                .listener((node, observable, oldValue, newValue) ->
                        forgeVersionComboBox.get().setValue(ForgeVersion.getLatestVersion(newValue)))
                .translate(false)
                .build();

        ComboBoxComponent<ForgeVersion> forgeVersionComponent = FormComponent.comboBox("railroad.project.creation.forge_version", ForgeVersion.class)
                .required()
                .items(ForgeVersion.getVersions(latestVersion))
                .defaultValue(() -> ForgeVersion.getLatestVersion(latestVersion))
                .bindComboBoxTo(forgeVersionComboBox)
                .keyFunction(ForgeVersion::id)
                .valueOfFunction(string -> ForgeVersion.fromId(string).orElse(null))
                .cellFactory(param -> new StarableListCell<>(
                        version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                        version -> Objects.equals(version, ForgeVersion.getLatestVersion(minecraftVersionComboBox.get().getValue())),
                        ForgeVersion::id))
                .buttonCell(new StarableListCell<>(
                        version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                        version -> Objects.equals(version, ForgeVersion.getLatestVersion(minecraftVersionComboBox.get().getValue())),
                        ForgeVersion::id))
                .translate(false)
                .build();

        TextFieldComponent modIdComponent = FormComponent.textField("railroad.project.creation.mod_id")
                .required()
                .bindTextFieldTo(modIdField)
                .promptText("railroad.project.creation.mod_id.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.mod_id.error.required");

                    if (text.length() < 3)
                        return ValidationResult.error("railroad.project.creation.mod_id.error.length_short");

                    if (text.length() > 64)
                        return ValidationResult.error("railroad.project.creation.mod_id.error.length_long");

                    if (!text.matches("^[a-z][a-z0-9_]{1,63}$"))
                        return ValidationResult.error("railroad.project.creation.mod_id.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInModid.get() && !modIdField.get().getText().isBlank())
                        hasTypedInModid.set(true);
                    else if (hasTypedInModid.get() && modIdField.get().getText().isBlank())
                        hasTypedInModid.set(false);
                })
                .addTransformer(modIdField, artifactIdField, text -> {
                    if (!hasTypedInArtifactId.get() || artifactIdField.get().getText().isBlank())
                        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");

                    return text;
                })
                .build();

        TextFieldComponent modNameComponent = FormComponent.textField("railroad.project.creation.mod_name")
                .required()
                .bindTextFieldTo(modNameField)
                .promptText("railroad.project.creation.mod_name.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.mod_name.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.mod_name.error.length_long");

                    return ValidationResult.ok();
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInModName.get() && !modNameField.get().getText().isBlank())
                        hasTypedInModName.set(true);
                    else if (hasTypedInModName.get() && modNameField.get().getText().isBlank())
                        hasTypedInModName.set(false);
                })
                .addTransformer(modNameField, mainClassField, text -> {
                    if (!hasTypedInMainClass.get() || mainClassField.get().getText().isBlank())
                        return text;

                    return text;
                })
                .build();

        TextFieldComponent mainClassComponent = FormComponent.textField("railroad.project.creation.main_class")
                .required()
                .bindTextFieldTo(mainClassField)
                .promptText("railroad.project.creation.main_class.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.main_class.error.required");

                    if (!text.matches("[a-zA-Z0-9_]+"))
                        return ValidationResult.error("railroad.project.creation.main_class.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInMainClass.get() && !mainClassField.get().getText().isBlank())
                        hasTypedInMainClass.set(true);
                    else if (hasTypedInMainClass.get() && mainClassField.get().getText().isBlank())
                        hasTypedInMainClass.set(false);
                })
                .build();

        CheckBoxComponent useMixinsComponent = FormComponent.checkBox("railroad.project.creation.use_mixins")
                .bindCheckBoxTo(useMixinsCheckBox)
                .build();

        CheckBoxComponent useAccessTransformerComponent = FormComponent.checkBox("railroad.project.creation.use_access_transformer")
                .bindCheckBoxTo(useAccessTransformerCheckBox)
                .build();

        CheckBoxComponent genRunFoldersComponent = FormComponent.checkBox("railroad.project.creation.gen_run_folders")
                .bindCheckBoxTo(genRunFoldersCheckBox)
                .build();

        ComboBoxComponent<MappingChannel> mappingChannelComponent = FormComponent.comboBox("railroad.project.creation.mapping_channel", MappingChannel.class)
                .required()
                .items(Arrays.asList(MappingChannel.values()))
                .defaultValue(() -> MappingChannel.MOJMAP)
                .bindComboBoxTo(mappingChannelComboBox)
                .keyFunction(MappingChannel::getName)
                .valueOfFunction(string -> MappingChannel.valueOf(string.toUpperCase(Locale.ROOT)))
                .addTransformer(mappingChannelComboBox, mappingVersionComboBox, mappingChannel -> {
                    ObservableList<MappingVersion> items = FXCollections.observableArrayList();
                    MappingHelper.loadMappingsVersions(items, minecraftVersionComboBox.get().getValue(), mappingChannel);
                    return items;
                })
                .listener((node, observable, oldValue, newValue) ->
                        mappingVersionComboBox.get().setValue(mappingVersionComboBox.get().getItems().getFirst()))
                .translate(false)
                .build();

        ComboBoxComponent<MappingVersion> mappingVersionComponent = FormComponent.comboBox("railroad.project.creation.mapping_version", MappingVersion.class)
                .required()
                .bindComboBoxTo(mappingVersionComboBox)
                .cellFactory(param -> new StarableListCell<>(
                        version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                        MappingVersion::isLatest,
                        MappingVersion::getId))
                .buttonCell(new StarableListCell<>(
                        version -> version instanceof RecommendableVersion recommendableVersion && recommendableVersion.isRecommended(),
                        MappingVersion::isLatest,
                        MappingVersion::getId))
                .keyFunction(MappingVersion::getId)
                .translate(false)
                .build();

        TextFieldComponent authorComponent = FormComponent.textField("railroad.project.creation.author")
                .bindTextFieldTo(authorField)
                .promptText("railroad.project.creation.author.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.author.error.length_long");

                    return ValidationResult.ok();
                })
                .build();

        TextFieldComponent creditsComponent = FormComponent.textField("railroad.project.creation.credits")
                .bindTextFieldTo(creditsField)
                .promptText("railroad.project.creation.credits.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.credits.error.length_long");

                    return ValidationResult.ok();
                })
                .build();

        TextAreaComponent descriptionComponent = FormComponent.textArea("railroad.project.creation.description")
                .bindTextAreaTo(descriptionArea)
                .promptText("railroad.project.creation.description.prompt")
                .validator(textArea -> {
                    String text = textArea.getText();
                    if (text.length() > 1028)
                        return ValidationResult.error("railroad.project.creation.description.error.length_long");

                    return ValidationResult.ok();
                })
                .resize(true)
                .wrapText(true)
                .build();

        TextFieldComponent issuesComponent = FormComponent.textField("railroad.project.creation.issues")
                .bindTextFieldTo(issuesField)
                .promptText("railroad.project.creation.issues.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.issues.error.length_long");

                    return ValidationResult.ok();
                })
                .build();

        TextFieldComponent updateJsonUrlComponent = FormComponent.textField("railroad.project.creation.update_json_url")
                .bindTextFieldTo(updateJsonUrlField)
                .promptText("railroad.project.creation.update_json_url.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.update_json_url.error.length_long");

                    if (!text.isBlank() && !text.matches("https?://.*"))
                        return ValidationResult.error("railroad.project.creation.update_json_url.error.invalid_url");

                    return ValidationResult.ok();
                })
                .build();

        TextFieldComponent displayUrlComponent = FormComponent.textField("railroad.project.creation.display_url")
                .bindTextFieldTo(displayUrlField)
                .promptText("railroad.project.creation.display_url.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.display_url.error.length_long");

                    if (!text.isBlank() && !text.matches("https?://.*"))
                        return ValidationResult.error("railroad.project.creation.display_url.error.invalid_url");

                    return ValidationResult.ok();
                })
                .build();

        ComboBoxComponent<DisplayTest> displayTestComponent = FormComponent.comboBox("railroad.project.creation.display_test", DisplayTest.class)
                .bindComboBoxTo(displayTestComboBox)
                .keyFunction(DisplayTest::name)
                .valueOfFunction(DisplayTest::valueOf)
                .translate(false)
                .items(Arrays.asList(DisplayTest.values()))
                .defaultValue(() -> DisplayTest.MATCH_VERSION)
                .build();

        CheckBoxComponent clientSideOnlyComponent = FormComponent.checkBox("railroad.project.creation.client_side_only")
                .bindCheckBoxTo(clientSideOnlyCheckBox)
                .build();

        TextFieldComponent groupIdComponent = FormComponent.textField("railroad.project.creation.group_id")
                .required()
                .bindTextFieldTo(groupIdField)
                .promptText("railroad.project.creation.group_id.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.group_id.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.group_id.error.length_long");

                    if (!text.matches("[a-zA-Z0-9.]+"))
                        return ValidationResult.error("railroad.project.creation.group_id.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .build();

        TextFieldComponent artifactIdComponent = FormComponent.textField("railroad.project.creation.artifact_id")
                .required()
                .bindTextFieldTo(artifactIdField)
                .promptText("railroad.project.creation.artifact_id.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.artifact_id.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.artifact_id.error.length_long");

                    if (!text.matches("[a-z0-9-]+"))
                        return ValidationResult.error("railroad.project.creation.artifact_id.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .keyTypedHandler(event -> {
                    if (!hasTypedInArtifactId.get() && !artifactIdField.get().getText().isBlank())
                        hasTypedInArtifactId.set(true);
                    else if (hasTypedInArtifactId.get() && artifactIdField.get().getText().isBlank())
                        hasTypedInArtifactId.set(false);
                })
                .build();

        TextFieldComponent versionComponent = FormComponent.textField("railroad.project.creation.version")
                .required()
                .bindTextFieldTo(versionField)
                .promptText("railroad.project.creation.version.prompt")
                .validator(textField -> {
                    String text = textField.getText();
                    if (text == null || text.isBlank())
                        return ValidationResult.error("railroad.project.creation.version.error.required");

                    if (text.length() > 256)
                        return ValidationResult.error("railroad.project.creation.version.error.length_long");

                    if (!text.matches("[a-zA-Z0-9.-]+"))
                        return ValidationResult.error("railroad.project.creation.version.error.invalid_characters");

                    return ValidationResult.ok();
                })
                .build();

        Form form = Form.create()
                .spacing(15)
                .padding(10)
                .appendSection(FormSection.create("railroad.project.creation.section.project")
                        .borderColor(Color.DARKGRAY)
                        .appendComponent(projectNameComponent)
                        .appendComponent(projectPathComponent)
                        .appendComponent(createGitComponent)
                        .appendComponent(licenseComponent)
                        .appendComponent(licenseCustomComponent))
                .appendSection(FormSection.create("railroad.project.creation.section.minecraft")
                        .borderColor(Color.DARKGRAY)
                        .appendComponent(minecraftVersionComponent)
                        .appendComponent(forgeVersionComponent)
                        .appendComponent(modIdComponent)
                        .appendComponent(modNameComponent)
                        .appendComponent(mainClassComponent)
                        .appendComponent(useMixinsComponent)
                        .appendComponent(useAccessTransformerComponent)
                        .appendComponent(genRunFoldersComponent)
                        .appendComponent(mappingChannelComponent)
                        .appendComponent(mappingVersionComponent))
                .appendSection(FormSection.create("railroad.project.creation.section.optional")
                        .borderColor(Color.SLATEGRAY)
                        .appendComponent(authorComponent)
                        .appendComponent(creditsComponent)
                        .appendComponent(descriptionComponent)
                        .appendComponent(issuesComponent)
                        .appendComponent(updateJsonUrlComponent)
                        .appendComponent(displayUrlComponent)
                        .appendComponent(displayTestComponent)
                        .appendComponent(clientSideOnlyComponent))
                .appendSection(FormSection.create("railroad.project.creation.section.maven")
                        .borderColor(Color.DARKGRAY)
                        .appendComponent(groupIdComponent)
                        .appendComponent(artifactIdComponent)
                        .appendComponent(versionComponent))
                .build();

        getChildren().add(form.createUI());

        projectPathComponent.getComponent().addInformationLabel("railroad.project.creation.location.info", createdAtPath, (projectPathField.get().getText() == null ? "" : projectPathField.get().getText()) + "\\" + (projectNameField.get().getText() == null ? "" : projectNameField.get().getText()));

        ComboBox<MappingVersion> mappingVersionComboBox = this.mappingVersionComboBox.get();
        MappingHelper.loadMappingsVersions(mappingVersionComboBox.getItems(), minecraftVersionComboBox.get().getValue(), mappingChannelComboBox.get().getValue());
        mappingVersionComboBox.setValue(mappingVersionComboBox.getItems().getFirst());
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

    protected ForgeProjectData createData() {
        String projectName = projectNameField.get().getText().trim();
        var projectPath = Path.of(projectPathField.get().getText().trim());
        boolean createGit = createGitCheckBox.get().isSelected();
        License license = licenseComboBox.get().getValue();
        String licenseCustom = license == License.CUSTOM ? licenseCustomField.get().getText().trim() : null;
        MinecraftVersion minecraftVersion = minecraftVersionComboBox.get().getValue();
        ForgeVersion forgeVersion = forgeVersionComboBox.get().getValue();
        String modId = modIdField.get().getText().trim();
        String modName = modNameField.get().getText().trim();
        String mainClass = mainClassField.get().getText().trim();
        boolean useMixins = useMixinsCheckBox.get().isSelected();
        boolean useAccessTransformer = useAccessTransformerCheckBox.get().isSelected();
        boolean genRunFolders = genRunFoldersCheckBox.get().isSelected();
        MappingChannel mappingChannel = mappingChannelComboBox.get().getValue();
        MappingVersion mappingVersion = mappingVersionComboBox.get().getValue();
        Optional<String> author = Optional.of(authorField.get().getText().trim()).filter(s -> !s.isBlank());
        Optional<String> credits = Optional.of(creditsField.get().getText().trim()).filter(s -> !s.isBlank());
        Optional<String> description = Optional.of(descriptionArea.get().getText().trim()).filter(s -> !s.isBlank());
        Optional<String> issues = Optional.of(issuesField.get().getText().trim()).filter(s -> !s.isBlank());
        Optional<String> updateJsonUrl = Optional.of(updateJsonUrlField.get().getText().trim()).filter(s -> !s.isBlank());
        Optional<String> displayUrl = Optional.of(displayUrlField.get().getText().trim()).filter(s -> !s.isBlank());
        DisplayTest displayTest = displayTestComboBox.get().getValue();
        boolean clientSideOnly = clientSideOnlyCheckBox.get().isSelected();
        String groupId = groupIdField.get().getText().trim();
        String artifactId = artifactIdField.get().getText().trim();
        String version = versionField.get().getText().trim();

        return new ForgeProjectData(projectName, projectPath, createGit, license, licenseCustom,
                minecraftVersion, forgeVersion, modId, modName, mainClass, useMixins, useAccessTransformer, genRunFolders,
                mappingChannel, mappingVersion,
                author, credits, description, issues, updateJsonUrl, displayUrl, displayTest, clientSideOnly,
                groupId, artifactId, version);
    }
}
