package dev.railroadide.railroad.project.onboarding.creation;

import dev.railroadide.railroad.project.ProjectContext;
import dev.railroadide.railroad.project.creation.modjson.FabricModJson;
import dev.railroadide.railroad.switchboard.pojo.MinecraftVersion;

public class ProjectContextKeys {
    public static final ProjectContext.Key<MinecraftVersion> MDK_VERSION = new ProjectContext.Key<>("mdk_version");
    public static final ProjectContext.Key<String> EXAMPLE_MOD_BRANCH = new ProjectContext.Key<>("example_mod_branch");
    public static final ProjectContext.Key<FabricModJson> FABRIC_MOD_JSON = new ProjectContext.Key<>("fabric_mod_json");
}
