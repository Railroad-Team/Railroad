package io.github.railroad.project.facet.data;

import lombok.Data;

@Data
public class MavenFacetData {
    private String pomFilePath;
    private String groupId;
    private String artifactId;
    private String version;
}
