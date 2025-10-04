package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.project.ProjectType;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.service.HttpService;
import dev.railroadide.core.project.creation.service.TemplateEngineService;
import dev.railroadide.core.project.minecraft.MappingChannel;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.project.MappingChannelRegistry;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.project.creation.ProjectContextKeys;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import groovy.lang.Binding;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record UpdateGradleFilesStep(FilesService files, HttpService http, TemplateEngineService templateEngine,
                                    String branch, boolean includeSettingsGradle) implements CreationStep {
    private static final String TEMPLATE_BUILD_GRADLE_URL = "https://raw.githubusercontent.com/Railroad-Team/Railroad/%s/templates/fabric/%s/template_build.gradle";
    private static final String TEMPLATE_SETTINGS_GRADLE_URL = "https://raw.githubusercontent.com/Railroad-Team/Railroad/%s/templates/fabric/%s/template_settings.gradle";

    @Override
    public String id() {
        return "railroad:update_gradle_files";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.update_gradle_files";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        updateBuildGradle(ctx, reporter);
        if (includeSettingsGradle)
            updateSettingsGradle(ctx, reporter);
    }

    private void updateBuildGradle(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Downloading template build.gradle...");

        Path projectDir = ctx.projectDir();
        Path buildGradlePath = projectDir.resolve("build.gradle");

        MinecraftVersion mdkVersion = ctx.get(ProjectContextKeys.MDK_VERSION);
        if (mdkVersion == null)
            throw new IllegalStateException("MDK version not set in project context");

        String templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(branch, mdkVersion.id().substring("1.".length()));
        if (http.isNotFound(new URI(templateBuildGradleUrl))) {
            MinecraftVersion minecraftVersion = ctx.data().get(MinecraftProjectKeys.MINECRAFT_VERSION, MinecraftVersion.class);
            if (minecraftVersion == null)
                throw new IllegalStateException("Minecraft version not set in project context");

            templateBuildGradleUrl = TEMPLATE_BUILD_GRADLE_URL.formatted(branch, minecraftVersion.id().substring("1.".length()));
            if (http.isNotFound(new URI(templateBuildGradleUrl)))
                throw new IllegalStateException("Template build.gradle not found for version " + mdkVersion.id() + " or " + minecraftVersion.id());
        }

        Path templateBuildGradlePath = buildGradlePath.resolveSibling("template_build.gradle");
        http.download(new URI(templateBuildGradleUrl), templateBuildGradlePath);

        reporter.info("Updating build.gradle...");
        String templateContent = files.readString(templateBuildGradlePath);
        if (!templateContent.startsWith("// fileName: "))
            throw new IllegalStateException("Invalid template build.gradle file: missing fileName metadata");

        updateContent(ctx, projectDir, buildGradlePath, templateBuildGradlePath, templateContent);
    }

    private void updateSettingsGradle(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Downloading template settings.gradle...");

        Path projectDir = ctx.projectDir();
        Path settingsGradlePath = projectDir.resolve("settings.gradle");

        MinecraftVersion mdkVersion = ctx.get(ProjectContextKeys.MDK_VERSION);
        if (mdkVersion == null)
            throw new IllegalStateException("MDK version not set in project context");

        String templateSettingsGradleUrl = TEMPLATE_SETTINGS_GRADLE_URL.formatted(branch, mdkVersion.id().substring("1.".length()));
        if (http.isNotFound(new URI(templateSettingsGradleUrl))) {
            MinecraftVersion minecraftVersion = ctx.data().get(MinecraftProjectKeys.MINECRAFT_VERSION, MinecraftVersion.class);
            if (minecraftVersion == null)
                throw new IllegalStateException("Minecraft version not set in project context");

            templateSettingsGradleUrl = TEMPLATE_SETTINGS_GRADLE_URL.formatted(branch, minecraftVersion.id().substring("1.".length()));
            if (http.isNotFound(new URI(templateSettingsGradleUrl)))
                throw new IllegalStateException("Template settings.gradle not found for version " + mdkVersion.id() + " or " + minecraftVersion.id());
        }

        Path templateSettingsGradlePath = settingsGradlePath.resolveSibling("template_settings.gradle");
        http.download(new URI(templateSettingsGradleUrl), templateSettingsGradlePath);

        reporter.info("Updating settings.gradle...");
        String templateContent = files.readString(templateSettingsGradlePath);
        if (!templateContent.startsWith("// fileName: "))
            throw new IllegalStateException("Invalid template settings.gradle file: missing fileName metadata");

        updateContent(ctx, projectDir, settingsGradlePath, templateSettingsGradlePath, templateContent);
    }

    private void updateContent(ProjectContext ctx, Path projectDir, Path settingsGradlePath, Path templateSettingsGradlePath, String templateContent) throws Exception {
        Map<String, Object> args = createGradleBindings(ctx.data());
        var binding = new Binding(args);
        binding.setVariable("defaultName", projectDir.relativize(settingsGradlePath.toAbsolutePath()).toString());

        String updatedContent = templateEngine.apply(templateContent, args);
        files.writeString(settingsGradlePath, updatedContent);
        files.delete(templateSettingsGradlePath);
    }

    private static Map<String, Object> createGradleBindings(ProjectData data) {
        ProjectType projectType = data.get(ProjectData.DefaultKeys.TYPE, ProjectType.class);
        if (projectType == null)
            throw new IllegalStateException("Project type not set in project data");

        MappingChannel defaultChannel = getDefaultMappingChannel(projectType);

        final Map<String, Object> args = new HashMap<>();
        args.put("mappings", Map.of(
            "channel", data.getOrDefault(MinecraftProjectKeys.MAPPING_CHANNEL, defaultChannel, MappingChannel.class).id().toLowerCase(Locale.ROOT),
            "version", data.getAsString(MinecraftProjectKeys.MAPPING_VERSION)
        ));

        Map<String, Object> props = new HashMap<>();
        if(projectType == ProjectTypeRegistry.FABRIC) {
            props.putAll(Map.of(
                "splitSourceSets", data.getAsBoolean(FabricProjectKeys.SPLIT_SOURCES),
                "includeFabricApi", data.contains(FabricProjectKeys.FABRIC_API_VERSION),
                "useAccessWidener", data.getAsBoolean(FabricProjectKeys.USE_ACCESS_WIDENER),
                "modId", data.getAsString(MinecraftProjectKeys.MOD_ID)
            ));
        } else if(projectType == ProjectTypeRegistry.FORGE || projectType == ProjectTypeRegistry.NEOFORGE) {
            props.putAll(Map.of(
                "useMixins", data.getAsBoolean(ForgeProjectKeys.USE_MIXINS),
                "useAccessTransformer", data.getAsBoolean(ForgeProjectKeys.USE_ACCESS_TRANSFORMER),
                "genRunFolders", data.getAsBoolean(ForgeProjectKeys.GEN_RUN_FOLDERS)
            ));
        } else {
            throw new IllegalStateException("Unsupported project type: " + projectType);
        }

        args.put("props", props);
        return args;
    }

    public static MappingChannel getDefaultMappingChannel(ProjectType projectType) {
        if (projectType.equals(ProjectTypeRegistry.FABRIC)) {
            return MappingChannelRegistry.YARN;
        } else if (projectType.equals(ProjectTypeRegistry.FORGE)) {
            return MappingChannelRegistry.MOJMAP;
        } else if (projectType.equals(ProjectTypeRegistry.NEOFORGE)) {
            return MappingChannelRegistry.PARCHMENT;
        }

        throw new IllegalStateException("Unsupported project type: " + projectType);
    }
}
