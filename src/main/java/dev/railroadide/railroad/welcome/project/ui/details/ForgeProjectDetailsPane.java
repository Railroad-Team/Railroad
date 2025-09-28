package dev.railroadide.railroad.welcome.project.ui.details;

import dev.railroadide.core.form.Form;
import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormData;
import dev.railroadide.core.form.FormSection;
import dev.railroadide.core.form.impl.*;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.DisplayTest;
import dev.railroadide.railroad.project.License;
import dev.railroadide.railroad.project.data.ForgeProjectData;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.forge.ForgeVersionService;
import dev.railroadide.railroad.project.minecraft.mappings.MappingChannel;
import dev.railroadide.railroad.project.minecraft.mappings.MappingChannelRegistry;
import dev.railroadide.railroad.welcome.project.ui.creation.ForgeProjectCreationPane;
import dev.railroadide.railroad.welcome.project.ui.widget.StarableListCell;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class ForgeProjectDetailsPane extends RRVBox {
    private final StringProperty createdAtPath = new SimpleStringProperty(ProjectValidators.getRepairedPath(System.getProperty("user.home") + "\\"));

    private final ObjectProperty<TextField> projectNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> projectPathField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> createGitCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<License>> licenseComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> licenseCustomField = new SimpleObjectProperty<>();

    private final ObjectProperty<ComboBox<MinecraftVersion>> minecraftVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<String>> forgeVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> mainClassField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> useMixinsCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> useAccessTransformerCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> genRunFoldersCheckBox = new SimpleObjectProperty<>();

    private final ObjectProperty<ComboBox<MappingChannel>> mappingChannelComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<String>> mappingVersionComboBox = new SimpleObjectProperty<>();

    private final ObjectProperty<TextField> authorField = new SimpleObjectProperty<>(new TextField(System.getProperty("user.name"))); // optional
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

        List<MinecraftVersion> supportedVersions = resolveForgeMinecraftVersions();
        MinecraftVersion latestVersion = MinecraftVersion.determineBestFit(supportedVersions);
        ComboBoxComponent<MinecraftVersion> minecraftVersionComponent = FormComponent.comboBox("MinecraftVersion", "railroad.project.creation.minecraft_version", MinecraftVersion.class)
            .required()
            .items(supportedVersions)
            .defaultValue(() -> latestVersion)
            .bindComboBoxTo(minecraftVersionComboBox)
            .keyFunction(MinecraftVersion::id)
            .valueOfFunction(string -> MinecraftVersion.fromId(string).orElse(null))
            .translate(false)
            .addTransformer(minecraftVersionComboBox, forgeVersionComboBox, version -> {
                if(version == null) {
                    Railroad.LOGGER.error("Minecraft version is null when transforming for Forge versions");
                    return null;
                }

                ComboBox<String> comboBox = forgeVersionComboBox.get();
                if (comboBox == null) {
                    Railroad.LOGGER.error("Forge version ComboBox is null when transforming for Minecraft version {}", version);
                    return null;
                }

                List<String> newVersions = ForgeVersionService.INSTANCE.listVersionsFor(version);
                comboBox.getItems().setAll(newVersions);
                if (newVersions.isEmpty()) {
                    Railroad.LOGGER.error("No Forge versions found for Minecraft version {}", version);
                    return null;
                }

                String latestFor = ForgeVersionService.INSTANCE.latestFor(version).orElse(null);
                if (latestFor == null) {
                    Railroad.LOGGER.error("No latest Forge version found for Minecraft version {}", version);
                    latestFor = newVersions.getLast();
                }

                return latestFor;
            })
            .addTransformer(minecraftVersionComboBox, mappingChannelComboBox, version -> {
                if(version == null) {
                    Railroad.LOGGER.error("Minecraft version is null when transforming for mapping channels");
                    return null;
                }

                ComboBox<MappingChannel> comboBox = mappingChannelComboBox.get();
                if (comboBox == null) {
                    Railroad.LOGGER.error("Mapping channel ComboBox is null when transforming for Minecraft version {}", version);
                    return null;
                }

                List<MappingChannel> newChannels = MappingChannelRegistry.findValidMappingChannels(version);
                comboBox.getItems().setAll(newChannels);
                if (newChannels.isEmpty()) {
                    Railroad.LOGGER.error("No mapping channels found for Minecraft version {}", version);
                    return null;
                }

                return MappingChannelRegistry.MOJMAP;
            })
            .build();

        ComboBoxComponent<String> forgeVersionComponent = FormComponent.comboBox("ForgeVersion", "railroad.project.creation.forge_version", String.class)
            .required()
            .items(ForgeVersionService.INSTANCE.listVersionsFor(latestVersion))
            .defaultValue(() -> ForgeVersionService.INSTANCE.latestFor(latestVersion).orElse(null))
            .bindComboBoxTo(forgeVersionComboBox)
            .cellFactory(param -> new StarableListCell<>(
                ForgeVersionService.INSTANCE::isRecommended,
                version -> Objects.equals(version, ForgeVersionService.INSTANCE.latestFor(getSelectedMinecraftVersion()).orElse(null)),
                Function.identity()))
            .buttonCell(new StarableListCell<>(
                ForgeVersionService.INSTANCE::isRecommended,
                version -> Objects.equals(version, ForgeVersionService.INSTANCE.latestFor(getSelectedMinecraftVersion()).orElse(null)),
                Function.identity()))
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
            .items(MappingChannelRegistry.findValidMappingChannels(getSelectedMinecraftVersion()))
            .defaultValue(() -> {
                MinecraftVersion minecraftVersion = getSelectedMinecraftVersion();
                if (minecraftVersion == null)
                    return MappingChannelRegistry.MOJMAP;

                return minecraftVersion.compareTo(MinecraftVersion.fromId("1.14.4").orElseThrow()) < 0 ?
                    MappingChannelRegistry.MCP :
                    MappingChannelRegistry.MOJMAP;
            })
            .bindComboBoxTo(mappingChannelComboBox)
            .keyFunction(MappingChannel::translationKey)
            .valueOfFunction(MappingChannelRegistry.REGISTRY::get)
            .translate(true)
            .addTransformer(mappingChannelComboBox, mappingVersionComboBox, channel -> {
                if(channel == null) {
                    Railroad.LOGGER.error("Mapping channel is null when transforming for mapping versions");
                    return null;
                }

                ComboBox<String> comboBox = mappingVersionComboBox.get();
                if (comboBox == null) {
                    Railroad.LOGGER.error("Mapping version ComboBox is null when transforming for mapping channel {}", channel);
                    return null;
                }

                List<String> newVersions = channel.listVersionsFor(getSelectedMinecraftVersion());
                comboBox.getItems().setAll(newVersions);
                if (newVersions.isEmpty()) {
                    Railroad.LOGGER.error("No mapping versions found for channel {} and Minecraft version {}", channel, getSelectedMinecraftVersion());
                    return null;
                }

                return newVersions.getLast();
            })
            .build();

        ComboBoxComponent<String> mappingVersionComponent = FormComponent.comboBox("MappingVersion", "railroad.project.creation.mapping_version", String.class)
            .required()
            .bindComboBoxTo(mappingVersionComboBox)
            .cellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item);
                    }
                }
            })
            .buttonCell(new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item);
                    }
                }
            })
            .translate(false)
            .defaultValue(() -> {
                ComboBox<MappingChannel> channelComboBox = mappingChannelComboBox.get();
                if (channelComboBox == null)
                    return null;

                MappingChannel channel = channelComboBox.getValue();
                if (channel == null)
                    return null;

                List<String> versions = channel.listVersionsFor(getSelectedMinecraftVersion());
                if (versions.isEmpty()) {
                    Railroad.LOGGER.error("No mapping versions found for default mapping version");
                    return null;
                }

                return versions.getLast();
            })
            .items(MappingChannelRegistry.MOJMAP.listVersionsFor(getSelectedMinecraftVersion()))
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

        projectPathComponent.getComponent().addInformationLabel("railroad.project.creation.location.info", createdAtPath, createdAtPath.get());
    }

    private MinecraftVersion getSelectedMinecraftVersion() {
        MinecraftVersion version = minecraftVersionComboBox.get().getValue();
        if (version != null)
            return version;

        List<MinecraftVersion> items = minecraftVersionComboBox.get().getItems();
        if (items.isEmpty())
            return null;

        return items.getFirst();
    }

    protected static ForgeProjectData createData(FormData formData) {
        String projectName = formData.getString("ProjectName");
        var projectPath = Path.of(formData.getString("ProjectPath"));
        boolean createGit = formData.getBoolean("CreateGit");
        License license = formData.getEnum("License", License.class);
        String licenseCustom = license == License.CUSTOM ? formData.getString("CustomLicense") : null;
        MinecraftVersion minecraftVersion = formData.get("MinecraftVersion", MinecraftVersion.class);
        String forgeVersion = formData.get("ForgeVersion", String.class);
        String modId = formData.getString("ModId");
        String modName = formData.getString("ModName");
        String mainClass = formData.getString("MainClass");
        boolean useMixins = formData.getBoolean("UseMixins");
        boolean useAccessTransformer = formData.getBoolean("UseAccessTransformer");
        boolean genRunFolders = formData.getBoolean("GenRunFolders");
        MappingChannel mappingChannel = formData.get("MappingChannel", MappingChannel.class);
        String mappingVersion = formData.get("MappingVersion", String.class);
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

    private List<MinecraftVersion> resolveForgeMinecraftVersions() {
        return ForgeVersionService.INSTANCE.listAllVersions()
            .stream()
            .map(ForgeVersionService::toMinecraftVersion)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();
    }
}
