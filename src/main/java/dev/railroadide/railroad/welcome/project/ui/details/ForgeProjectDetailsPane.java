package dev.railroadide.railroad.welcome.project.ui.details;

import dev.railroadide.core.form.Form;
import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormData;
import dev.railroadide.core.form.FormSection;
import dev.railroadide.core.form.impl.*;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.project.DisplayTest;
import dev.railroadide.railroad.project.License;
import dev.railroadide.railroad.project.data.ForgeProjectData;
import dev.railroadide.railroad.project.minecraft.ForgeVersion;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.RecommendableVersion;
import dev.railroadide.railroad.project.minecraft.mapping.MappingChannel;
import dev.railroadide.railroad.project.minecraft.mapping.MappingHelper;
import dev.railroadide.railroad.project.minecraft.mapping.MappingVersion;
import dev.railroadide.railroad.welcome.project.ProjectType;
import dev.railroadide.railroad.welcome.project.ui.widget.StarableListCell;
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
        TextFieldComponent projectNameComponent = FormComponent.textField("ProjectName", "railroad.project.creation.name")
                .required()
                .bindTextFieldTo(projectNameField)
                .promptText("railroad.project.creation.name.prompt")
                .validator(ProjectValidators::validateProjectName)
                .listener((node, observable, oldValue, newValue) -> {
                    String path = ProjectValidators.getRepairedPath(projectPathField.get().getText().trim() + "\\" + projectNameField.get().getText().trim());
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

        DirectoryChooserComponent projectPathComponent = FormComponent.directoryChooser("ProjectPath", "railroad.project.creation.location")
                .required()
                .defaultPath(System.getProperty("user.home"))
                .bindTextFieldTo(projectPathField)
                .validator(ProjectValidators::validatePath)
                .listener((node, observable, oldValue, newValue) -> {
                    String path = ProjectValidators.getRepairedPath(projectPathField.get().getText().trim() + "\\" + projectNameField.get().getText().trim());
                    createdAtPath.set(path);
                })
                .build();

        CheckBoxComponent createGitComponent = FormComponent.checkBox("CreateGit", "railroad.project.creation.git")
                .bindCheckBoxTo(createGitCheckBox)
                .build();

        ComboBoxComponent<License> licenseComponent = FormComponent.comboBox("License", "railroad.project.creation.license", License.class)
                .required()
                .bindComboBoxTo(licenseComboBox)
                .keyFunction(License::getName)
                .valueOfFunction(License::fromName)
                .translate(false)
                .items(Arrays.asList(License.values()))
                .defaultValue(() -> License.LGPL)
                .build();

        TextFieldComponent licenseCustomComponent = FormComponent.textField("CustomLicense", "railroad.project.creation.license.custom")
                .visible(licenseComboBox.get().valueProperty().isEqualTo(License.CUSTOM))
                .bindTextFieldTo(licenseCustomField)
                .promptText("railroad.project.creation.license.custom.prompt")
                .validator(ProjectValidators::validateCustomLicense)
                .build();

        List<MinecraftVersion> supportedVersions = MinecraftVersion.getSupportedVersions(ProjectType.FORGE);
        MinecraftVersion latestVersion = supportedVersions.getFirst();
        ComboBoxComponent<MinecraftVersion> minecraftVersionComponent = FormComponent.comboBox("MinecraftVersion", "railroad.project.creation.minecraft_version", MinecraftVersion.class)
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

        ComboBoxComponent<ForgeVersion> forgeVersionComponent = FormComponent.comboBox("ForgeVersion", "railroad.project.creation.forge_version", ForgeVersion.class)
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

        TextFieldComponent modIdComponent = FormComponent.textField("ModId", "railroad.project.creation.mod_id")
                .required()
                .bindTextFieldTo(modIdField)
                .promptText("railroad.project.creation.mod_id.prompt")
                .validator(ProjectValidators::validateModId)
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

        TextFieldComponent modNameComponent = FormComponent.textField("ModName", "railroad.project.creation.mod_name")
                .required()
                .bindTextFieldTo(modNameField)
                .promptText("railroad.project.creation.mod_name.prompt")
                .validator(ProjectValidators::validateModName)
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

        TextFieldComponent mainClassComponent = FormComponent.textField("MainClass", "railroad.project.creation.main_class")
                .required()
                .bindTextFieldTo(mainClassField)
                .promptText("railroad.project.creation.main_class.prompt")
                .validator(ProjectValidators::validateMainClass)
                .keyTypedHandler(event -> {
                    if (!hasTypedInMainClass.get() && !mainClassField.get().getText().isBlank())
                        hasTypedInMainClass.set(true);
                    else if (hasTypedInMainClass.get() && mainClassField.get().getText().isBlank())
                        hasTypedInMainClass.set(false);
                })
                .build();

        CheckBoxComponent useMixinsComponent = FormComponent.checkBox("UseMixins", "railroad.project.creation.use_mixins")
                .bindCheckBoxTo(useMixinsCheckBox)
                .build();

        CheckBoxComponent useAccessTransformerComponent = FormComponent.checkBox("UseAccessTransformer", "railroad.project.creation.use_access_transformer")
                .bindCheckBoxTo(useAccessTransformerCheckBox)
                .build();

        CheckBoxComponent genRunFoldersComponent = FormComponent.checkBox("GenRunFolders", "railroad.project.creation.gen_run_folders")
                .bindCheckBoxTo(genRunFoldersCheckBox)
                .build();

        ComboBoxComponent<MappingChannel> mappingChannelComponent = FormComponent.comboBox("MappingChannel", "railroad.project.creation.mapping_channel", MappingChannel.class)
                .required()
                .items(MappingHelper.getChannels(latestVersion))
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

        ComboBoxComponent<MappingVersion> mappingVersionComponent = FormComponent.comboBox("MappingVersion", "railroad.project.creation.mapping_version", MappingVersion.class)
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

        TextFieldComponent authorComponent = FormComponent.textField("Author", "railroad.project.creation.author")
                .bindTextFieldTo(authorField)
                .promptText("railroad.project.creation.author.prompt")
                .validator(ProjectValidators::validateAuthor)
                .build();

        TextFieldComponent creditsComponent = FormComponent.textField("Credits", "railroad.project.creation.credits")
                .bindTextFieldTo(creditsField)
                .promptText("railroad.project.creation.credits.prompt")
                .validator(ProjectValidators::validateCredits)
                .build();

        TextAreaComponent descriptionComponent = FormComponent.textArea("Description", "railroad.project.creation.description")
                .bindTextAreaTo(descriptionArea)
                .promptText("railroad.project.creation.description.prompt")
                .validator(ProjectValidators::validateDescription)
                .resize(true)
                .wrapText(true)
                .build();

        TextFieldComponent issuesComponent = FormComponent.textField("Issues", "railroad.project.creation.issues")
                .bindTextFieldTo(issuesField)
                .promptText("railroad.project.creation.issues.prompt")
                .validator(ProjectValidators::validateIssues)
                .build();

        TextFieldComponent updateJsonUrlComponent = FormComponent.textField("UpdateJsonUrl", "railroad.project.creation.update_json_url")
                .bindTextFieldTo(updateJsonUrlField)
                .promptText("railroad.project.creation.update_json_url.prompt")
                .validator(ProjectValidators::validateUpdateJsonUrl)
                .build();

        TextFieldComponent displayUrlComponent = FormComponent.textField("DisplayUrl", "railroad.project.creation.display_url")
                .bindTextFieldTo(displayUrlField)
                .promptText("railroad.project.creation.display_url.prompt")
                .validator(field -> ProjectValidators.validateGenericUrl(field, "display_url"))
                .build();

        ComboBoxComponent<DisplayTest> displayTestComponent = FormComponent.comboBox("DisplayTest", "railroad.project.creation.display_test", DisplayTest.class)
                .bindComboBoxTo(displayTestComboBox)
                .keyFunction(DisplayTest::name)
                .valueOfFunction(DisplayTest::valueOf)
                .translate(false)
                .items(Arrays.asList(DisplayTest.values()))
                .defaultValue(() -> DisplayTest.MATCH_VERSION)
                .build();

        CheckBoxComponent clientSideOnlyComponent = FormComponent.checkBox("ClientSideOnly", "railroad.project.creation.client_side_only")
                .bindCheckBoxTo(clientSideOnlyCheckBox)
                .build();

        TextFieldComponent groupIdComponent = FormComponent.textField("GroupId", "railroad.project.creation.group_id")
                .required()
                .bindTextFieldTo(groupIdField)
                .promptText("railroad.project.creation.group_id.prompt")
                .validator(ProjectValidators::validateGroupId)
                .build();

        TextFieldComponent artifactIdComponent = FormComponent.textField("ArtifactId", "railroad.project.creation.artifact_id")
                .required()
                .bindTextFieldTo(artifactIdField)
                .promptText("railroad.project.creation.artifact_id.prompt")
                .validator(ProjectValidators::validateArtifactId)
                .keyTypedHandler(event -> {
                    if (!hasTypedInArtifactId.get() && !artifactIdField.get().getText().isBlank())
                        hasTypedInArtifactId.set(true);
                    else if (hasTypedInArtifactId.get() && artifactIdField.get().getText().isBlank())
                        hasTypedInArtifactId.set(false);
                })
                .build();

        TextFieldComponent versionComponent = FormComponent.textField("Version", "railroad.project.creation.version")
                .required()
                .bindTextFieldTo(versionField)
                .promptText("railroad.project.creation.version.prompt")
                .validator(ProjectValidators::validateVersion)
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
                        .appendComponent(genRunFoldersComponent))
                .appendSection(FormSection.create("railroad.project.creation.section.mappings")
                        .borderColor(Color.DARKGRAY)
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
                .disableResetButton()
                .onSubmit((theForm, formData) -> {
                    if (theForm.validate()) {
                        ForgeProjectData data = createData(formData);
                        getScene().setRoot(new ForgeProjectCreationPane(data));
                    } else {
                        theForm.runValidation(); // Show validation errors
                    }
                })
                .build();

        getChildren().add(form.createUI());

        String path = projectPathField.get().getText() == null ? "" : projectPathField.get().getText();
        projectPathComponent.getComponent().addInformationLabel("railroad.project.creation.location.info", path + "\\" + projectNameField.get().getText().trim());

        ComboBox<MappingVersion> mappingVersionComboBox = this.mappingVersionComboBox.get();
        MappingHelper.loadMappingsVersions(mappingVersionComboBox.getItems(), minecraftVersionComboBox.get().getValue(), mappingChannelComboBox.get().getValue());
        mappingVersionComboBox.setValue(mappingVersionComboBox.getItems().getFirst());
    }

    protected static ForgeProjectData createData(FormData formData) {
        String projectName = formData.getString("ProjectName");
        var projectPath = Path.of(formData.getString("ProjectPath"));
        boolean createGit = formData.getBoolean("CreateGit");
        License license = formData.getEnum("License", License.class);
        String licenseCustom = license == License.CUSTOM ? formData.getString("CustomLicense") : null;
        MinecraftVersion minecraftVersion = formData.get("MinecraftVersion", MinecraftVersion.class);
        ForgeVersion forgeVersion = formData.get("ForgeVersion", ForgeVersion.class);
        String modId = formData.getString("ModId");
        String modName = formData.getString("ModName");
        String mainClass = formData.getString("MainClass");
        boolean useMixins = formData.getBoolean("UseMixins");
        boolean useAccessTransformer = formData.getBoolean("UseAccessTransformer");
        boolean genRunFolders = formData.getBoolean("GenRunFolders");
        MappingChannel mappingChannel = formData.getEnum("MappingChannel", MappingChannel.class);
        MappingVersion mappingVersion = formData.get("MappingVersion", MappingVersion.class);
        Optional<String> author = Optional.ofNullable(formData.getString("Author")).filter(s -> !s.isBlank());
        Optional<String> credits = Optional.ofNullable(formData.getString("Credits")).filter(s -> !s.isBlank());
        Optional<String> description = Optional.ofNullable(formData.getString("Description")).filter(s -> !s.isBlank());
        Optional<String> issues = Optional.ofNullable(formData.getString("Issues")).filter(s -> !s.isBlank());
        Optional<String> updateJsonUrl = Optional.ofNullable(formData.getString("UpdateJsonUrl")).filter(s -> !s.isBlank());
        Optional<String> displayUrl = Optional.ofNullable(formData.getString("DisplayUrl")).filter(s -> !s.isBlank());
        DisplayTest displayTest = formData.getEnum("DisplayTest", DisplayTest.class);
        boolean clientSideOnly = formData.getBoolean("ClientSideOnly");
        String groupId = formData.getString("GroupId");
        String artifactId = formData.getString("ArtifactId");
        String version = formData.getString("Version");

        return new ForgeProjectData(projectName, projectPath, createGit, license, licenseCustom,
                minecraftVersion, forgeVersion, modId, modName, mainClass, useMixins, useAccessTransformer, genRunFolders,
                mappingChannel, mappingVersion,
                author, credits, description, issues, updateJsonUrl, displayUrl, displayTest, clientSideOnly,
                groupId, artifactId, version);
    }
}
