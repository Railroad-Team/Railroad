package dev.railroadide.railroad.project.onboarding.creation;

import dev.railroadide.railroad.project.ProjectContext;
import dev.railroadide.railroad.project.creation.modjson.FabricModJson;
import dev.railroadide.railroad.switchboard.pojo.MinecraftVersion;

/** Keys for intermediate results shared between project creation steps. */
public class ProjectContextKeys {
    /** Minecraft version selected as the basis for the Fabric example mod and Gradle templates. */
    public static final ProjectContext.Key<MinecraftVersion> MDK_VERSION = new ProjectContext.Key<>("mdk_version");
    /** Branch name used to locate the extracted Fabric example mod directory. */
    public static final ProjectContext.Key<String> EXAMPLE_MOD_BRANCH = new ProjectContext.Key<>("example_mod_branch");
    /** Updated Fabric mod metadata, stored after the configuration has been written successfully. */
    public static final ProjectContext.Key<FabricModJson> FABRIC_MOD_JSON = new ProjectContext.Key<>("fabric_mod_json");
}
