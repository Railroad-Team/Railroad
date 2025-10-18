package dev.railroadide.railroad.project.details.deprecated;

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
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.project.DisplayTest;
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.railroad.project.MappingChannelRegistry;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.project.creation.ui.ProjectCreationPane;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.railroad.project.details.ProjectValidators;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
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

public class NeoforgeProjectDetailsPane extends RRVBox {
    private final ObjectProperty<ComboBox<MinecraftVersion>> minecraftVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<String>> neoforgeVersionComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<String> latestNeoforgeVersionProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modIdField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> modNameField = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> mainClassField = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> useMixinsCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> useAccessTransformerCheckBox = new SimpleObjectProperty<>();
    private final ObjectProperty<CheckBox> genRunFoldersCheckBox = new SimpleObjectProperty<>();

    private final ObjectProperty<ComboBox<MappingChannel>> mappingChannelComboBox = new SimpleObjectProperty<>();
    private final ObjectProperty<ComboBox<String>> mappingVersionComboBox = new SimpleObjectProperty<>();

    private final ObjectProperty<TextField> authorField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> creditsField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextArea> descriptionArea = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> issuesField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> updateJsonUrlField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<TextField> displayUrlField = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<ComboBox<DisplayTest>> displayTestComboBox = new SimpleObjectProperty<>(); // optional
    private final ObjectProperty<CheckBox> clientSideOnlyCheckBox = new SimpleObjectProperty<>(); // optional

    private final AtomicBoolean hasTypedInProjectName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModid = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInModName = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInMainClass = new AtomicBoolean(false);
    private final AtomicBoolean hasTypedInArtifactId = new AtomicBoolean(false);

    private static final ExpiringCache<List<MinecraftVersion>> NEOFORGE_MINECRAFT_VERSIONS_CACHE = new ExpiringCache<>(Duration.ofHours(3));

    public NeoforgeProjectDetailsPane() {
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
            })
            .addTransformer(projectNameField, mainClassField, text -> {
                if (!hasTypedInMainClass.get() || mainClassField.get().getText().isBlank()) {
                    String[] words = text.split("[ _-]+");
                    var pascalCase = new StringBuilder();
                    for (String word : words) {
                        if (word.isBlank())
                            continue;

                        pascalCase.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
                    }
                    return pascalCase.toString().replaceAll("[^a-zA-Z0-9]", "");
                }
                return text;
            })
            .addTransformer(projectNameField, artifactIdField, text -> {
                if (!hasTypedInArtifactId.get() || artifactIdField.get().getText().isBlank())
                    return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");

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
            .items(() -> NEOFORGE_MINECRAFT_VERSIONS_CACHE.getIfPresent()
                .map(List::copyOf)
                .orElseGet(Collections::emptyList))
            .defaultValue(() -> determineDefaultMinecraftVersion(
                NEOFORGE_MINECRAFT_VERSIONS_CACHE.getIfPresent().orElseGet(Collections::emptyList)))
            .bindComboBoxTo(minecraftVersionComboBox)
            .keyFunction(MinecraftVersion::id)
            .valueOfFunction(id -> {
                try {
                    return SwitchboardRepositories.MINECRAFT.getVersionSync(id).orElse(null);
                } catch (ExecutionException exception) {
                    throw new RuntimeException(exception);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(exception);
                }
            })
            .translate(false)
            .addAsyncTransformer(minecraftVersionComboBox, this::applyNeoforgeVersions, this::fetchNeoforgeVersions)
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

                return MappingChannelRegistry.PARCHMENT;
            })
            .build();

        ComboBoxComponent<String> neoforgeVersionComponent = FormComponent.comboBox("NeoforgeVersion", "railroad.project.creation.neoforge_version", String.class)
            .required()
            .items(Collections::emptyList)
            .defaultValue(this::latestNeoforgeVersion)
            .bindComboBoxTo(neoforgeVersionComboBox)
            .cellFactory(param -> new StarableListCell<>(
                NeoforgeProjectDetailsPane::isPrerelease,
                version -> Objects.equals(version, latestNeoforgeVersion()),
                Function.identity()))
            .buttonCell(new StarableListCell<>(
                NeoforgeProjectDetailsPane::isPrerelease,
                version -> Objects.equals(version, latestNeoforgeVersion()),
                Function.identity()))
            .translate(false)
            .build();

        loadNeoforgeMinecraftVersionsAsync();

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
            .items(() -> {
                MinecraftVersion selectedVersion = getSelectedMinecraftVersion();
                return selectedVersion == null ? Collections.emptyList() : MappingChannelRegistry.findValidMappingChannels(selectedVersion);
            })
            .defaultValue(() -> MappingChannelRegistry.PARCHMENT)
            .bindComboBoxTo(mappingChannelComboBox)
            .keyFunction(MappingChannel::translationKey)
            .valueOfFunction(MappingChannel.REGISTRY::get)
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

                MinecraftVersion selectedVersion = getSelectedMinecraftVersion();
                if (selectedVersion == null) {
                    comboBox.getItems().clear();
                    return null;
                }

                List<String> newVersions = channel.listVersionsFor(selectedVersion);
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

                MinecraftVersion selectedVersion = getSelectedMinecraftVersion();
                if (selectedVersion == null)
                    return null;

                List<String> versions = channel.listVersionsFor(selectedVersion);
                if (versions.isEmpty()) {
                    Railroad.LOGGER.error("No mapping versions found for default mapping version");
                    return null;
                }

                return versions.getLast();
            })
            .items(() -> {
                MinecraftVersion selectedVersion = getSelectedMinecraftVersion();
                if (selectedVersion == null)
                    return Collections.emptyList();

                return MappingChannelRegistry.PARCHMENT.listVersionsFor(selectedVersion);
            })
            .build();

        TextFieldComponent authorComponent = FormComponent.textField("Author", "railroad.project.creation.author")
            .bindTextFieldTo(authorField)
            .promptText("railroad.project.creation.author.prompt")
            .validator(ProjectValidators::validateAuthor)
            .text(System.getProperty("user.name", ""))
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
            .listener((node, observable, oldValue, newValue) -> {
                displayTestComboBox.get().setValue(newValue ? DisplayTest.IGNORE_ALL_VERSION : DisplayTest.MATCH_VERSION);
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
                .appendComponent(neoforgeVersionComponent)
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
                    ProjectData data = createData(formData);
                    var creationPane = new ProjectCreationPane(data);

                    ProjectServiceRegistry serviceRegistry = Services.PROJECT_SERVICE_REGISTRY;
                    serviceRegistry.get(GradleService.class).setOutputStream(creationPane.getTaos());
                    creationPane.initService(new ProjectCreationService(Services.PROJECT_CREATION_PIPELINE.createProject(
                        ProjectTypeRegistry.NEOFORGE,
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
    }

    private void loadNeoforgeMinecraftVersionsAsync() {
        NEOFORGE_MINECRAFT_VERSIONS_CACHE.getIfPresent().ifPresent(this::applyMinecraftVersions);

        resolveNeoforgeMinecraftVersions().whenComplete((versions, throwable) -> {
            if (throwable != null) {
                Railroad.LOGGER.error("Failed to fetch Minecraft versions for Neoforge", throwable);
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

    private CompletableFuture<List<MinecraftVersion>> resolveNeoforgeMinecraftVersions() {
        return NEOFORGE_MINECRAFT_VERSIONS_CACHE.getAsync(() ->
            SwitchboardRepositories.NEOFORGE.getAllVersions()
                .thenApply(versions -> versions.stream()
                    .map(NeoforgeProjectDetailsPane::extractMinecraftVersionId)
                    .flatMap(Optional::stream)
                    .map(this::lookupMinecraftVersion)
                    .flatMap(Optional::stream)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList()
                )
        );
    }

    private CompletableFuture<NeoforgeVersionsPayload> fetchNeoforgeVersions(MinecraftVersion version) {
        Platform.runLater(() -> {
            ComboBox<String> comboBox = neoforgeVersionComboBox.get();
            if (comboBox != null) {
                comboBox.getItems().clear();
                comboBox.setValue(null);
            }
            latestNeoforgeVersionProperty.set(null);
        });

        if (version == null)
            return CompletableFuture.completedFuture(new NeoforgeVersionsPayload(null, Collections.emptyList(), null));

        String minecraftId = version.id();

        CompletableFuture<List<String>> versionsFuture = SwitchboardRepositories.NEOFORGE.getVersionsFor(minecraftId);
        CompletableFuture<String> latestFuture = SwitchboardRepositories.NEOFORGE.getLatestVersionFor(minecraftId);

        return versionsFuture.thenCombine(latestFuture, (versions, latest) ->
                new NeoforgeVersionsPayload(version, versions == null ? Collections.emptyList() : versions, latest))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to fetch Neoforge versions for Minecraft version {}", version, throwable);
                return new NeoforgeVersionsPayload(version, Collections.emptyList(), null);
            });
    }

    private void applyNeoforgeVersions(NeoforgeVersionsPayload payload) {
        ComboBox<String> comboBox = neoforgeVersionComboBox.get();
        if (comboBox == null)
            return;

        if (!Objects.equals(payload.contextVersion(), getSelectedMinecraftVersion()))
            return;

        List<String> versions = payload.versions();
        comboBox.getItems().setAll(versions);
        latestNeoforgeVersionProperty.set(payload.latest());

        String currentValue = comboBox.getValue();
        if (currentValue != null && versions.contains(currentValue))
            return;

        String selection = null;
        String latest = payload.latest();
        if (latest != null && versions.contains(latest))
            selection = latest;
        else if (!versions.isEmpty())
            selection = versions.getFirst();

        comboBox.setValue(selection);
    }

    private String latestNeoforgeVersion() {
        return latestNeoforgeVersionProperty.get();
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
        if (comboBox == null)
            return null;

        MinecraftVersion value = comboBox.getValue();
        if (value != null)
            return value;

        List<MinecraftVersion> items = comboBox.getItems();
        if (items.isEmpty())
            return null;

        return items.getFirst();
    }

    private Optional<MinecraftVersion> lookupMinecraftVersion(String versionId) {
        try {
            return SwitchboardRepositories.MINECRAFT.getVersionSync(versionId);
        } catch (ExecutionException exception) {
            Railroad.LOGGER.error("Failed to fetch Minecraft version {}", versionId, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Railroad.LOGGER.error("Interrupted while fetching Minecraft version {}", versionId, exception);
        }

        return Optional.empty();
    }

    private static Optional<String> extractMinecraftVersionId(String neoforgeVersion) {
        if (neoforgeVersion == null || neoforgeVersion.isBlank())
            return Optional.empty();

        String lower = neoforgeVersion.toLowerCase(Locale.ROOT);
        if (lower.contains("25w14craftmine"))
            return Optional.of("25w14craftmine");

        int lastDot = neoforgeVersion.lastIndexOf('.');
        if (lastDot <= 0)
            return Optional.empty();

        String withoutBuild = neoforgeVersion.substring(0, lastDot);
        int hyphen = withoutBuild.indexOf('-');
        String base = hyphen >= 0 ? withoutBuild.substring(0, hyphen) : withoutBuild;
        if (base.isBlank())
            return Optional.empty();

        return Optional.of("1." + base);
    }

    private static boolean isPrerelease(String version) {
        if (version == null)
            return false;

        String lower = version.toLowerCase(Locale.ROOT);
        return lower.contains("beta") || lower.contains("alpha") || lower.contains("rc") || lower.contains("25w14craftmine");
    }

    private record NeoforgeVersionsPayload(MinecraftVersion contextVersion, List<String> versions, String latest) {
    }

    protected static ProjectData createData(FormData formData) {
        String projectName = formData.getString("ProjectName");
        var projectPath = Path.of(formData.getString("ProjectPath"));
        boolean createGit = formData.getBoolean("CreateGit");
        License license = formData.get("License", License.class);
        String licenseCustom = license == LicenseRegistry.CUSTOM ? formData.getString("CustomLicense") : null;
        MinecraftVersion minecraftVersion = formData.get("MinecraftVersion", MinecraftVersion.class);
        String forgeVersion = formData.get("NeoforgeVersion", String.class);
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

        var data = new ProjectData();
        data.set(ProjectData.DefaultKeys.NAME, projectName);
        data.set(ProjectData.DefaultKeys.PATH, projectPath);
        data.set(ProjectData.DefaultKeys.INIT_GIT, createGit);

        data.set(ProjectData.DefaultKeys.LICENSE, license);
        // TODO: Get rid of this and move into CustomLicense (once licenses are registerable)
        if (licenseCustom != null)
            data.set(ProjectData.DefaultKeys.LICENSE_CUSTOM, licenseCustom);

        data.set(MinecraftProjectKeys.MINECRAFT_VERSION, minecraftVersion);
        data.set(ForgeProjectKeys.FORGE_VERSION, forgeVersion);
        data.set(MinecraftProjectKeys.MOD_ID, modId);
        data.set(MinecraftProjectKeys.MOD_NAME, modName);
        data.set(MinecraftProjectKeys.MAIN_CLASS, mainClass);
        data.set(ForgeProjectKeys.USE_MIXINS, useMixins);
        data.set(ForgeProjectKeys.USE_ACCESS_TRANSFORMER, useAccessTransformer);
        data.set(ForgeProjectKeys.GEN_RUN_FOLDERS, genRunFolders);
        data.set(MinecraftProjectKeys.MAPPING_CHANNEL, mappingChannel);
        data.set(MinecraftProjectKeys.MAPPING_VERSION, mappingVersion);
        author.ifPresent(a -> data.set(ProjectData.DefaultKeys.AUTHOR, a));
        credits.ifPresent(c -> data.set(ProjectData.DefaultKeys.CREDITS, c));
        description.ifPresent(d -> data.set(ProjectData.DefaultKeys.DESCRIPTION, d));
        issues.ifPresent(i -> data.set(ProjectData.DefaultKeys.ISSUES_URL, i));
        updateJsonUrl.ifPresent(u -> data.set(ForgeProjectKeys.UPDATE_JSON_URL, u));
        displayUrl.ifPresent(u -> data.set(ForgeProjectKeys.DISPLAY_URL, u));
        data.set(ForgeProjectKeys.DISPLAY_TEST, displayTest);
        data.set(ForgeProjectKeys.CLIENT_SIDE_ONLY, clientSideOnly);
        data.set(MavenProjectKeys.GROUP_ID, groupId);
        data.set(MavenProjectKeys.ARTIFACT_ID, artifactId);
        data.set(MavenProjectKeys.VERSION, version);
        return data;
    }
}
