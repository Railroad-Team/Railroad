package dev.railroadide.railroad.project.data;

import dev.railroadide.railroad.project.DisplayTest;
import dev.railroadide.railroad.project.License;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.MappingChannel;

import java.nio.file.Path;
import java.util.Optional;

public record ForgeProjectData(String projectName, Path projectPath, boolean createGit, License license,
                               String licenseCustom,
                               MinecraftVersion minecraftVersion, String forgeVersion, String modId,
                               String modName, String mainClass, boolean useMixins, boolean useAccessTransformer,
                               boolean genRunFolders,
                               MappingChannel mappingChannel, String mappingVersion,
                               Optional<String> author, Optional<String> credits, Optional<String> description,
                               Optional<String> issues, Optional<String> updateJsonUrl, Optional<String> displayUrl,
                               DisplayTest displayTest, boolean clientSideOnly,
                               String groupId, String artifactId, String version) {
}
