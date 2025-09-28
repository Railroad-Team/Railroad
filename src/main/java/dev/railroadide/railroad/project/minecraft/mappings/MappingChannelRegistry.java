package dev.railroadide.railroad.project.minecraft.mappings;

import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.VersionService;
import dev.railroadide.railroad.project.minecraft.mappings.MCPVersionService;
import dev.railroadide.railroad.project.minecraft.mappings.ParchmentVersionService;
import dev.railroadide.railroad.project.minecraft.mappings.YarnVersionService;

import java.util.Collections;
import java.util.List;

public class MappingChannelRegistry {
    public static final Registry<MappingChannel> REGISTRY = RegistryManager.createRegistry("railroad:mapping_channel", MappingChannel.class);

    public static final MappingChannel MCP = register("mcp", MCPVersionService.INSTANCE);
    public static final MappingChannel MOJMAP = register("mojmap", MappingChannel.builder()
        .versionLister(minecraftVersion -> {
            if (MinecraftVersion.fromId("1.14.4").map(minecraftVersion::equals).orElse(false))
                return Collections.emptyList();

            return Collections.singletonList(minecraftVersion.id());
        }));
    public static final MappingChannel YARN = register("yarn", YarnVersionService.INSTANCE);
    public static final MappingChannel PARCHMENT = register("parchment", new MappingChannel.Builder()
        .versionLister(ParchmentVersionService.INSTANCE::listVersionsFor));

    public static MappingChannel register(String id, MappingChannel.Builder channel) {
        return REGISTRY.register(id, channel.build(id));
    }

    public static MappingChannel register(String id, VersionService<String> versionService) {
        return REGISTRY.register(id, MappingChannel.builder()
            .versionLister(versionService::listVersionsFor)
            .build(id));
    }

    public static List<MappingChannel> findValidMappingChannels(MinecraftVersion minecraftVersion) {
        return REGISTRY.values().stream()
            .filter(channel -> {
                try {
                    return channel.supports(minecraftVersion);
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Failed to check if mapping channel {} supports Minecraft version {}", channel.id(), minecraftVersion.id(), exception);
                    return false;
                }
            })
            .toList();
    }
}
