package dev.railroadide.railroad.project.onboarding.impl;

import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.ValidationResult;
import dev.railroadide.core.project.License;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.project.creation.ProjectCreationService;
import dev.railroadide.core.project.creation.ProjectServiceRegistry;
import dev.railroadide.core.project.creation.service.GradleService;
import dev.railroadide.core.project.minecraft.MappingChannel;
import dev.railroadide.core.switchboard.pojo.FabricLoaderVersion;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.railroad.project.MappingChannelRegistry;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.project.creation.ui.ProjectCreationPane;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.railroad.project.details.ProjectValidators;
import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import dev.railroadide.railroad.project.onboarding.OnboardingProcess;
import dev.railroadide.railroad.project.onboarding.flow.OnboardingFlow;
import dev.railroadide.railroad.project.onboarding.step.OnboardingFormStep;
import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
import dev.railroadide.railroad.switchboard.repositories.FabricApiVersionRepository;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.apache.commons.collections.ListUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class FabricProjectOnboarding {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public void start(Scene scene) {
        var flow = OnboardingFlow.builder()
            .addStep("project_details", this::createProjectDetailsStep)
            .addStep("maven_coordinates", this::createMavenCoordinatesStep)
            .addStep("minecraft_version", this::createMinecraftVersionStep)
            .addStep("mapping_channel", this::createMappingChannelStep)
            .addStep("mapping_version", this::createMappingVersionStep)
            .addStep("fabric_loader", this::createFabricLoaderStep)
            .addStep("fabric_api", this::createFabricApiStep)
            .addStep("mod_details", this::createModDetailsStep)
            .addStep("license", this::createLicenseStep)
            .addStep("git", this::createGitStep)
            .addStep("access_widener", this::createAccessWidenerStep)
            .addStep("split_sources", this::createSplitSourcesStep)
            .addStep("optional_details", this::createOptionalDetailsStep)
            .firstStep("project_details")
            .addTransition("project_details", "maven_coordinates")
            .addTransition("maven_coordinates", "minecraft_version")
            .addTransition("minecraft_version", "mapping_channel")
            .addTransition("mapping_channel", "mapping_version")
            .addTransition("mapping_version", "fabric_loader")
            .addTransition("fabric_loader", "fabric_api")
            .addTransition("fabric_api", "mod_details")
            .addTransition("mod_details", "license")
            .addTransition("license", "git")
            .addTransition("git", "access_widener")
            .addTransition("access_widener", "split_sources")
            .addTransition("split_sources", "optional_details")
            .build();

        var process = OnboardingProcess.createBasic(
            flow,
            new OnboardingContext(executor),
            ctx -> onFinish(ctx, scene)
        );

        process.run(scene);
    }

    private void onFinish(OnboardingContext ctx, Scene scene) {
        this.executor.shutdown();

        var data = new ProjectData();
        data.set(ProjectData.DefaultKeys.TYPE, ProjectTypeRegistry.FABRIC);
        data.set(ProjectData.DefaultKeys.NAME, ctx.get(ProjectData.DefaultKeys.NAME));
        data.set(ProjectData.DefaultKeys.PATH, ctx.get(ProjectData.DefaultKeys.PATH));
        data.set(ProjectData.DefaultKeys.INIT_GIT, ctx.get(ProjectData.DefaultKeys.INIT_GIT));

        data.set(ProjectData.DefaultKeys.LICENSE, ctx.get(ProjectData.DefaultKeys.LICENSE));
        // TODO: Get rid of this and move into CustomLicense
        if (ctx.contains(ProjectData.DefaultKeys.LICENSE_CUSTOM))
            data.set(ProjectData.DefaultKeys.LICENSE_CUSTOM, ctx.get(ProjectData.DefaultKeys.LICENSE_CUSTOM));

        data.set(MinecraftProjectKeys.MINECRAFT_VERSION, ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION));
        data.set(FabricProjectKeys.FABRIC_LOADER_VERSION, ctx.get(FabricProjectKeys.FABRIC_LOADER_VERSION));

        if (ctx.contains(FabricProjectKeys.FABRIC_API_VERSION))
            data.set(FabricProjectKeys.FABRIC_API_VERSION, ctx.get(FabricProjectKeys.FABRIC_API_VERSION));

        data.set(MinecraftProjectKeys.MOD_ID, ctx.get(MinecraftProjectKeys.MOD_ID));
        data.set(MinecraftProjectKeys.MOD_NAME, ctx.get(MinecraftProjectKeys.MOD_NAME));
        data.set(MinecraftProjectKeys.MAIN_CLASS, ctx.get(MinecraftProjectKeys.MAIN_CLASS));
        data.set(FabricProjectKeys.USE_ACCESS_WIDENER, ctx.get(FabricProjectKeys.USE_ACCESS_WIDENER));
        data.set(FabricProjectKeys.SPLIT_SOURCES, ctx.get(FabricProjectKeys.SPLIT_SOURCES));
        data.set(MinecraftProjectKeys.MAPPING_CHANNEL, ctx.get(MinecraftProjectKeys.MAPPING_CHANNEL));
        data.set(MinecraftProjectKeys.MAPPING_VERSION, ctx.get(MinecraftProjectKeys.MAPPING_VERSION));

        if (ctx.contains(ProjectData.DefaultKeys.AUTHOR))
            data.set(ProjectData.DefaultKeys.AUTHOR, ctx.get(ProjectData.DefaultKeys.AUTHOR));
        if (ctx.contains(ProjectData.DefaultKeys.DESCRIPTION))
            data.set(ProjectData.DefaultKeys.DESCRIPTION, ctx.get(ProjectData.DefaultKeys.DESCRIPTION));
        if (ctx.contains(ProjectData.DefaultKeys.ISSUES_URL))
            data.set(ProjectData.DefaultKeys.ISSUES_URL, ctx.get(ProjectData.DefaultKeys.ISSUES_URL));
        if (ctx.contains(ProjectData.DefaultKeys.HOMEPAGE_URL))
            data.set(ProjectData.DefaultKeys.HOMEPAGE_URL, ctx.get(ProjectData.DefaultKeys.HOMEPAGE_URL));
        if (ctx.contains(ProjectData.DefaultKeys.SOURCES_URL))
            data.set(ProjectData.DefaultKeys.SOURCES_URL, ctx.get(ProjectData.DefaultKeys.SOURCES_URL));

        data.set(MavenProjectKeys.GROUP_ID, ctx.get(MavenProjectKeys.GROUP_ID));
        data.set(MavenProjectKeys.ARTIFACT_ID, ctx.get(MavenProjectKeys.ARTIFACT_ID));
        data.set(MavenProjectKeys.VERSION, ctx.get(MavenProjectKeys.VERSION));

        var creationPane = new ProjectCreationPane(data);

        ProjectServiceRegistry serviceRegistry = Services.PROJECT_SERVICE_REGISTRY;
        serviceRegistry.get(GradleService.class).setOutputStream(creationPane.getTaos());
        creationPane.initService(new ProjectCreationService(Services.PROJECT_CREATION_PIPELINE.createProject(
            ProjectTypeRegistry.FABRIC,
            serviceRegistry
        ), creationPane.getContext()));

        scene.setRoot(creationPane);
    }

    private OnboardingStep createProjectDetailsStep() {
        return OnboardingFormStep.builder()
            .id("project_details")
            .title("railroad.project.creation.project_details.title")
            .description("railroad.project.creation.project_details.description")
            .appendSection("railroad.project.creation.section.project",
                OnboardingFormStep.component(
                    FormComponent.textField(ProjectData.DefaultKeys.NAME, "railroad.project.creation.name")
                        .required()
                        .promptText("railroad.project.creation.name.prompt")
                        .validator(ProjectValidators::validateProjectName)),
                OnboardingFormStep.component(
                    FormComponent.directoryChooser(ProjectData.DefaultKeys.PATH, "railroad.project.creation.location")
                        .required()
                        .defaultPath(System.getProperty("user.home"))
                        .validator(ProjectValidators::validatePath),
                    value -> {
                        if (value == null)
                            return null;

                        String text = value.toString();
                        return text.isBlank() ? null : Path.of(text);
                    },
                    value -> {
                        if (value == null)
                            return null;

                        return value instanceof Path path ? path.toAbsolutePath().toString() : value.toString();
                    }))
            .build();
    }

    private OnboardingStep createMavenCoordinatesStep() {
        StringProperty artifactId = new SimpleStringProperty();
        String configuredGroupId = SettingsHandler.getValue(Settings.DEFAULT_PROJECT_GROUP_ID);
        String configuredVersion = SettingsHandler.getValue(Settings.DEFAULT_PROJECT_VERSION);
        String defaultGroupId = isNullOrBlank(configuredGroupId) ? "" : configuredGroupId;
        String defaultVersion = isNullOrBlank(configuredVersion) ? "1.0.0" : configuredVersion;

        return OnboardingFormStep.builder()
            .id("maven_coordinates")
            .title("railroad.project.creation.maven_coordinates.title")
            .description("railroad.project.creation.maven_coordinates.description")
            .appendSection("railroad.project.creation.section.maven_coordinates",
                OnboardingFormStep.component(
                    FormComponent.textField(MavenProjectKeys.GROUP_ID, "railroad.project.creation.group_id")
                        .required()
                        .promptText("railroad.project.creation.group_id.prompt")
                        .text(() -> defaultGroupId)
                        .validator(ProjectValidators::validateGroupId)),
                OnboardingFormStep.component(
                    FormComponent.textField(MavenProjectKeys.ARTIFACT_ID, "railroad.project.creation.artifact_id")
                        .required()
                        .promptText("railroad.project.creation.artifact_id.prompt")
                        .text(artifactId::get)
                        .validator(ProjectValidators::validateArtifactId)),
                OnboardingFormStep.component(
                    FormComponent.textField(MavenProjectKeys.VERSION, "railroad.project.creation.version")
                        .required()
                        .promptText("railroad.project.creation.version.prompt")
                        .text(() -> defaultVersion)
                        .validator(ProjectValidators::validateVersion)))
            .onEnter(ctx -> {
                String projectName = ctx.get(ProjectData.DefaultKeys.NAME);
                if (projectName != null) {
                    String defaultArtifactId = ProjectValidators.projectNameToArtifactId(projectName);
                    if (isNullOrBlank(artifactId.get())) {
                        artifactId.set(defaultArtifactId);
                    }
                }
            })
            .build();
    }

    private OnboardingStep createMinecraftVersionStep() {
        ObservableList<MinecraftVersion> availableVersions = FXCollections.observableArrayList();
        var nextInvalidationTime = new AtomicLong(0L);

        return OnboardingFormStep.builder()
            .id("minecraft_version")
            .title("railroad.project.creation.minecraft_version.title")
            .description("railroad.project.creation.minecraft_version.description")
            .appendSection("railroad.project.creation.section.minecraft_version",
                OnboardingFormStep.component(
                    FormComponent.comboBox(MinecraftProjectKeys.MINECRAFT_VERSION, "railroad.project.creation.minecraft_version", MinecraftVersion.class)
                        .items(() -> availableVersions)
                        .defaultValue(() -> MinecraftVersion.determineDefaultMinecraftVersion(availableVersions))
                        .keyFunction(MinecraftVersion::id)
                        .valueOfFunction(FabricProjectOnboarding::getMinecraftVersion)
                        .required()
                        .translate(false)))
            .onEnter(ctx -> {
                if (availableVersions.isEmpty() || System.currentTimeMillis() > nextInvalidationTime.get()) {
                    availableVersions.clear();
                    availableVersions.addAll(getMinecraftVersions());
                    nextInvalidationTime.set(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));
                    ctx.markForRefresh(MinecraftProjectKeys.MINECRAFT_VERSION);
                }
            })
            .build();
    }

    private OnboardingStep createMappingChannelStep() {
        ObservableList<MappingChannel> availableChannels = FXCollections.observableArrayList();
        return OnboardingFormStep.builder()
            .id("mapping_channel")
            .title("railroad.project.creation.mapping_channel.title")
            .description("railroad.project.creation.mapping_channel.description")
            .appendSection("railroad.project.creation.section.mapping_channel",
                OnboardingFormStep.component(
                    FormComponent.comboBox(MinecraftProjectKeys.MAPPING_CHANNEL, "railroad.project.creation.mapping_channel", MappingChannel.class)
                        .required()
                        .items(() -> availableChannels)
                        .defaultValue(() -> MappingChannelRegistry.YARN)
                        .keyFunction(MappingChannel::id)
                        .valueOfFunction(MappingChannel.REGISTRY::get)
                        .defaultDisplayNameFunction(MappingChannel::translationKey)
                        .translate(true)))
            .onEnter(ctx -> {
                MinecraftVersion mcVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                if (mcVersion != null) {
                    availableChannels.clear();
                    availableChannels.setAll(MappingChannelRegistry.findValidMappingChannels(mcVersion));
                    ctx.markForRefresh(MinecraftProjectKeys.MAPPING_CHANNEL);
                }
            })
            .build();
    }

    private OnboardingStep createMappingVersionStep() {
        ObservableList<String> availableVersions = FXCollections.observableArrayList();
        return OnboardingFormStep.builder()
            .id("mapping_version")
            .title("railroad.project.creation.mapping_version.title")
            .description("railroad.project.creation.mapping_version.description")
            .appendSection("railroad.project.creation.section.mapping_version",
                OnboardingFormStep.component(
                    FormComponent.comboBox(MinecraftProjectKeys.MAPPING_VERSION, "railroad.project.creation.mapping_version", String.class)
                        .required()
                        .items(() -> availableVersions)
                        .defaultValue(() -> {
                            if (!availableVersions.isEmpty())
                                return availableVersions.getFirst();

                            return null;
                        })
                        .translate(false)))
            .onEnter(ctx -> {
                MinecraftVersion mcVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                MappingChannel channel = ctx.get(MinecraftProjectKeys.MAPPING_CHANNEL);
                if (mcVersion != null && channel != null) {
                    List<String> newVersions = channel.listVersionsFor(mcVersion);
                    availableVersions.clear();
                    availableVersions.setAll(newVersions);
                    ctx.markForRefresh(MinecraftProjectKeys.MAPPING_VERSION);
                }
            })
            .build();
    }

    private OnboardingStep createFabricLoaderStep() {
        ObservableList<FabricLoaderVersion> availableVersions = FXCollections.observableArrayList();
        ObjectProperty<FabricLoaderVersion> latestVersionProperty = new SimpleObjectProperty<>();
        return OnboardingFormStep.builder()
            .id("fabric_loader")
            .title("railroad.project.creation.fabric_loader.title")
            .description("railroad.project.creation.fabric_loader.description")
            .appendSection("railroad.project.creation.section.fabric_loader",
                OnboardingFormStep.component(
                    FormComponent.comboBox(FabricProjectKeys.FABRIC_LOADER_VERSION, "railroad.project.creation.fabric_loader", FabricLoaderVersion.class)
                        .required()
                        .items(() -> availableVersions)
                        .keyFunction(FabricLoaderVersion::version)
                        .defaultValue(() -> {
                            if (latestVersionProperty.get() != null)
                                return latestVersionProperty.get();

                            if (!availableVersions.isEmpty())
                                return availableVersions.getFirst();

                            return null;
                        })
                        .translate(false)))
            .onEnter(ctx -> {
                MinecraftVersion mcVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                if (mcVersion != null) {
                    try {
                        CompletableFuture<List<FabricLoaderVersion>> versionsFuture = SwitchboardRepositories.FABRIC_LOADER.getVersionsFor(mcVersion.id());
                        CompletableFuture<FabricLoaderVersion> latestFuture = SwitchboardRepositories.FABRIC_LOADER.getLatestVersionFor(mcVersion.id());

                        List<FabricLoaderVersion> versions = versionsFuture.get();
                        FabricLoaderVersion latest = latestFuture.get();
                        availableVersions.clear();
                        availableVersions.addAll(versions);
                        latestVersionProperty.set(latest);
                        ctx.markForRefresh(FabricProjectKeys.FABRIC_LOADER_VERSION);
                    } catch (ExecutionException | InterruptedException exception) {
                        Railroad.LOGGER.error("Failed to fetch Fabric Loader versions for Minecraft {}", mcVersion.id(), exception);
                    }
                }
            })
            .build();
    }

    private OnboardingStep createFabricApiStep() {
        ObservableList<String> availableVersions = FXCollections.observableArrayList();
        return OnboardingFormStep.builder()
            .id("fabric_api")
            .title("railroad.project.creation.fabric_api.title")
            .description("railroad.project.creation.fabric_api.description")
            .appendSection("railroad.project.creation.section.fabric_api",
                OnboardingFormStep.component(
                    FormComponent.comboBox(FabricProjectKeys.FABRIC_API_VERSION, "railroad.project.creation.fabric_api", String.class)
                        .items(() -> availableVersions)
                        .defaultValue(() -> {
                            if (!availableVersions.isEmpty())
                                return availableVersions.getFirst();

                            return null;
                        })
                        .translate(false)))
            .onEnter(ctx -> {
                MinecraftVersion mcVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                if (mcVersion != null) {
                    try {
                        CompletableFuture<List<String>> versionsFuture = SwitchboardRepositories.FABRIC_API.getVersionsFor(mcVersion.id());
                        List<String> versions = versionsFuture.get();
                        availableVersions.clear();
                        availableVersions.addAll(versions);
                        ctx.markForRefresh(FabricProjectKeys.FABRIC_API_VERSION);
                    } catch (ExecutionException | InterruptedException exception) {
                        Railroad.LOGGER.error("Failed to fetch Fabric API versions for Minecraft {}", mcVersion.id(), exception);
                    }
                }
            })
            .build();
    }

    private OnboardingStep createModDetailsStep() {
        StringProperty modIdProperty = new SimpleStringProperty();
        StringProperty modNameProperty = new SimpleStringProperty();
        StringProperty mainClassProperty = new SimpleStringProperty();

        ObjectProperty<TextField> modIdField = new SimpleObjectProperty<>();
        ObjectProperty<TextField> modNameField = new SimpleObjectProperty<>();
        ObjectProperty<TextField> mainClassField = new SimpleObjectProperty<>();

        bindTextField(modIdProperty, modIdField);
        bindTextField(modNameProperty, modNameField);
        bindTextField(mainClassProperty, mainClassField);

        return OnboardingFormStep.builder()
            .id("mod_details")
            .title("railroad.project.creation.mod_details.title")
            .description("railroad.project.creation.mod_details.description")
            .appendSection("railroad.project.creation.section.mod_details",
                OnboardingFormStep.component(
                    FormComponent.textField(MinecraftProjectKeys.MOD_ID, "railroad.project.creation.mod_id")
                        .required()
                        .promptText("railroad.project.creation.mod_id.prompt")
                        .text(modIdProperty::get)
                        .bindTextFieldTo(modIdField)
                        .validator(ProjectValidators::validateModId)),
                OnboardingFormStep.component(
                    FormComponent.textField(MinecraftProjectKeys.MOD_NAME, "railroad.project.creation.mod_name")
                        .required()
                        .promptText("railroad.project.creation.mod_name.prompt")
                        .text(modNameProperty::get)
                        .bindTextFieldTo(modNameField)
                        .validator(ProjectValidators::validateModName)),
                OnboardingFormStep.component(
                    FormComponent.textField(MinecraftProjectKeys.MAIN_CLASS, "railroad.project.creation.main_class")
                        .required()
                        .promptText("railroad.project.creation.main_class.prompt")
                        .text(mainClassProperty::get)
                        .bindTextFieldTo(mainClassField)
                        .validator(ProjectValidators::validateMainClass)))
            .onEnter(ctx -> {
                String projectName = ctx.get(ProjectData.DefaultKeys.NAME);

                if (!isNullOrBlank(projectName)) {
                    modIdProperty.set(ProjectValidators.projectNameToModId(projectName));
                }

                if (!isNullOrBlank(projectName)) {
                    modNameProperty.set(projectName);
                }

                if (!isNullOrBlank(projectName)) {
                    String mainClassName = ProjectValidators.projectNameToMainClass(projectName);
                    mainClassProperty.set(isNullOrBlank(mainClassName) ? "" : mainClassName);
                }
            })
            .build();
    }

    private OnboardingStep createLicenseStep() {
        ObservableList<License> availableLicenses = FXCollections.observableArrayList();
        ObjectProperty<ComboBox<License>> licenseComboBox = new SimpleObjectProperty<>();
        BooleanProperty showCustomLicense = new SimpleBooleanProperty(false);
        ChangeListener<License> licenseSelectionListener = (observable, oldValue, newValue) ->
            showCustomLicense.set(newValue == LicenseRegistry.CUSTOM);

        licenseComboBox.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.valueProperty().removeListener(licenseSelectionListener);
            }

            if (newValue != null) {
                showCustomLicense.set(newValue.getValue() == LicenseRegistry.CUSTOM);
                newValue.valueProperty().addListener(licenseSelectionListener);
            } else {
                showCustomLicense.set(false);
            }
        });

        BooleanBinding customLicenseVisible = Bindings.createBooleanBinding(showCustomLicense::get, showCustomLicense);

        return OnboardingFormStep.builder()
            .id("license")
            .title("railroad.project.creation.license.title")
            .description("railroad.project.creation.license.description")
            .appendSection("railroad.project.creation.section.license",
                OnboardingFormStep.component(
                    FormComponent.comboBox(ProjectData.DefaultKeys.LICENSE, "railroad.project.creation.license", License.class)
                        .required()
                        .bindComboBoxTo(licenseComboBox)
                        .keyFunction(License::getSpdxId)
                        .valueOfFunction(License::fromSpdxId)
                        .defaultDisplayNameFunction(License::getName)
                        .translate(false)
                        .items(() -> availableLicenses)
                        .defaultValue(() -> {
                            if (availableLicenses.contains(LicenseRegistry.LGPL))
                                return LicenseRegistry.LGPL;

                            if (!availableLicenses.isEmpty())
                                return availableLicenses.getFirst();

                            return null;
                        })),
                OnboardingFormStep.component(
                    FormComponent.textField(ProjectData.DefaultKeys.LICENSE_CUSTOM, "railroad.project.creation.license.custom")
                        .visible(customLicenseVisible)
                        .promptText("railroad.project.creation.license.custom.prompt")
                        .validator(ProjectValidators::validateCustomLicense)))
            .onEnter(ctx -> {
                List<License> newValues = License.REGISTRY.values()
                    .stream()
                    .sorted(Comparator.comparing(License::getName))
                    .toList();

                if(availableLicenses.size() != newValues.size() || !ListUtils.isEqualList(availableLicenses, newValues)) {
                    availableLicenses.clear();
                    availableLicenses.addAll(newValues);
                    ctx.markForRefresh(ProjectData.DefaultKeys.LICENSE);
                }
            })
            .build();
    }

    private OnboardingStep createGitStep() {
        // TODO: Provide options for GitHub, GitLab, Bitbucket initialization (with private/public options)
        return OnboardingFormStep.builder()
            .id("git")
            .title("railroad.project.creation.git.title")
            .description("railroad.project.creation.git.description")
            .appendSection("railroad.project.creation.section.git",
                OnboardingFormStep.component(
                    FormComponent.checkBox(ProjectData.DefaultKeys.INIT_GIT, "railroad.project.creation.init_git")
                        .selected(true)))
            .build();
    }

    private OnboardingStep createAccessWidenerStep() {
        ObjectProperty<CheckBox> useAccessWidenerCheckBox = new SimpleObjectProperty<>();
        BooleanProperty accessWidenerEnabled = new SimpleBooleanProperty(true);
        ChangeListener<Boolean> useAccessWidenerListener = (observable, oldValue, newValue) ->
            accessWidenerEnabled.set(Boolean.TRUE.equals(newValue));

        useAccessWidenerCheckBox.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.selectedProperty().removeListener(useAccessWidenerListener);
            }

            if (newValue != null) {
                accessWidenerEnabled.set(newValue.isSelected());
                newValue.selectedProperty().addListener(useAccessWidenerListener);
            } else {
                accessWidenerEnabled.set(false);
            }
        });

        BooleanBinding accessWidenerPathVisible = Bindings.createBooleanBinding(
            accessWidenerEnabled::get,
            accessWidenerEnabled
        );

        return OnboardingFormStep.builder()
            .id("access_widener")
            .title("railroad.project.creation.access_widener.title")
            .description("railroad.project.creation.access_widener.description")
            .appendSection("railroad.project.creation.section.access_widener",
                OnboardingFormStep.component(
                    FormComponent.checkBox(FabricProjectKeys.USE_ACCESS_WIDENER, "railroad.project.creation.use_access_widener")
                        .selected(true)
                        .bindCheckBoxTo(useAccessWidenerCheckBox)),
                OnboardingFormStep.component(
                    FormComponent.textField(FabricProjectKeys.ACCESS_WIDENER_PATH, "railroad.project.creation.access_widener.path")
                        .text("${modid}.accesswidener")
                        .promptText("railroad.project.creation.access_widener.path.prompt")
                        .visible(accessWidenerPathVisible)
                        .validator(field -> {
                            if (!accessWidenerEnabled.get())
                                return ValidationResult.ok();

                            String text = field.getText();
                            if (text == null || text.isBlank())
                                return ValidationResult.error("railroad.project.creation.access_widener.path.error.required");

                            return ValidationResult.ok();
                        })))
            .onExit(ctx -> {
                if (!accessWidenerEnabled.get()) {
                    ctx.data().remove(FabricProjectKeys.ACCESS_WIDENER_PATH);
                }
            })
            .build();
    }

    private OnboardingStep createSplitSourcesStep() {
        return OnboardingFormStep.builder()
            .id("split_sources")
            .title("railroad.project.creation.split_sources.title")
            .description("railroad.project.creation.split_sources.description")
            .appendSection("railroad.project.creation.section.split_sources",
                OnboardingFormStep.component(
                    FormComponent.checkBox(FabricProjectKeys.SPLIT_SOURCES, "railroad.project.creation.split_sources")
                        .selected(true)))
            .build();
    }

    private OnboardingStep createOptionalDetailsStep() {
        String configuredAuthor = SettingsHandler.getValue(Settings.DEFAULT_PROJECT_AUTHOR);
        String defaultAuthor = !isNullOrBlank(configuredAuthor)
            ? configuredAuthor
            : Optional.ofNullable(System.getProperty("user.name"))
                .filter(name -> !isNullOrBlank(name))
                .orElse("");

        return OnboardingFormStep.builder()
            .id("optional_details")
            .title("railroad.project.creation.optional_details.title")
            .description("railroad.project.creation.optional_details.description")
            .appendSection("railroad.project.creation.section.optional_details",
                OnboardingFormStep.component(
                    FormComponent.textField(ProjectData.DefaultKeys.AUTHOR, "railroad.project.creation.author")
                        .text(() -> defaultAuthor)
                        .promptText("railroad.project.creation.author.prompt")
                        .validator(ProjectValidators::validateAuthor)),
                OnboardingFormStep.component(
                    FormComponent.textArea(ProjectData.DefaultKeys.DESCRIPTION, "railroad.project.creation.description")
                        .promptText("railroad.project.creation.description.prompt")
                        .validator(ProjectValidators::validateDescription)),
                OnboardingFormStep.component(
                    FormComponent.textField(ProjectData.DefaultKeys.ISSUES_URL, "railroad.project.creation.issues_url")
                        .promptText("railroad.project.creation.issues_url.prompt")
                        .validator(ProjectValidators::validateIssues)),
                OnboardingFormStep.component(
                    FormComponent.textField(ProjectData.DefaultKeys.HOMEPAGE_URL, "railroad.project.creation.homepage_url")
                        .promptText("railroad.project.creation.homepage_url.prompt")
                        .validator(textField -> ProjectValidators.validateGenericUrl(textField, "homepage"))),
                OnboardingFormStep.component(
                    FormComponent.textField(ProjectData.DefaultKeys.SOURCES_URL, "railroad.project.creation.sources_url")
                        .promptText("railroad.project.creation.sources_url.prompt")
                        .validator(textField -> ProjectValidators.validateGenericUrl(textField, "sources"))))
            .build();
    }

    private static @NotNull List<MinecraftVersion> getMinecraftVersions() {
        try {
            return SwitchboardRepositories.FABRIC_API.getAllVersionsSync().stream()
                .map(FabricApiVersionRepository::fapiToMinecraftVersion)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .map(FabricProjectOnboarding::getMinecraftVersion)
                .filter(Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .toList();
        } catch (ExecutionException | InterruptedException exception) {
            Railroad.LOGGER.error("Failed to fetch Minecraft versions", exception);
            return Collections.emptyList();
        }
    }

    private static MinecraftVersion getMinecraftVersion(String string) {
        try {
            return SwitchboardRepositories.MINECRAFT.getVersionSync(string).orElse(null);
        } catch (ExecutionException | InterruptedException exception) {
            Railroad.LOGGER.error("Failed to fetch Minecraft version {}", string, exception);
            return null;
        }
    }

    private static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void bindTextField(StringProperty valueProperty, ObjectProperty<TextField> fieldProperty) {
        Objects.requireNonNull(valueProperty, "valueProperty");
        Objects.requireNonNull(fieldProperty, "fieldProperty");

        valueProperty.addListener((obs, oldValue, newValue) -> {
            TextField field = fieldProperty.get();
            if (field != null && !Objects.equals(field.getText(), newValue)) {
                field.setText(newValue);
            }
        });

        fieldProperty.addListener((obs, oldField, newField) -> {
            if (newField == null)
                return;

            if (!Objects.equals(newField.getText(), valueProperty.get())) {
                newField.setText(valueProperty.get());
            }

            newField.textProperty().addListener((textObs, oldText, newText) -> {
                if (!Objects.equals(valueProperty.get(), newText)) {
                    valueProperty.set(newText);
                }
            });
        });
    }
}
