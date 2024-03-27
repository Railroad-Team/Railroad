package io.github.railroad.minecraft.mapping;

public enum MappingChannel {
    MOJMAP("Mojmap", "[16.5,)"),
    MCP("MCP", "[7.10,16.5]"),
    PARCHMENT("Parchment", "[16.5,)"),
    YARN("Yarn", "[14,)");

    private final String name;
    private final String minecraftVersionRange;

    MappingChannel(String name, String minecraftVersionRange) {
        this.name = name;
        this.minecraftVersionRange = minecraftVersionRange;
    }

    public String getName() {
        return name;
    }

    public String getMinecraftVersionRange() {
        return minecraftVersionRange;
    }
}
