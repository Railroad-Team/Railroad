package dev.railroadide.railroad.project.facet.data;

import lombok.Data;

/**
 * Base class for facet data describing a Minecraft mod project.
 * Contains common metadata fields for mods, such as id, version, authors, and links.
 */
@Data
public class MinecraftModFacetData {
    /**
     * The mod ID (unique identifier).
     */
    private String modId;
    /**
     * The mod version.
     */
    private String version;
    /**
     * The display name of the mod.
     */
    private String displayName;
    /**
     * The description of the mod.
     */
    private String description;
    /**
     * The authors of the mod.
     */
    private String authors;
    /**
     * The contributors to the mod.
     */
    private String contributors;
    /**
     * The license of the mod.
     */
    private String license;
    /**
     * The path to the mod's icon.
     */
    private String iconPath;
    /**
     * The path to the mod's logo.
     */
    private String logoPath;
    /**
     * The website URL for the mod.
     */
    private String websiteUrl;
    /**
     * The source code URL for the mod.
     */
    private String sourceUrl;
    /**
     * The issues tracker URL for the mod.
     */
    private String issuesUrl;
    /**
     * The changelog URL for the mod.
     */
    private String changelogUrl;

    /**
     * The path to the build file for the mod project.
     */
    private String buildFilePath;

    /**
     * The Minecraft version targeted by the mod.
     */
    private String minecraftVersion;
}
