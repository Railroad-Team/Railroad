package io.github.railroad.minecraft.mapping;

import io.github.railroad.minecraft.MinecraftVersion;

public class MappingVersion {
    private final MinecraftVersion minecraftVersion;
    private final String id;
    private final boolean isLatest;

    public MappingVersion(MinecraftVersion minecraftVersion, String id, boolean isLatest) {
        this.minecraftVersion = minecraftVersion;
        this.id = id;
        this.isLatest = isLatest;
    }

    public MinecraftVersion getMinecraftVersion() {
        return minecraftVersion;
    }

    public String getId() {
        return id;
    }

    public boolean isLatest() {
        return isLatest;
    }
}
