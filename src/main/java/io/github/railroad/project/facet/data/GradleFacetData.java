package io.github.railroad.project.facet.data;

import lombok.Data;

@Data
public class GradleFacetData {
    private String gradleVersion;
    private String buildFilePath;
    private boolean isKts;
}
