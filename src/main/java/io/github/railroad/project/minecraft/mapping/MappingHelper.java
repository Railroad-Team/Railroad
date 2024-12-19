package io.github.railroad.project.minecraft.mapping;

import io.github.railroad.Railroad;
import io.github.railroad.project.minecraft.MinecraftVersion;
import io.github.railroad.project.minecraft.VersionRange;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

// TODO: Fix mappings for pre-1.7.10 versions (MCP)
public class MappingHelper {
    public static void loadMappings(ObservableList<MappingChannel> options, MinecraftVersion mcVersion) {
        options.clear();

        options.addAll(getChannels(mcVersion));
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

    public static Collection<MappingChannel> getChannels(MinecraftVersion minecraftVersion) {
        if (minecraftVersion == null)
            return Collections.emptyList();

        List<MappingChannel> options = new ArrayList<>();
        String version = minecraftVersion.id().substring(2);

        double versionNumber;
        try {
            versionNumber = Double.parseDouble(version);
        } catch (NumberFormatException exception) {
            options.add(MappingChannel.MOJMAP);
            Railroad.LOGGER.error("Failed to parse Minecraft version number: {}", version, exception);
            return options;
        }

        for (MappingChannel channel : MappingChannel.values()) {
            try {
                var range = VersionRange.parse(channel.getMinecraftVersionRange());
                if (range.contains(versionNumber))
                    options.add(channel);
            } catch (NumberFormatException exception) {
                Railroad.LOGGER.error("Failed to parse version range for {}: {}", channel, channel.getMinecraftVersionRange(), exception);
            }
        }

        return options;
    }
}