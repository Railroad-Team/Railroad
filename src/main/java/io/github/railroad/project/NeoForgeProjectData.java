package io.github.railroad.project;

import io.github.railroad.project.minecraft.MinecraftVersion;
import io.github.railroad.project.minecraft.NeoForgeVersion;
import io.github.railroad.project.minecraft.mapping.MappingChannel;
import io.github.railroad.project.minecraft.mapping.MappingVersion;

import java.nio.file.Path;
import java.util.Optional;

public record NeoForgeProjectData(String projectName, Path projectPath, boolean createGit, License license,
                                  String licenseCustom,
                                  MinecraftVersion minecraftVersion, NeoForgeVersion neoForgeVersion, String modId,
                                  String modName, String mainClass, boolean useMixins, boolean useAccessTransformer,
                                  boolean genRunFolders,
                                  MappingChannel mappingChannel, MappingVersion mappingVersion,
                                  Optional<String> author, Optional<String> credits, Optional<String> description,
                                  Optional<String> issues, Optional<String> updateJsonUrl, Optional<String> displayUrl,
                                  DisplayTest displayTest, boolean clientSideOnly,
                                  String groupId, String artifactId, String version) {
}