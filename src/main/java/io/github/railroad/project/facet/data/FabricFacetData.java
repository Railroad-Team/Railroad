package io.github.railroad.project.facet.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Holds information about the Fabric modding platform configuration for a project facet.
 * Used by the Fabric facet to describe loader, API, mappings, and build details.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FabricFacetData extends MinecraftModFacetData {
    /**
     * The version of Fabric Loader used by the project.
     */
    private String fabricLoaderVersion;
    /**
     * The version of Fabric API used by the project.
     */
    private String fabricApiVersion;
    /**
     * The version of Yarn mappings used by the project.
     */
    private String yarnMappingsVersion;
    /**
     * The version of Loom used by the project.
     */
    private String loomVersion;
}
