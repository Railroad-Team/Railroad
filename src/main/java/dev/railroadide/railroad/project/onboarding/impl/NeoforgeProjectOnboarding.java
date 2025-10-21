package dev.railroadide.railroad.project.onboarding.impl;

import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormComponentBuilder;
import dev.railroadide.core.form.ui.InformativeLabeledHBox;
import dev.railroadide.core.project.License;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.project.creation.ProjectCreationService;
import dev.railroadide.core.project.creation.ProjectServiceRegistry;
import dev.railroadide.core.project.creation.service.GradleService;
import dev.railroadide.core.project.minecraft.MappingChannel;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.project.*;
import dev.railroadide.railroad.project.creation.ui.ProjectCreationPane;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import dev.railroadide.railroad.project.onboarding.OnboardingProcess;
import dev.railroadide.railroad.project.onboarding.flow.OnboardingFlow;
import dev.railroadide.railroad.project.onboarding.step.OnboardingFormStep;
import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
import dev.railroadide.railroad.utility.ExpiringCache;
import dev.railroadide.railroad.welcome.project.ui.widget.StarableListCell;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.apache.commons.collections.ListUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

// TODO: Make it so the display test and client side only options are only shown for versions that support it
// TODO: Make it so the display test and client side only options are in their own steps
// TODO: Fix the comboboxes not being immediately populated and instead having the data fetched completely async
public class NeoforgeProjectOnboarding {
    private static final ExpiringCache<List<MinecraftVersion>> NEOFORGE_MINECRAFT_VERSIONS_CACHE = new ExpiringCache<>(Duration.ofHours(3));

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public void start(Scene scene) {
        var flow = OnboardingFlow.builder()
            .addStep("project_details", this::createProjectDetailsStep)
            .addStep("maven_coordinates", this::createMavenCoordinatesStep)
            .addStep("minecraft_version", this::createMinecraftVersionStep)
            .addStep("neoforge_version", this::createNeoforgeVersionStep)
            .addStep("mapping_channel", this::createMappingChannelStep)
            .addStep("mapping_version", this::createMappingVersionStep)
            .addStep("mod_details", this::createModDetailsStep)
            .addStep("license", this::createLicenseStep)
            .addStep("git", this::createGitStep)
            .addStep("optional_details", this::createOptionalDetailsStep)
            .firstStep("project_details")
            .addTransition("project_details", "maven_coordinates")
            .addTransition("maven_coordinates", "minecraft_version")
            .addTransition("minecraft_version", "neoforge_version")
            .addTransition("neoforge_version", "mapping_channel")
            .addTransition("mapping_channel", "mapping_version")
            .addTransition("mapping_version", "mod_details")
            .addTransition("mod_details", "license")
            .addTransition("license", "git")
            .addTransition("git", "optional_details")
            .build();

        var process = OnboardingProcess.createBasic(
            flow,
            new OnboardingContext(executor),
            ctx -> onFinish(ctx, scene)
        );

        process.run(scene);
    }

    private void onFinish(OnboardingContext ctx, Scene scene) {
        executor.shutdown();

        var data = new ProjectData();
        data.set(ProjectData.DefaultKeys.TYPE, ProjectTypeRegistry.NEOFORGE);
        data.set(ProjectData.DefaultKeys.NAME, ctx.get(ProjectData.DefaultKeys.NAME));
        data.set(ProjectData.DefaultKeys.PATH, ctx.get(ProjectData.DefaultKeys.PATH));
        data.set(ProjectData.DefaultKeys.INIT_GIT, Boolean.TRUE.equals(ctx.get(ProjectData.DefaultKeys.INIT_GIT)));
        data.set(ProjectData.DefaultKeys.LICENSE, ctx.get(ProjectData.DefaultKeys.LICENSE));

        if (ctx.contains(ProjectData.DefaultKeys.LICENSE_CUSTOM)) {
            data.set(ProjectData.DefaultKeys.LICENSE_CUSTOM, ctx.get(ProjectData.DefaultKeys.LICENSE_CUSTOM));
        }

        data.set(MinecraftProjectKeys.MINECRAFT_VERSION, ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION));
        data.set(ForgeProjectKeys.FORGE_VERSION, ctx.get(ForgeProjectKeys.FORGE_VERSION));
        data.set(MinecraftProjectKeys.MAPPING_CHANNEL, ctx.get(MinecraftProjectKeys.MAPPING_CHANNEL));
        data.set(MinecraftProjectKeys.MAPPING_VERSION, ctx.get(MinecraftProjectKeys.MAPPING_VERSION));
        data.set(MinecraftProjectKeys.MOD_ID, ctx.get(MinecraftProjectKeys.MOD_ID));
        data.set(MinecraftProjectKeys.MOD_NAME, ctx.get(MinecraftProjectKeys.MOD_NAME));
        data.set(MinecraftProjectKeys.MAIN_CLASS, ctx.get(MinecraftProjectKeys.MAIN_CLASS));
        data.set(ForgeProjectKeys.USE_MIXINS, Boolean.TRUE.equals(ctx.get(ForgeProjectKeys.USE_MIXINS)));
        data.set(ForgeProjectKeys.USE_ACCESS_TRANSFORMER, Boolean.TRUE.equals(ctx.get(ForgeProjectKeys.USE_ACCESS_TRANSFORMER)));
        data.set(ForgeProjectKeys.GEN_RUN_FOLDERS, Boolean.TRUE.equals(ctx.get(ForgeProjectKeys.GEN_RUN_FOLDERS)));

        if (ctx.contains(ProjectData.DefaultKeys.AUTHOR)) {
            String author = ctx.get(ProjectData.DefaultKeys.AUTHOR);
            if (!isNullOrBlank(author)) {
                data.set(ProjectData.DefaultKeys.AUTHOR, author);
            }
        }

        if (ctx.contains(ProjectData.DefaultKeys.CREDITS)) {
            String credits = ctx.get(ProjectData.DefaultKeys.CREDITS);
            if (!isNullOrBlank(credits)) {
                data.set(ProjectData.DefaultKeys.CREDITS, credits);
            }
        }

        if (ctx.contains(ProjectData.DefaultKeys.DESCRIPTION)) {
            String description = ctx.get(ProjectData.DefaultKeys.DESCRIPTION);
            if (!isNullOrBlank(description)) {
                data.set(ProjectData.DefaultKeys.DESCRIPTION, description);
            }
        }

        if (ctx.contains(ProjectData.DefaultKeys.ISSUES_URL)) {
            String issuesUrl = ctx.get(ProjectData.DefaultKeys.ISSUES_URL);
            if (!isNullOrBlank(issuesUrl)) {
                data.set(ProjectData.DefaultKeys.ISSUES_URL, issuesUrl);
            }
        }

        if (ctx.contains(ForgeProjectKeys.UPDATE_JSON_URL)) {
            String updateJson = ctx.get(ForgeProjectKeys.UPDATE_JSON_URL);
            if (!isNullOrBlank(updateJson)) {
                data.set(ForgeProjectKeys.UPDATE_JSON_URL, updateJson);
            }
        }

        if (ctx.contains(ForgeProjectKeys.DISPLAY_URL)) {
            String displayUrl = ctx.get(ForgeProjectKeys.DISPLAY_URL);
            if (!isNullOrBlank(displayUrl)) {
                data.set(ForgeProjectKeys.DISPLAY_URL, displayUrl);
            }
        }

        DisplayTest displayTest = Optional.ofNullable((DisplayTest) ctx.get(ForgeProjectKeys.DISPLAY_TEST))
            .orElse(DisplayTest.MATCH_VERSION);
        data.set(ForgeProjectKeys.DISPLAY_TEST, displayTest);
        data.set(ForgeProjectKeys.CLIENT_SIDE_ONLY, Boolean.TRUE.equals(ctx.get(ForgeProjectKeys.CLIENT_SIDE_ONLY)));

        data.set(MavenProjectKeys.GROUP_ID, ctx.get(MavenProjectKeys.GROUP_ID));
        data.set(MavenProjectKeys.ARTIFACT_ID, ctx.get(MavenProjectKeys.ARTIFACT_ID));
        data.set(MavenProjectKeys.VERSION, ctx.get(MavenProjectKeys.VERSION));

        var creationPane = new ProjectCreationPane(data);

        ProjectServiceRegistry serviceRegistry = Services.PROJECT_SERVICE_REGISTRY;
        serviceRegistry.get(GradleService.class).setOutputStream(creationPane.getTaos());
        creationPane.initService(new ProjectCreationService(Services.PROJECT_CREATION_PIPELINE.createProject(
            ProjectTypeRegistry.NEOFORGE,
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
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.NAME, "railroad.project.creation.name")
                        .required()
                        .promptText("railroad.project.creation.name.prompt")
                        .validator(ProjectValidators::validateProjectName),
                    "railroad.project.creation.name.info"),
                described(
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
                    },
                    "railroad.project.creation.location.info"))
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
                described(
                    FormComponent.textField(MavenProjectKeys.GROUP_ID, "railroad.project.creation.group_id")
                        .required()
                        .promptText("railroad.project.creation.group_id.prompt")
                        .text(() -> defaultGroupId)
                        .validator(ProjectValidators::validateGroupId),
                    "railroad.project.creation.group_id.info"),
                described(
                    FormComponent.textField(MavenProjectKeys.ARTIFACT_ID, "railroad.project.creation.artifact_id")
                        .required()
                        .promptText("railroad.project.creation.artifact_id.prompt")
                        .text(artifactId::get)
                        .validator(ProjectValidators::validateArtifactId),
                    "railroad.project.creation.artifact_id.info"),
                described(
                    FormComponent.textField(MavenProjectKeys.VERSION, "railroad.project.creation.version")
                        .required()
                        .promptText("railroad.project.creation.version.prompt")
                        .text(() -> defaultVersion)
                        .validator(ProjectValidators::validateVersion),
                    "railroad.project.creation.version.info"))
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
        AtomicLong nextInvalidationTime = new AtomicLong(0L);

        return OnboardingFormStep.builder()
            .id("minecraft_version")
            .title("railroad.project.creation.minecraft_version.title")
            .description("railroad.project.creation.minecraft_version.description")
            .appendSection("railroad.project.creation.section.minecraft_version",
                described(
                    FormComponent.comboBox(MinecraftProjectKeys.MINECRAFT_VERSION, "railroad.project.creation.minecraft_version", MinecraftVersion.class)
                        .items(() -> availableVersions)
                        .defaultValue(() -> determineDefaultMinecraftVersion(availableVersions))
                        .keyFunction(MinecraftVersion::id)
                        .valueOfFunction(NeoforgeProjectOnboarding::getMinecraftVersion)
                        .required()
                        .translate(false),
                    "railroad.project.creation.minecraft_version.info"))
            .onEnter(ctx -> {
                long now = System.currentTimeMillis();
                if (availableVersions.isEmpty() || now > nextInvalidationTime.get()) {
                    NEOFORGE_MINECRAFT_VERSIONS_CACHE.getIfPresent().ifPresent(values ->
                        Platform.runLater(() -> {
                            availableVersions.setAll(values);
                            ctx.markForRefresh(MinecraftProjectKeys.MINECRAFT_VERSION);
                        }));

                    resolveNeoforgeMinecraftVersions().whenComplete((versions, throwable) -> {
                        if (throwable != null) {
                            Railroad.LOGGER.error("Failed to fetch Minecraft versions for Neoforge", throwable);
                            return;
                        }

                        Platform.runLater(() -> {
                            availableVersions.setAll(versions);
                            ctx.markForRefresh(MinecraftProjectKeys.MINECRAFT_VERSION);
                        });
                    });

                    nextInvalidationTime.set(now + TimeUnit.MINUTES.toMillis(5));
                }
            })
            .build();
    }

    private OnboardingStep createNeoforgeVersionStep() {
        ObservableList<String> availableVersions = FXCollections.observableArrayList();
        StringProperty latestNeoforgeVersion = new SimpleStringProperty();

        return OnboardingFormStep.builder()
            .id("neoforge_version")
            .title("railroad.project.creation.neoforge_version.title")
            .description("railroad.project.creation.neoforge_version.description")
            .appendSection("railroad.project.creation.section.neoforge_version",
                described(
                    FormComponent.comboBox(ForgeProjectKeys.FORGE_VERSION, "railroad.project.creation.neoforge_version", String.class)
                        .required()
                        .items(() -> availableVersions)
                        .defaultValue(() -> {
                            String latest = latestNeoforgeVersion.get();
                            if (latest != null && availableVersions.contains(latest))
                                return latest;

                            if (!availableVersions.isEmpty())
                                return availableVersions.getFirst();

                            return null;
                        })
                        .translate(false)
                        .cellFactory(param -> new StarableListCell<>(
                            version -> isRecommendedNeoforgeVersion(version, latestNeoforgeVersion.get()),
                            latest -> Objects.equals(latest, latestNeoforgeVersion.get()),
                            Function.identity()))
                        .buttonCell(new StarableListCell<>(
                            version -> isRecommendedNeoforgeVersion(version, latestNeoforgeVersion.get()),
                            latest -> Objects.equals(latest, latestNeoforgeVersion.get()),
                            Function.identity())),
                    "railroad.project.creation.neoforge_version.info"))
            .onEnter(ctx -> {
                MinecraftVersion minecraftVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);

                fetchNeoforgeVersions(minecraftVersion).whenComplete((payload, throwable) -> {
                    if (throwable != null) {
                        Railroad.LOGGER.error("Failed to fetch Neoforge versions for Minecraft {}", minecraftVersion, throwable);
                        return;
                    }

                    Platform.runLater(() -> {
                        availableVersions.setAll(payload.versions());
                        latestNeoforgeVersion.set(payload.latest());
                        ctx.markForRefresh(ForgeProjectKeys.FORGE_VERSION);
                    });
                });
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
                described(
                    FormComponent.comboBox(MinecraftProjectKeys.MAPPING_CHANNEL, "railroad.project.creation.mapping_channel", MappingChannel.class)
                        .required()
                        .items(() -> availableChannels)
                        .defaultValue(() -> {
                            if (availableChannels.contains(MappingChannelRegistry.MOJMAP))
                                return MappingChannelRegistry.MOJMAP;

                            if (!availableChannels.isEmpty())
                                return availableChannels.getFirst();

                            return null;
                        })
                        .keyFunction(MappingChannel::id)
                        .valueOfFunction(MappingChannel.REGISTRY::get)
                        .defaultDisplayNameFunction(MappingChannel::translationKey)
                        .translate(true),
                    "railroad.project.creation.mapping_channel.info"))
            .onEnter(ctx -> {
                MinecraftVersion minecraftVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                if (minecraftVersion != null) {
                    List<MappingChannel> channels = MappingChannelRegistry.findValidMappingChannels(minecraftVersion);
                    Platform.runLater(() -> {
                        availableChannels.setAll(channels);
                        ctx.markForRefresh(MinecraftProjectKeys.MAPPING_CHANNEL);
                    });
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
                described(
                    FormComponent.comboBox(MinecraftProjectKeys.MAPPING_VERSION, "railroad.project.creation.mapping_version", String.class)
                        .required()
                        .items(() -> availableVersions)
                        .defaultValue(() -> {
                            if (!availableVersions.isEmpty())
                                return availableVersions.getLast();

                            return null;
                        })
                        .translate(false),
                    "railroad.project.creation.mapping_version.info"))
            .onEnter(ctx -> {
                MinecraftVersion minecraftVersion = ctx.get(MinecraftProjectKeys.MINECRAFT_VERSION);
                MappingChannel mappingChannel = ctx.get(MinecraftProjectKeys.MAPPING_CHANNEL);
                if (minecraftVersion != null && mappingChannel != null) {
                    List<String> versions = mappingChannel.listVersionsFor(minecraftVersion);
                    Platform.runLater(() -> {
                        availableVersions.setAll(versions);
                        ctx.markForRefresh(MinecraftProjectKeys.MAPPING_VERSION);
                    });
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
                described(
                    FormComponent.textField(MinecraftProjectKeys.MOD_ID, "railroad.project.creation.mod_id")
                        .required()
                        .promptText("railroad.project.creation.mod_id.prompt")
                        .text(modIdProperty::get)
                        .bindTextFieldTo(modIdField)
                        .validator(ProjectValidators::validateModId),
                    "railroad.project.creation.mod_id.info"),
                described(
                    FormComponent.textField(MinecraftProjectKeys.MOD_NAME, "railroad.project.creation.mod_name")
                        .required()
                        .promptText("railroad.project.creation.mod_name.prompt")
                        .text(modNameProperty::get)
                        .bindTextFieldTo(modNameField)
                        .validator(ProjectValidators::validateModName),
                    "railroad.project.creation.mod_name.info"),
                described(
                    FormComponent.textField(MinecraftProjectKeys.MAIN_CLASS, "railroad.project.creation.main_class")
                        .required()
                        .promptText("railroad.project.creation.main_class.prompt")
                        .text(mainClassProperty::get)
                        .bindTextFieldTo(mainClassField)
                        .validator(ProjectValidators::validateMainClass),
                    "railroad.project.creation.main_class.info"))
            .appendSection("railroad.project.creation.section.neoforge_options",
                described(
                    FormComponent.checkBox(ForgeProjectKeys.USE_MIXINS, "railroad.project.creation.use_mixins"),
                    "railroad.project.creation.use_mixins.info"),
                described(
                    FormComponent.checkBox(ForgeProjectKeys.USE_ACCESS_TRANSFORMER, "railroad.project.creation.use_access_transformer"),
                    "railroad.project.creation.use_access_transformer.info"),
                described(
                    FormComponent.checkBox(ForgeProjectKeys.GEN_RUN_FOLDERS, "railroad.project.creation.gen_run_folders"),
                    "railroad.project.creation.gen_run_folders.info"))
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
                described(
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
                        }),
                    "railroad.project.creation.license.info"),
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.LICENSE_CUSTOM, "railroad.project.creation.license.custom")
                        .visible(customLicenseVisible)
                        .promptText("railroad.project.creation.license.custom.prompt")
                        .validator(ProjectValidators::validateCustomLicense),
                    "railroad.project.creation.license.custom.info"))
            .onEnter(ctx -> {
                List<License> newValues = License.REGISTRY.values()
                    .stream()
                    .sorted(Comparator.comparing(License::getName))
                    .toList();

                if (availableLicenses.size() != newValues.size() || !ListUtils.isEqualList(availableLicenses, newValues)) {
                    availableLicenses.clear();
                    availableLicenses.addAll(newValues);
                    ctx.markForRefresh(ProjectData.DefaultKeys.LICENSE);
                }
            })
            .build();
    }

    private OnboardingStep createGitStep() {
        return OnboardingFormStep.builder()
            .id("git")
            .title("railroad.project.creation.git.title")
            .description("railroad.project.creation.git.description")
            .appendSection("railroad.project.creation.section.git",
                described(
                    FormComponent.checkBox(ProjectData.DefaultKeys.INIT_GIT, "railroad.project.creation.init_git")
                        .selected(true),
                    "railroad.project.creation.init_git.info"))
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
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.AUTHOR, "railroad.project.creation.author")
                        .text(() -> defaultAuthor)
                        .promptText("railroad.project.creation.author.prompt")
                        .validator(ProjectValidators::validateAuthor),
                    "railroad.project.creation.author.info"),
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.CREDITS, "railroad.project.creation.credits")
                        .promptText("railroad.project.creation.credits.prompt")
                        .validator(ProjectValidators::validateCredits),
                    "railroad.project.creation.credits.info"),
                described(
                    FormComponent.textArea(ProjectData.DefaultKeys.DESCRIPTION, "railroad.project.creation.description")
                        .promptText("railroad.project.creation.description.prompt")
                        .validator(ProjectValidators::validateDescription),
                    "railroad.project.creation.description.info"),
                described(
                    FormComponent.textField(ProjectData.DefaultKeys.ISSUES_URL, "railroad.project.creation.issues_url")
                        .promptText("railroad.project.creation.issues_url.prompt")
                        .validator(ProjectValidators::validateIssues),
                    "railroad.project.creation.issues_url.info"),
                described(
                    FormComponent.textField(ForgeProjectKeys.UPDATE_JSON_URL, "railroad.project.creation.update_json_url")
                        .promptText("railroad.project.creation.update_json_url.prompt")
                        .validator(ProjectValidators::validateUpdateJsonUrl),
                    "railroad.project.creation.update_json_url.info"),
                described(
                    FormComponent.textField(ForgeProjectKeys.DISPLAY_URL, "railroad.project.creation.display_url")
                        .promptText("railroad.project.creation.display_url.prompt")
                        .validator(field -> ProjectValidators.validateGenericUrl(field, "display_url")),
                    "railroad.project.creation.display_url.info"),
                described(
                    FormComponent.comboBox(ForgeProjectKeys.DISPLAY_TEST, "railroad.project.creation.display_test", DisplayTest.class)
                        .items(() -> Arrays.asList(DisplayTest.values()))
                        .defaultValue(() -> DisplayTest.MATCH_VERSION)
                        .keyFunction(DisplayTest::name)
                        .valueOfFunction(DisplayTest::valueOf)
                        .translate(false),
                    "railroad.project.creation.display_test.info"),
                described(
                    FormComponent.checkBox(ForgeProjectKeys.CLIENT_SIDE_ONLY, "railroad.project.creation.client_side_only"),
                    "railroad.project.creation.client_side_only.info"))
            .build();
    }

    private static OnboardingFormStep.ComponentSpec described(FormComponentBuilder<?, ?, ?, ?> builder, String descriptionKey) {
        return OnboardingFormStep.component(builder, createDescriptionCustomizer(descriptionKey));
    }

    private static OnboardingFormStep.ComponentSpec described(FormComponentBuilder<?, ?, ?, ?> builder, Function<Object, Object> transformer, Function<Object, Object> reverseTransformer, String descriptionKey) {
        return OnboardingFormStep.component(builder, builder != null ? builder.dataKey() : null, transformer, reverseTransformer, createDescriptionCustomizer(descriptionKey));
    }

    private static Consumer<FormComponent<?, ?, ?, ?>> createDescriptionCustomizer(String descriptionKey) {
        if (isNullOrBlank(descriptionKey))
            return null;

        return component -> attachDescription(component, descriptionKey);
    }

    private static void attachDescription(FormComponent<?, ?, ?, ?> component, String descriptionKey) {
        if (component == null || isNullOrBlank(descriptionKey))
            return;

        Consumer<Node> applyToNode = node -> {
            if (node instanceof InformativeLabeledHBox<?> informative) {
                boolean exists = informative.getInformationLabels().stream()
                    .anyMatch(label -> descriptionKey.equals(label.getKey()));
                if (!exists) {
                    informative.addInformationLabel(descriptionKey);
                }
            }
        };

        Node currentNode = component.componentProperty().get();
        if (currentNode != null) {
            applyToNode.accept(currentNode);
        }

        component.componentProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                applyToNode.accept(newValue);
            }
        });
    }

    private static MinecraftVersion getMinecraftVersion(String string) {
        try {
            return SwitchboardRepositories.MINECRAFT.getVersionSync(string).orElse(null);
        } catch (ExecutionException | InterruptedException exception) {
            Railroad.LOGGER.error("Failed to fetch Minecraft version {}", string, exception);
            return null;
        }
    }

    private static CompletableFuture<List<MinecraftVersion>> resolveNeoforgeMinecraftVersions() {
        return NEOFORGE_MINECRAFT_VERSIONS_CACHE.getAsync(() ->
            SwitchboardRepositories.NEOFORGE.getAllVersions()
                .thenApply(versions -> versions.stream()
                    .map(NeoforgeProjectOnboarding::extractMinecraftVersionId)
                    .flatMap(Optional::stream)
                    .map(NeoforgeProjectOnboarding::lookupMinecraftVersion)
                    .flatMap(Optional::stream)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList())
        );
    }

    private static CompletableFuture<NeoforgeVersionsPayload> fetchNeoforgeVersions(MinecraftVersion version) {
        if (version == null)
            return CompletableFuture.completedFuture(new NeoforgeVersionsPayload(null, List.of(), null));

        String minecraftId = version.id();
        CompletableFuture<List<String>> versionsFuture = SwitchboardRepositories.NEOFORGE.getVersionsFor(minecraftId);
        CompletableFuture<String> latestFuture = SwitchboardRepositories.NEOFORGE.getLatestVersionFor(minecraftId);

        return versionsFuture.thenCombine(latestFuture, (versions, latest) ->
                new NeoforgeVersionsPayload(version, versions == null ? List.of() : versions, latest))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to fetch Neoforge versions for Minecraft {}", version, throwable);
                return new NeoforgeVersionsPayload(version, List.of(), null);
            });
    }

    private static MinecraftVersion determineDefaultMinecraftVersion(List<MinecraftVersion> versions) {
        if (versions == null || versions.isEmpty())
            return null;

        return versions.stream()
            .filter(version -> version != null && version.getType() == MinecraftVersion.Type.RELEASE)
            .findFirst()
            .orElseGet(versions::getFirst);
    }

    private static Optional<MinecraftVersion> lookupMinecraftVersion(String versionId) {
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

        int lastDash = neoforgeVersion.indexOf('-');
        if (lastDash <= 0)
            return Optional.empty();

        String base = neoforgeVersion.substring(0, lastDash);
        if (base.isBlank())
            return Optional.empty();

        return Optional.of(base);
    }

    private static boolean isRecommendedNeoforgeVersion(String version, String latest) {
        return version != null && Objects.equals(version, latest) && !isNeoforgePrerelease(version);
    }

    private static boolean isNeoforgePrerelease(String version) {
        if (version == null)
            return false;

        String lower = version.toLowerCase(Locale.ROOT);
        return lower.contains("beta") || lower.contains("alpha") || lower.contains("rc") || lower.contains("25w14craftmine");
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

    private record NeoforgeVersionsPayload(MinecraftVersion contextVersion, List<String> versions, String latest) {
    }
}
