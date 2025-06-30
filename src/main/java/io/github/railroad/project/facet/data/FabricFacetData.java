package io.github.railroad.project.facet.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FabricFacetData extends MinecraftModFacetData {
    private String fabricLoaderVersion;
    private String fabricApiVersion;
    private String yarnMappingsVersion;
    private String loomVersion;
}
