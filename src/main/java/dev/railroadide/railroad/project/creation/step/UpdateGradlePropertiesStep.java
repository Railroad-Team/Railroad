package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.License;
import dev.railroadide.core.project.ProjectType;
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.project.minecraft.MappingChannel;
import dev.railroadide.railroad.project.MappingChannelRegistry;
import dev.railroadide.core.switchboard.pojo.FabricLoaderVersion;
import dev.railroadide.core.switchboard.pojo.MinecraftVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public record UpdateGradlePropertiesStep(FilesService files) implements CreationStep {
    @Override
    public String id() {
        return "railroad:update_gradle_properties";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.updating_gradle";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws IOException {
        Path propsFile = ctx.projectDir().resolve("gradle.properties");
        if (!files.exists(propsFile))
            throw new IOException("gradle.properties file not found!");

        ProjectType projectType = ctx.data().get(ProjectData.DefaultKeys.TYPE, ProjectType.class);
        if (projectType == null)
            throw new IllegalStateException("Project type not set in project context");

        reporter.info("Updating gradle.properties...");

        files.updateKeyPairInPropertiesFile(propsFile, "org.gradle.jvmargs", "-Xmx4G");

        MappingChannel mappingChannel = ctx.data().get(MinecraftProjectKeys.MAPPING_CHANNEL, MappingChannel.class);
        if (mappingChannel == null)
            mappingChannel = UpdateGradleFilesStep.getDefaultMappingChannel(projectType);
        String channelId = mappingChannel.id().toLowerCase(Locale.ROOT);
        if (channelId.equals("mojmap"))
            channelId = "official";

        String mappingVersion = ctx.data().getAsString(MinecraftProjectKeys.MAPPING_VERSION);
        if (mappingVersion == null)
            throw new IllegalStateException("Mapping version not set in project context");

        MinecraftVersion minecraftVersion = ctx.data().get(MinecraftProjectKeys.MINECRAFT_VERSION, MinecraftVersion.class);
        if (minecraftVersion == null)
            throw new IllegalStateException("Minecraft version not set in project context");

        String modId = ctx.data().getAsString(MinecraftProjectKeys.MOD_ID);
        String modName = ctx.data().getAsString(MinecraftProjectKeys.MOD_NAME);
        License license = ctx.data().getOrDefault(ProjectData.DefaultKeys.LICENSE, LicenseRegistry.LGPL, License.class);
        String licenseStr = license == LicenseRegistry.CUSTOM ? ctx.data().getAsString(ProjectData.DefaultKeys.LICENSE_CUSTOM) : license.getSpdxId();

        String version = ctx.data().getAsString(MavenProjectKeys.VERSION);
        String groupId = ctx.data().getAsString(MavenProjectKeys.GROUP_ID);

        String authors = ctx.data().getAsString(ProjectData.DefaultKeys.AUTHOR, "");
        String description = ctx.data().getAsString(ProjectData.DefaultKeys.DESCRIPTION, "");

        if (projectType.equals(ProjectTypeRegistry.FABRIC)) {
            FabricLoaderVersion loaderVersion = ctx.data().get(FabricProjectKeys.FABRIC_LOADER_VERSION, FabricLoaderVersion.class);
            if (loaderVersion == null)
                throw new IllegalStateException("Fabric Loader version not set in project context");

            String fabricApiVersion = ctx.data().getAsString(FabricProjectKeys.FABRIC_API_VERSION, "");

            files.updateKeyPairInPropertiesFile(propsFile, "minecraft_version", minecraftVersion.id());
            files.updateKeyPairInPropertiesFile(propsFile, "loader_version", loaderVersion.version());
            files.updateKeyPairInPropertiesFile(propsFile, "fabric_version", fabricApiVersion);
            if (mappingChannel == MappingChannelRegistry.YARN) {
                files.updateKeyPairInPropertiesFile(propsFile, "yarn_mappings", mappingVersion);
            } else if (mappingChannel == MappingChannelRegistry.PARCHMENT) {
                files.updateKeyPairInPropertiesFile(propsFile, "parchment_version", mappingVersion);
            }

            files.updateKeyPairInPropertiesFile(propsFile, "mod_version", version);
            files.updateKeyPairInPropertiesFile(propsFile, "maven_group", groupId);
            files.updateKeyPairInPropertiesFile(propsFile, "archives_base_name", ctx.data().getAsString(MavenProjectKeys.ARTIFACT_ID, modId));
        } else if (projectType.equals(ProjectTypeRegistry.FORGE) || projectType.equals(ProjectTypeRegistry.NEOFORGE)) {// TODO: Confirm for neoforge
            files.updateKeyPairInPropertiesFile(propsFile, "mapping_channel", channelId);
            files.updateKeyPairInPropertiesFile(propsFile, "mapping_version", mappingVersion);

            files.updateKeyPairInPropertiesFile(propsFile, "mod_id", modId);
            files.updateKeyPairInPropertiesFile(propsFile, "mod_name", modName);
            files.updateKeyPairInPropertiesFile(propsFile, "mod_license", licenseStr);

            files.updateKeyPairInPropertiesFile(propsFile, "mod_version", version);
            files.updateKeyPairInPropertiesFile(propsFile, "mod_group_id", groupId);

            files.updateKeyPairInPropertiesFile(propsFile, "mod_authors", authors);
            files.updateKeyPairInPropertiesFile(propsFile, "mod_description", "'''" + description + "'''");
        } else {
            throw new IllegalStateException("Unsupported project type: " + projectType.getName());
        }
    }
}
