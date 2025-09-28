package dev.railroadide.railroad.project.data;


import dev.railroadide.railroad.project.License;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.fabric.FabricLoaderVersionService.FabricLoaderVersion;
import dev.railroadide.railroad.project.minecraft.mappings.MappingChannel;

import java.nio.file.Path;
import java.util.Optional;

public record FabricProjectData(String projectName, Path projectPath, boolean createGit, License license,
                                String licenseCustom,
                                MinecraftVersion minecraftVersion, FabricLoaderVersion fabricLoaderVersion,
                                Optional<String> fapiVersion,
                                String modId, String modName, String mainClass, boolean useAccessWidener,
                                boolean splitSources,
                                MappingChannel mappingChannel, String mappingVersion,
                                Optional<String> author, Optional<String> description, Optional<String> issues,
                                Optional<String> homepage, Optional<String> sources,
                                String groupId, String artifactId, String version) {
}
