package io.github.railroad.project.facet.data;

import lombok.Data;

@Data
public class MinecraftModFacetData {
    private String modId;
    private String version;
    private String displayName;
    private String description;
    private String authors;
    private String contributors;
    private String license;
    private String iconPath;
    private String logoPath;
    private String websiteUrl;
    private String sourceUrl;
    private String issuesUrl;
    private String changelogUrl;

    private String buildFilePath;

    private String minecraftVersion;
}
