package io.github.railroad.project;


import io.github.railroad.project.minecraft.FabricAPIVersion;
import io.github.railroad.project.minecraft.FabricLoaderVersion;
import io.github.railroad.project.minecraft.MinecraftVersion;
import io.github.railroad.project.minecraft.mapping.MappingChannel;
import io.github.railroad.project.minecraft.mapping.MappingVersion;

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
                                Optional<String> homepage, Optional<String> sources,
                                String groupId, String artifactId, String version) {
}