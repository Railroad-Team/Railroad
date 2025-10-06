package dev.railroadide.railroad.project.onboarding.impl;

import dev.railroadide.core.form.FormComponent;
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
import javafx.scene.Scene;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class FabricProjectOnboarding {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public void start(Scene scene) {
        // TODO: Turn flow into a builder
        var flow = new OnboardingFlow(
            Map.of(
                "project_details", this::createProjectDetailsStep,
                "minecraft_version", this::createMinecraftVersionStep
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
                    }))
            .build();
    }

    private OnboardingStep createMinecraftVersionStep() {
        List<MinecraftVersion> availableVersions = new ArrayList<>();
        var nextInvalidationTime = new AtomicLong(0L);

        return OnboardingFormStep.builder()
            .id("minecraft_version")
            .title("railroad.project.creation.minecraft_version.title")
            .description("railroad.project.creation.minecraft_version.description")
            .appendSection("railroad.project.creation.section.minecraft_version",
                OnboardingFormStep.component(
                    FormComponent.comboBox(MinecraftProjectKeys.MINECRAFT_VERSION, "railroad.project.creation.minecraft_version", MinecraftVersion.class)
                        .items(availableVersions)
                        .defaultValue(() -> MinecraftVersion.determineDefaultMinecraftVersion(availableVersions))
                        .keyFunction(MinecraftVersion::id)
                        .valueOfFunction(FabricProjectOnboarding::getMinecraftVersion)
                        .translate(false)))
            .onEnter(ctx -> {
                if (availableVersions.isEmpty() || System.currentTimeMillis() > nextInvalidationTime.get()) {
                    availableVersions.clear();
                    availableVersions.addAll(getMinecraftVersions());
                    nextInvalidationTime.set(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));
                }
            })
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
}
