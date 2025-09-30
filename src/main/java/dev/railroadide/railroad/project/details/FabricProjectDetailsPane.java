package dev.railroadide.railroad.project.details;

import dev.railroadide.core.form.Form;
import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormData;
import dev.railroadide.core.form.FormSection;
import dev.railroadide.core.form.impl.*;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.License;
import dev.railroadide.railroad.project.data.FabricProjectData;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.fabric.FabricApiVersionService;
import dev.railroadide.railroad.project.minecraft.fabric.FabricLoaderVersionService;
import dev.railroadide.railroad.project.minecraft.fabric.FabricLoaderVersionService.FabricLoaderVersion;
import dev.railroadide.railroad.project.minecraft.mappings.channels.MappingChannel;
import dev.railroadide.railroad.project.minecraft.mappings.channels.MappingChannelRegistry;
import dev.railroadide.railroad.project.creation.FabricProjectCreationPane;
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

public class FabricProjectDetailsPane extends RRVBox {
    private final StringProperty createdAtPath = new SimpleStringProperty(ProjectValidators.getRepairedPath(System.getProperty("user.home") + "\\"));

    private final ObjectProperty<TextField> projectNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> projectPathField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> createGitCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<License>> licenseComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> licenseCustomField = new SimpleObjectProperty<>();

    private final ObjectProperty<ComboBox<MinecraftVersion>> minecraftVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<FabricLoaderVersion>> fabricLoaderVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> includeFapiCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<String>> fapiVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> mainClassField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> useAccessWidenerCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> splitSourcesCheckBox = new SimpleObjectProperty<>();

    private final ObjectProperty<ComboBox<MappingChannel>> mappingChannelComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<String>> mappingVersionComboBox = new SimpleObjectProperty<>();

    private final ObjectProperty<TextField> authorField = new SimpleObjectProperty<>(new TextField(System.getProperty("user.name"))); // optional
    private final ObjectProperty<TextArea> descriptionArea = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> issuesField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> homepageField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> sourcesField = new SimpleObjectProperty<>(); // optional

    private final ObjectProperty<TextField> groupIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> artifactIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> versionField = new SimpleObjectProperty<>();

    private final AtomicBoolean hasTypedInProjectName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModid = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInMainClass = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInArtifactId = new AtomicBoolean(false);

    public FabricProjectDetailsPane() {
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

        List<MinecraftVersion> supportedVersions = resolveFabricMinecraftVersions();
        MinecraftVersion latestVersion = determineDefaultMinecraftVersion(supportedVersions);
        ComboBoxComponent<MinecraftVersion> minecraftVersionComponent = FormComponent.comboBox("MinecraftVersion", "railroad.project.creation.minecraft_version", MinecraftVersion.class)
            .required()
            .items(supportedVersions)
            .defaultValue(() -> latestVersion)
            .bindComboBoxTo(minecraftVersionComboBox)
            .keyFunction(MinecraftVersion::id)
            .valueOfFunction(string -> MinecraftVersion.fromId(string).orElse(null))
            .translate(false)
            .addTransformer(minecraftVersionComboBox, fabricLoaderVersionComboBox, version -> {
                if(version == null) {
                    Railroad.LOGGER.error("Minecraft version is null when transforming for Fabric Loader versions");
                    return null;
                }

                ComboBox<FabricLoaderVersion> comboBox = fabricLoaderVersionComboBox.get();
                if (comboBox == null) {
                    Railroad.LOGGER.error("Fabric Loader ComboBox is null when transforming for Minecraft version {}", version);
                    return null;
                }

                List<FabricLoaderVersion> newVersions = FabricLoaderVersionService.INSTANCE.listVersionsFor(version);
                comboBox.getItems().setAll(newVersions);
                if (newVersions.isEmpty()) {
                    Railroad.LOGGER.error("No Fabric Loader versions found for Minecraft version {}", version);
                    return null;
                }

                FabricLoaderVersion latestFor = FabricLoaderVersionService.INSTANCE.getLatestVersion(version);
                if (latestFor == null) {
                    Railroad.LOGGER.error("No latest Fabric Loader version found for Minecraft version {}", version);
                    latestFor = newVersions.getFirst();
                }

                return latestFor;
            })
            .addTransformer(minecraftVersionComboBox, fapiVersionComboBox, version -> {
                if(version == null) {
                    Railroad.LOGGER.error("Minecraft version is null when transforming for Fabric API versions");
                    return null;
                }

                ComboBox<String> comboBox = fapiVersionComboBox.get();
                if (comboBox == null) {
                    Railroad.LOGGER.error("Fabric API ComboBox is null when transforming for Minecraft version {}", version);
                    return null;
                }

                List<String> newVersions = FabricApiVersionService.INSTANCE.listVersionsFor(version);
                comboBox.getItems().setAll(newVersions);
                if (newVersions.isEmpty()) {
                    Railroad.LOGGER.error("No Fabric API versions found for Minecraft version {}", version);
                    return null;
                }

                String latestFor = FabricApiVersionService.INSTANCE.latestFor(version).orElse(null);
                if (latestFor == null) {
                    Railroad.LOGGER.error("No latest Fabric API version found for Minecraft version {}", version);
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

                return MappingChannelRegistry.YARN;
            })
            .build();

        ComboBoxComponent<FabricLoaderVersion> fabricLoaderVersionComponent = FormComponent.comboBox("FabricLoaderVersion", "railroad.project.creation.fabric_loader_version", FabricLoaderVersion.class)
            .required()
            .bindComboBoxTo(fabricLoaderVersionComboBox)
            .keyFunction(version -> version.loaderVersion().version())
            .valueOfFunction(string -> {
                if (string == null)
                    return null;

                ComboBox<FabricLoaderVersion> comboBox = fabricLoaderVersionComboBox.get();
                if (comboBox == null)
                    return null;

                return comboBox.getItems().stream()
                    .filter(version -> Objects.equals(version.loaderVersion().version(), string))
                    .findFirst()
                    .orElse(null);
            })
            .translate(false)
            .cellFactory(param -> new StarableListCell<>(
                version -> Objects.equals(version, latestFabricLoaderVersion()),
                version -> false,
                fabricLoaderVersion -> fabricLoaderVersion.loaderVersion().version()))
            .buttonCell(new StarableListCell<>(
                version -> Objects.equals(version, latestFabricLoaderVersion()),
                version -> false,
                fabricLoaderVersion -> fabricLoaderVersion.loaderVersion().version()))
            .defaultValue(() -> latestVersion == null ? null : FabricLoaderVersionService.INSTANCE.getLatestVersion(latestVersion))
            .items(latestVersion == null ? Collections.emptyList() : FabricLoaderVersionService.INSTANCE.listVersionsFor(latestVersion))
            .build();

        CheckBoxComponent includeFapiComponent = FormComponent.checkBox("IncludeFapi", "railroad.project.creation.include_fapi")
            .bindCheckBoxTo(includeFapiCheckBox)
            .selected(true)
            .build();

        ComboBoxComponent<String> fapiVersionComponent = FormComponent.comboBox("FabricApiVersion", "railroad.project.creation.fabric_api_version", String.class)
            .required()
            .bindComboBoxTo(fapiVersionComboBox)
            .keyFunction(Objects::toIdentityString)
            .valueOfFunction(string -> fapiVersionComboBox.get().getItems().stream()
                .filter(version -> version.equals(string))
                .findFirst().orElse(null))
            .translate(false)
            .cellFactory(param -> new StarableListCell<>(
                version -> Objects.equals(version, latestFabricApiVersion()),
                version -> false,
                Function.identity()))
            .buttonCell(new StarableListCell<>(
                version -> Objects.equals(version, latestFabricApiVersion()),
                version -> false,
                Function.identity()))
            .defaultValue(this::latestFabricApiVersion)
            .items(latestVersion == null ? Collections.emptyList() : FabricApiVersionService.INSTANCE.listVersionsFor(latestVersion))
            .visible(ProjectValidators.createBinding(includeFapiCheckBox.get().selectedProperty()))
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

        CheckBoxComponent useAccessWidenerComponent = FormComponent.checkBox("UseAccessWidener", "railroad.project.creation.use_access_widener")
            .bindCheckBoxTo(useAccessWidenerCheckBox)
            .build();

        CheckBoxComponent splitSourcesComponent = FormComponent.checkBox("SplitSources", "railroad.project.creation.split_sources")
            .bindCheckBoxTo(splitSourcesCheckBox)
            .build();

        ComboBoxComponent<MappingChannel> mappingChannelComponent = FormComponent.comboBox("MappingChannel", "railroad.project.creation.mapping_channel", MappingChannel.class)
            .required()
            .items(MappingChannelRegistry.findValidMappingChannels(getSelectedMinecraftVersion()))
            .defaultValue(() -> MappingChannelRegistry.YARN)
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
            .items(MappingChannelRegistry.YARN.listVersionsFor(getSelectedMinecraftVersion()))
            .build();

        TextFieldComponent authorComponent = FormComponent.textField("Author", "railroad.project.creation.author")
            .bindTextFieldTo(authorField)
            .promptText("railroad.project.creation.author.prompt")
            .validator(ProjectValidators::validateAuthor)
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

        TextFieldComponent homepageComponent = FormComponent.textField("Homepage", "railroad.project.creation.homepage")
            .bindTextFieldTo(homepageField)
            .promptText("railroad.project.creation.homepage.prompt")
            .validator(field -> ProjectValidators.validateGenericUrl(field, "homepage"))
            .build();

        TextFieldComponent sourcesComponent = FormComponent.textField("Sources", "railroad.project.creation.sources")
            .bindTextFieldTo(sourcesField)
            .promptText("railroad.project.creation.sources.prompt")
            .validator(field -> ProjectValidators.validateGenericUrl(field, "sources"))
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
                .appendComponent(fabricLoaderVersionComponent)
                .appendComponent(includeFapiComponent)
                .appendComponent(fapiVersionComponent)
                .appendComponent(modIdComponent)
                .appendComponent(modNameComponent)
                .appendComponent(mainClassComponent)
                .appendComponent(useAccessWidenerComponent)
                .appendComponent(splitSourcesComponent))
            .appendSection(FormSection.create("railroad.project.creation.section.mappings")
                .borderColor(Color.DARKGRAY)
                .appendComponent(mappingChannelComponent)
                .appendComponent(mappingVersionComponent))
            .appendSection(FormSection.create("railroad.project.creation.section.optional")
                .borderColor(Color.SLATEGRAY)
                .appendComponent(authorComponent)
                .appendComponent(descriptionComponent)
                .appendComponent(issuesComponent)
                .appendComponent(homepageComponent)
                .appendComponent(sourcesComponent))
            .appendSection(FormSection.create("railroad.project.creation.section.maven")
                .borderColor(Color.DARKGRAY)
                .appendComponent(groupIdComponent)
                .appendComponent(artifactIdComponent)
                .appendComponent(versionComponent))
            .disableResetButton()
            .onSubmit((theForm, formData) -> {
                if (theForm.validate()) {
                    FabricProjectData data = createData(formData);
                    getScene().setRoot(new FabricProjectCreationPane(data));
                } else {
                    theForm.runValidation(); // Show validation errors
                }
            })
            .build();

        getChildren().add(form.createUI());

        projectPathComponent.getComponent().addInformationLabel("railroad.project.creation.location.info", createdAtPath, createdAtPath.get());
    }

    private String latestFabricApiVersion() {
        MinecraftVersion minecraftVersion = getSelectedMinecraftVersion();
        if (minecraftVersion == null)
            return null;

        return FabricApiVersionService.INSTANCE.latestFor(minecraftVersion).orElse(null);
    }

    private FabricLoaderVersion latestFabricLoaderVersion() {
        MinecraftVersion minecraftVersion = getSelectedMinecraftVersion();
        if (minecraftVersion == null)
            return null;

        return FabricLoaderVersionService.INSTANCE.getLatestVersion(minecraftVersion);
    }

    protected static FabricProjectData createData(FormData formData) {
        String projectName = formData.getString("ProjectName");
        var projectPath = Path.of(formData.getString("ProjectPath"));
        boolean createGit = formData.getBoolean("CreateGit");
        License license = formData.getEnum("License", License.class);
        String licenseCustom = license == License.CUSTOM ? formData.getString("CustomLicense") : null;
        MinecraftVersion minecraftVersion = formData.get("MinecraftVersion", MinecraftVersion.class);
        FabricLoaderVersion fabricVersion = formData.get("FabricLoaderVersion", FabricLoaderVersion.class);
        Optional<String> fapiVersion = Optional.ofNullable(formData.getBoolean("IncludeFapi") ? formData.get("FabricApiVersion", String.class) : null);
        String modId = formData.getString("ModId");
        String modName = formData.getString("ModName");
        String mainClass = formData.getString("MainClass");
        boolean useAccessWidener = formData.getBoolean("UseAccessWidener");
        boolean splitSources = formData.getBoolean("SplitSources");
        MappingChannel mappingChannel = formData.get("MappingChannel", MappingChannel.class);
        String mappingVersion = formData.get("MappingVersion", String.class);
        Optional<String> author = Optional.ofNullable(formData.getString("Author")).filter(s -> !s.isBlank());
        Optional<String> description = Optional.ofNullable(formData.getString("Description")).filter(s -> !s.isBlank());
        Optional<String> issues = Optional.ofNullable(formData.getString("Issues")).filter(s -> !s.isBlank());
        Optional<String> homepage = Optional.ofNullable(formData.getString("Homepage")).filter(s -> !s.isBlank());
        Optional<String> sources = Optional.ofNullable(formData.getString("Sources")).filter(s -> !s.isBlank());
        String groupId = formData.getString("GroupId");
        String artifactId = formData.getString("ArtifactId");
        String version = formData.getString("Version");

        return new FabricProjectData(projectName, projectPath, createGit, license, licenseCustom, minecraftVersion, fabricVersion, fapiVersion, modId, modName, mainClass, useAccessWidener, splitSources, mappingChannel, mappingVersion, author, description, issues, homepage, sources, groupId, artifactId, version);
    }

    private List<MinecraftVersion> resolveFabricMinecraftVersions() {
        return FabricApiVersionService.INSTANCE.listAllVersions()
            .stream()
            .map(FabricApiVersionService::toMinecraftVersion)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();
    }

    private MinecraftVersion determineDefaultMinecraftVersion(List<MinecraftVersion> versions) {
        if (!versions.isEmpty())
            return versions.getFirst();

        MinecraftVersion latestStable = MinecraftVersion.getLatestStableVersion();
        if (latestStable != null)
            return latestStable;

        return MinecraftVersion.getLatestSnapshotVersion();
    }

    private MinecraftVersion getSelectedMinecraftVersion() {
        ComboBox<MinecraftVersion> comboBox = minecraftVersionComboBox.get();
        return comboBox == null ? null : comboBox.getValue();
    }
}
