package io.github.railroad.minecraft.mapping;

import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.minecraft.VersionRange;
import javafx.collections.ObservableList;

import java.util.Arrays;

// TODO: Fix mappings for pre-1.7.10 versions (MCP)
public class MappingHelper {
    public static void loadMappings(ObservableList<MappingChannel> options, MinecraftVersion mcVersion) {
        options.clear();

        String version = mcVersion.id().substring(2);
        try {
            double versionNumber = Double.parseDouble(version);
            for (MappingChannel channel : MappingChannel.values()) {
                var range = VersionRange.parse(channel.getMinecraftVersionRange());
                if (range.contains(versionNumber))
                    options.add(channel);
            }
        } catch (NumberFormatException exception) {
            options.addAll(Arrays.asList(MappingChannel.values()));
        }
    }

    public static void loadMappingsVersions(ObservableList<MappingVersion> options, MinecraftVersion mcVersion, MappingChannel mappingsChannel) {
        options.clear();

        switch (mappingsChannel) {
            case MOJMAP ->
                    options.add(new MappingVersion(mcVersion, mcVersion.id(), MinecraftVersion.isLatest(mcVersion)));
            case MCP -> options.addAll(MCPVersion.getMCPVersions(mcVersion));
            case PARCHMENT -> options.addAll(ParchmentVersion.getParchmentVersions(mcVersion));
            case YARN -> options.addAll(YarnVersion.getYarnVersions(mcVersion));
        }
    }
}