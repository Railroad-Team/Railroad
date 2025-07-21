package dev.railroadide.railroad.project.minecraft.mapping;

import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import lombok.Getter;

@Getter
public class MappingVersion {
    private final MinecraftVersion minecraftVersion;
    private final String id;
    private final boolean latest;

    public MappingVersion(MinecraftVersion minecraftVersion, String id, boolean latest) {
        this.minecraftVersion = minecraftVersion;
        this.id = id;
        this.latest = latest;
    }
}
