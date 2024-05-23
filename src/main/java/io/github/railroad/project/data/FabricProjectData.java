package io.github.railroad.project.data;


import io.github.railroad.minecraft.FabricAPIVersion;
import io.github.railroad.minecraft.FabricLoaderVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.minecraft.mapping.MappingChannel;
import io.github.railroad.minecraft.mapping.MappingVersion;
import io.github.railroad.project.License;

import java.nio.file.Path;
import java.util.Optional;

public record FabricProjectData(String projectName, Path projectPath, boolean createGit, License license,
                                String licenseCustom,
                                MinecraftVersion minecraftVersion, FabricLoaderVersion fabricLoaderVersion,
                                Optional<FabricAPIVersion> fapiVersion,
                                String modId, String modName, String mainClass, boolean useAccessWidener,
                                boolean splitSources,
                                MappingChannel mappingChannel, MappingVersion mappingVersion,
                                Optional<String> author, Optional<String> description, Optional<String> issues,
                                String groupId, String artifactId, String version) {
}