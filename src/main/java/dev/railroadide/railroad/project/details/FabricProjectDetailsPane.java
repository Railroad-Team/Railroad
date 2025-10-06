package dev.railroadide.railroad.project.details;

import dev.railroadide.core.form.Form;
import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormData;
import dev.railroadide.core.form.FormSection;
import dev.railroadide.core.form.impl.*;
import dev.railroadide.core.project.License;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.project.creation.ProjectCreationService;
import dev.railroadide.core.project.creation.ProjectServiceRegistry;
import dev.railroadide.core.project.creation.service.GradleService;
import dev.railroadide.core.project.minecraft.MappingChannel;
import dev.railroadide.core.switchboard.pojo.FabricLoaderVersion;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.railroad.project.MappingChannelRegistry;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.project.creation.ui.ProjectCreationPane;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
import dev.railroadide.railroad.switchboard.repositories.FabricApiVersionRepository;
import dev.railroadide.railroad.utility.ExpiringCache;
import dev.railroadide.railroad.welcome.project.ui.widget.StarableListCell;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class FabricProjectDetailsPane extends RRVBox {
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

    private final ObjectProperty<TextField> authorField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextArea> descriptionArea = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> issuesField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> homepageField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> sourcesField = new SimpleObjectProperty<>(); // optional

    private final AtomicBoolean hasTypedInProjectName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModid = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInMainClass = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInArtifactId = new AtomicBoolean(false);

    private final ObjectProperty<FabricLoaderVersion> latestFabricLoaderVersionProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<String> latestFabricApiVersionProperty = new SimpleObjectProperty<>();

    private static final ExpiringCache<List<MinecraftVersion>> FABRIC_MINECRAFT_VERSIONS_CACHE = new ExpiringCache<>(Duration.ofHours(3));

    public FabricProjectDetailsPane() {
        var projectBasics = new ProjectBasicsComponents();
        var projectCoordinates = new ProjectCoordinatesComponents();

        StringProperty createdAtPath = projectBasics.createdPathProperty();
        ObjectProperty<TextField> projectNameField = projectBasics.projectNameFieldProperty();
        ObjectProperty<TextField> artifactIdField = projectCoordinates.artifactIdFieldProperty();

        projectBasics.projectNameBuilder()
            .keyTypedHandler(event -> {
                TextField field = projectNameField.get();
                String text = field == null ? "" : field.getText();
                if (!hasTypedInProjectName.get() && !text.isBlank())
                    hasTypedInProjectName.set(true);
                else if (hasTypedInProjectName.get() && text.isBlank())
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
            });

        ProjectBasicsComponents.Components basicsComponents = projectBasics.build();

        projectCoordinates.artifactIdBuilder()
            .keyTypedHandler(event -> {
                TextField field = artifactIdField.get();
                String text = field == null ? "" : field.getText();
                if (!hasTypedInArtifactId.get() && !text.isBlank())
                    hasTypedInArtifactId.set(true);
                else if (hasTypedInArtifactId.get() && text.isBlank())
                    hasTypedInArtifactId.set(false);
            });

        ProjectCoordinatesComponents.Components coordinateComponents = projectCoordinates.build();

        TextFieldComponent projectNameComponent = basicsComponents.projectNameComponent();
        DirectoryChooserComponent projectPathComponent = basicsComponents.projectPathComponent();
        CheckBoxComponent createGitComponent = basicsComponents.createGitComponent();
        ComboBoxComponent<License> licenseComponent = basicsComponents.licenseComponent();
        TextFieldComponent licenseCustomComponent = basicsComponents.licenseCustomComponent();

        TextFieldComponent groupIdComponent = coordinateComponents.groupIdComponent();
        TextFieldComponent artifactIdComponent = coordinateComponents.artifactIdComponent();
        TextFieldComponent versionComponent = coordinateComponents.versionComponent();

        ComboBoxComponent<MinecraftVersion> minecraftVersionComponent = FormComponent.comboBox("MinecraftVersion", "railroad.project.creation.minecraft_version", MinecraftVersion.class)
            .required()
            .items(() -> FABRIC_MINECRAFT_VERSIONS_CACHE.getIfPresent()
                .map(List::copyOf)
                .orElseGet(Collections::emptyList))
            .defaultValue(() -> {
                List<MinecraftVersion> versions = FABRIC_MINECRAFT_VERSIONS_CACHE.getIfPresent()
                    .orElseGet(Collections::emptyList);
                return determineDefaultMinecraftVersion(versions);
            })
            .bindComboBoxTo(minecraftVersionComboBox)
            .keyFunction(MinecraftVersion::id)
            .valueOfFunction(string -> {
                try {
                    return SwitchboardRepositories.MINECRAFT.getVersionSync(string).orElse(null);
                } catch (ExecutionException | InterruptedException exception) {
                    throw new RuntimeException(exception);
                }
            })
            .translate(false)
            .addAsyncTransformer(minecraftVersionComboBox, this::applyFabricLoaderVersions, this::fetchFabricLoaderVersions)
            .addAsyncTransformer(minecraftVersionComboBox, this::applyFabricApiVersions, this::fetchFabricApiVersions)
            .addTransformer(minecraftVersionComboBox, mappingChannelComboBox, version -> {
                if (version == null) {
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
            .keyFunction(FabricLoaderVersion::version)
            .valueOfFunction(string -> {
                if (string == null)
                    return null;

                ComboBox<FabricLoaderVersion> comboBox = fabricLoaderVersionComboBox.get();
                if (comboBox == null)
                    return null;

                return comboBox.getItems().stream()
                    .filter(version -> Objects.equals(version.version(), string))
                    .findFirst()
                    .orElse(null);
            })
            .translate(false)
            .cellFactory(param -> new StarableListCell<>(
                version -> Objects.equals(version, latestFabricLoaderVersion()),
                version -> false,
                FabricLoaderVersion::version))
            .buttonCell(new StarableListCell<>(
                version -> Objects.equals(version, latestFabricLoaderVersion()),
                version -> false,
                FabricLoaderVersion::version))
            .defaultValue(this::latestFabricLoaderVersion)
            .items(Collections::emptyList)
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
            .items(Collections::emptyList)
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
            .selected(true)
            .build();

        ComboBoxComponent<MappingChannel> mappingChannelComponent = FormComponent.comboBox("MappingChannel", "railroad.project.creation.mapping_channel", MappingChannel.class)
            .required()
            .items(() -> MappingChannelRegistry.findValidMappingChannels(getSelectedMinecraftVersion()))
            .defaultValue(() -> MappingChannelRegistry.YARN)
            .bindComboBoxTo(mappingChannelComboBox)
            .keyFunction(MappingChannel::translationKey)
            .valueOfFunction(MappingChannel.REGISTRY::get)
            .translate(true)
            .addTransformer(mappingChannelComboBox, mappingVersionComboBox, channel -> {
                if (channel == null) {
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
            .items(() -> MappingChannelRegistry.YARN.listVersionsFor(getSelectedMinecraftVersion()))
            .build();

        TextFieldComponent authorComponent = FormComponent.textField("Author", "railroad.project.creation.author")
            .bindTextFieldTo(authorField)
            .promptText("railroad.project.creation.author.prompt")
            .validator(ProjectValidators::validateAuthor)
            .text(System.getProperty("user.name"))
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
                    ProjectData data = createData(formData);
                    var creationPane = new ProjectCreationPane(data);

                    ProjectServiceRegistry serviceRegistry = Services.PROJECT_SERVICE_REGISTRY;
                    serviceRegistry.get(GradleService.class).setOutputStream(creationPane.getTaos());
                    creationPane.initService(new ProjectCreationService(Services.PROJECT_CREATION_PIPELINE.createProject(
                        ProjectTypeRegistry.FABRIC,
                        serviceRegistry
                    ), creationPane.getContext()));

                    getScene().setRoot(creationPane);
                } else {
                    theForm.runValidation();
                }
            })
            .build();

        getChildren().add(form.createUI());

        projectPathComponent.getComponent().addInformationLabel("railroad.project.creation.location.info", createdAtPath, createdAtPath.get());

        loadFabricMinecraftVersionsAsync();
    }

    private String latestFabricApiVersion() {
        return latestFabricApiVersionProperty.get();
    }

    private FabricLoaderVersion latestFabricLoaderVersion() {
        return latestFabricLoaderVersionProperty.get();
    }

    protected static ProjectData createData(FormData formData) {
        String projectName = formData.getString("ProjectName");
        var projectPath = Path.of(formData.getString("ProjectPath"));
        boolean createGit = formData.getBoolean("CreateGit");
        License license = formData.get("License", License.class);
        String licenseCustom = license == LicenseRegistry.CUSTOM ? formData.getString("CustomLicense") : null;
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

        var data = new ProjectData();

        return data;
    }

    private void loadFabricMinecraftVersionsAsync() {
        FABRIC_MINECRAFT_VERSIONS_CACHE.getIfPresent().ifPresent(this::applyMinecraftVersions);

        resolveFabricMinecraftVersions().whenComplete((versions, throwable) -> {
            if (throwable != null) {
                Railroad.LOGGER.error("Failed to fetch Minecraft versions for Fabric", throwable);
                return;
            }

            Platform.runLater(() -> applyMinecraftVersions(versions));
        });
    }

    private void applyMinecraftVersions(List<MinecraftVersion> versions) {
        ComboBox<MinecraftVersion> comboBox = minecraftVersionComboBox.get();
        if (comboBox == null)
            return;

        comboBox.getItems().setAll(versions);
        if (versions.isEmpty()) {
            comboBox.setValue(null);
            return;
        }

        MinecraftVersion current = comboBox.getValue();
        if (current != null && versions.contains(current))
            return;

        comboBox.setValue(determineDefaultMinecraftVersion(versions));
    }

    private CompletableFuture<FabricLoaderVersionsPayload> fetchFabricLoaderVersions(MinecraftVersion version) {
        Platform.runLater(() -> {
            ComboBox<FabricLoaderVersion> comboBox = fabricLoaderVersionComboBox.get();
            if (comboBox != null) {
                comboBox.getItems().clear();
                comboBox.setValue(null);
            }
            latestFabricLoaderVersionProperty.set(null);
        });

        if (version == null)
            return CompletableFuture.completedFuture(new FabricLoaderVersionsPayload(null, Collections.emptyList(), null));

        String versionId = version.id();
        CompletableFuture<List<FabricLoaderVersion>> versionsFuture = SwitchboardRepositories.FABRIC_LOADER.getVersionsFor(versionId);
        CompletableFuture<FabricLoaderVersion> latestFuture = SwitchboardRepositories.FABRIC_LOADER.getLatestVersionFor(versionId);

        return versionsFuture.thenCombine(latestFuture, (versions, latest) -> new FabricLoaderVersionsPayload(
                version,
                versions == null ? Collections.emptyList() : versions,
                latest))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to fetch Fabric Loader versions for Minecraft version {}", version, throwable);
                return new FabricLoaderVersionsPayload(version, Collections.emptyList(), null);
            });
    }

    private void applyFabricLoaderVersions(FabricLoaderVersionsPayload payload) {
        ComboBox<FabricLoaderVersion> comboBox = fabricLoaderVersionComboBox.get();
        if (comboBox == null)
            return;

        if (!Objects.equals(payload.contextVersion(), getSelectedMinecraftVersion()))
            return;

        List<FabricLoaderVersion> versions = payload.versions();
        FabricLoaderVersion latest = payload.latest();

        comboBox.getItems().setAll(versions);
        latestFabricLoaderVersionProperty.set(latest);

        FabricLoaderVersion currentValue = comboBox.getValue();
        if (currentValue != null && versions.contains(currentValue))
            return;

        FabricLoaderVersion selection = null;
        if (latest != null && versions.contains(latest))
            selection = latest;
        else if (!versions.isEmpty())
            selection = versions.getFirst();

        comboBox.setValue(selection);
    }

    private CompletableFuture<FabricApiVersionsPayload> fetchFabricApiVersions(MinecraftVersion version) {
        Platform.runLater(() -> {
            ComboBox<String> comboBox = fapiVersionComboBox.get();
            if (comboBox != null) {
                comboBox.getItems().clear();
                comboBox.setValue(null);
            }
            latestFabricApiVersionProperty.set(null);
        });

        if (version == null)
            return CompletableFuture.completedFuture(new FabricApiVersionsPayload(null, Collections.emptyList(), null));

        String versionId = version.id();
        CompletableFuture<List<String>> versionsFuture = SwitchboardRepositories.FABRIC_API.getVersionsFor(versionId);
        CompletableFuture<String> latestFuture = SwitchboardRepositories.FABRIC_API.getLatestVersionFor(versionId);

        return versionsFuture.thenCombine(latestFuture, (versions, latest) -> new FabricApiVersionsPayload(
                version,
                versions == null ? Collections.emptyList() : versions,
                latest))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to fetch Fabric API versions for Minecraft version {}", version, throwable);
                return new FabricApiVersionsPayload(version, Collections.emptyList(), null);
            });
    }

    private void applyFabricApiVersions(FabricApiVersionsPayload payload) {
        ComboBox<String> comboBox = fapiVersionComboBox.get();
        if (comboBox == null)
            return;

        if (!Objects.equals(payload.contextVersion(), getSelectedMinecraftVersion()))
            return;

        List<String> versions = payload.versions();
        String latest = payload.latest();

        comboBox.getItems().setAll(versions);
        latestFabricApiVersionProperty.set(latest);

        String currentValue = comboBox.getValue();
        if (currentValue != null && versions.contains(currentValue))
            return;

        String selection = null;
        if (latest != null && versions.contains(latest))
            selection = latest;
        else if (!versions.isEmpty())
            selection = versions.getFirst();

        comboBox.setValue(selection);
    }

    private CompletableFuture<List<MinecraftVersion>> resolveFabricMinecraftVersions() {
        return FABRIC_MINECRAFT_VERSIONS_CACHE.getAsync(() ->
            SwitchboardRepositories.FABRIC_API.getAllVersions()
                .thenApply(versions -> versions.stream()
                    .map(FabricApiVersionRepository::fapiToMinecraftVersion)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .distinct()
                    .map(version -> {
                        try {
                            return SwitchboardRepositories.MINECRAFT.getVersionSync(version).orElse(null);
                        } catch (ExecutionException | InterruptedException exception) {
                            Railroad.LOGGER.error("Failed to fetch Minecraft version {}", version, exception);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.reverseOrder())
                    .toList()
                )
        );
    }

    private record FabricLoaderVersionsPayload(MinecraftVersion contextVersion, List<FabricLoaderVersion> versions,
                                               FabricLoaderVersion latest) {
    }

    private record FabricApiVersionsPayload(MinecraftVersion contextVersion, List<String> versions, String latest) {
    }

    private MinecraftVersion determineDefaultMinecraftVersion(List<MinecraftVersion> versions) {
        if (versions == null || versions.isEmpty())
            return null;

        return versions.stream()
            .filter(version -> version != null && version.getType() == MinecraftVersion.Type.RELEASE)
            .findFirst()
            .orElseGet(versions::getFirst);
    }

    private MinecraftVersion getSelectedMinecraftVersion() {
        ComboBox<MinecraftVersion> comboBox = minecraftVersionComboBox.get();
        return comboBox == null ? null : comboBox.getValue();
    }
}
