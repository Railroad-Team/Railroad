package dev.railroadide.railroad.project.onboarding.impl;

import dev.railroadide.core.form.Form;
import dev.railroadide.core.form.FormComponent;
import dev.railroadide.core.form.FormSection;
import dev.railroadide.core.form.ValidationResult;
import dev.railroadide.core.form.impl.ComboBoxComponent;
import dev.railroadide.core.form.impl.DirectoryChooserComponent;
import dev.railroadide.core.form.impl.TextFieldComponent;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.project.creation.ProjectCreationService;
import dev.railroadide.core.project.creation.ProjectServiceRegistry;
import dev.railroadide.core.project.creation.service.GradleService;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.project.creation.ui.ProjectCreationPane;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.railroad.project.details.ProjectValidators;
import dev.railroadide.railroad.project.onboarding.*;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
import dev.railroadide.railroad.switchboard.repositories.FabricApiVersionRepository;
import javafx.beans.property.*;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FabricProjectOnboarding {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public void start(Scene scene) {
        // TODO: Turn flow into a builder
        var flow = new OnboardingFlow(
            Map.of(
                "project_details", ProjectDetailsStep::new,
                "minecraft_version", MinecraftVersionStep::new
            ),
            List.of("project_details", "minecraft_version")
        );

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

    public static class ProjectDetailsStep implements OnboardingStep {
        private final BooleanProperty valid = new SimpleBooleanProperty(false);

        private final ObjectProperty<TextField> projectNameField = new SimpleObjectProperty<>();
        private final ObjectProperty<TextField> projectPathField = new SimpleObjectProperty<>();
        private final OnboardingSection section;

        public ProjectDetailsStep() {
            TextFieldComponent projectNameComponent = FormComponent.textField(ProjectData.DefaultKeys.NAME, "railroad.project.creation.name")
                .required()
                .bindTextFieldTo(projectNameField)
                .promptText("railroad.project.creation.name.prompt")
                .validator(ProjectValidators::validateProjectName)
                .listener((node, observable, oldValue, newValue) ->
                    valid.set(ProjectValidators.validateProjectName(projectNameField.get()).status() != ValidationResult.Status.ERROR
                        && ProjectValidators.validatePath(projectPathField.get()).status() != ValidationResult.Status.ERROR))
                .build();

            DirectoryChooserComponent projectPathComponent = FormComponent.directoryChooser(ProjectData.DefaultKeys.PATH, "railroad.project.creation.location")
                .required()
                .defaultPath(System.getProperty("user.home"))
                .bindTextFieldTo(projectPathField)
                .validator(ProjectValidators::validatePath)
                .listener((node, observable, oldValue, newValue) ->
                    valid.set(ProjectValidators.validateProjectName(projectNameField.get()).status() != ValidationResult.Status.ERROR
                        && ProjectValidators.validatePath(projectPathField.get()).status() != ValidationResult.Status.ERROR))
                .build();

            Form form = Form.create()
                .spacing(15)
                .padding(10)
                .appendSection(FormSection.create("railroad.project.creation.section.project")
                    .borderColor(Color.DARKGRAY)
                    .appendComponent(projectNameComponent)
                    .appendComponent(projectPathComponent)
                    .build())
                .disableResetButton()
                .disableSubmitButton()
                .build();

            this.section = new FormOnboardingSection(form);
        }

        @Override
        public String id() {
            return "project_details";
        }

        @Override
        public String title() {
            return "Project Details";
        }

        @Override
        public String description() {
            return "Enter the basic details for your Fabric project.";
        }

        @Override
        public OnboardingSection section() {
            return this.section;
        }

        @Override
        public ReadOnlyBooleanProperty validProperty() {
            return valid;
        }

        @Override
        public void onExit(OnboardingContext ctx) {
            ctx.put(ProjectData.DefaultKeys.NAME, this.projectNameField.get().getText());

            // TODO: How tf are we meant to handle this later on
            ctx.put(ProjectData.DefaultKeys.PATH, Path.of(this.projectPathField.get().getText()));
        }
    }

    public static class MinecraftVersionStep implements OnboardingStep {
        private final BooleanProperty valid = new SimpleBooleanProperty(true); // Always valid since there's no input

        private final ObjectProperty<ComboBox<MinecraftVersion>> minecraftVersionComboBox = new SimpleObjectProperty<>();
        private final OnboardingSection section;

        private final List<MinecraftVersion> availableVersions = new ArrayList<>();
        private long nextInvalidationTime = 0;

        public MinecraftVersionStep() {
            ComboBoxComponent<MinecraftVersion> minecraftVersionComponent = FormComponent.comboBox(MinecraftProjectKeys.MINECRAFT_VERSION, "railroad.project.creation.minecraft_version", MinecraftVersion.class)
                .items(availableVersions)
                .defaultValue(() -> determineDefaultMinecraftVersion(this.availableVersions))
                .bindComboBoxTo(minecraftVersionComboBox)
                .keyFunction(MinecraftVersion::id)
                .valueOfFunction(string -> {
                    try {
                        return SwitchboardRepositories.MINECRAFT.getVersionSync(string).orElse(null);
                    } catch (ExecutionException | InterruptedException exception) {
                        Railroad.LOGGER.error("Failed to fetch Minecraft version {}", string, exception);
                        return null;
                    }
                })
                .translate(false)
                .build();

            Form form = Form.create()
                .spacing(15)
                .padding(10)
                .appendSection(FormSection.create("railroad.project.creation.section.minecraft_version")
                    .appendComponent(minecraftVersionComponent)
                    .borderColor(Color.DARKGRAY)
                    .build())
                .disableResetButton()
                .disableSubmitButton()
                .build();

            this.section = new FormOnboardingSection(form);
        }

        @Override
        public String id() {
            return "minecraft_version";
        }

        @Override
        public String title() {
            return "Minecraft Version";
        }

        @Override
        public String description() {
            return "Select the Minecraft version for your Fabric project.";
        }

        @Override
        public OnboardingSection section() {
            return this.section;
        }

        @Override
        public ReadOnlyBooleanProperty validProperty() {
            return valid;
        }

        @Override
        public void onEnter(OnboardingContext ctx) {
            try {
                if (this.availableVersions.isEmpty() || System.currentTimeMillis() > this.nextInvalidationTime) {
                    this.availableVersions.clear();
                    this.availableVersions.addAll(SwitchboardRepositories.FABRIC_API.getAllVersionsSync().stream()
                        .map(FabricApiVersionRepository::getMinecraftVersion)
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
                        .toList());
                    this.nextInvalidationTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
                }
            } catch (ExecutionException | InterruptedException exception) {
                Railroad.LOGGER.error("Failed to fetch Fabric API versions", exception);
            }
        }

        @Override
        public void onExit(OnboardingContext ctx) {
            ctx.put(MinecraftProjectKeys.MINECRAFT_VERSION, this.minecraftVersionComboBox.get().getValue());
        }

        private MinecraftVersion determineDefaultMinecraftVersion(List<MinecraftVersion> versions) {
            if (versions == null || versions.isEmpty())
                return null;

            return versions.stream()
                .filter(version -> version != null && version.getType() == MinecraftVersion.Type.RELEASE)
                .findFirst()
                .orElseGet(versions::getFirst);
        }
    }
}
