package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.mixin.MixinConfig;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

public record CreateMixinsJsonStep(FilesService files) implements CreationStep {
    @Override
    public String id() {
        return "railroad:create_mixins_json";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.create_mixins_json";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        if(ctx.data().getAsBoolean(ForgeProjectKeys.USE_MIXINS, false)) {
            reporter.info("Skipping mixins.json creation as mixins are not used.");
            Thread.sleep(1000); // sleeping to allow the user to read the message
            return;
        }

        reporter.info("Creating mixins.json...");

        Path mixinsPath = ctx.projectDir().resolve("src/main/resources").resolve(ctx.data().getAsString(MinecraftProjectKeys.MOD_ID) + ".mixins.json");

        var config = new MixinConfig();
        config.setRequired(true);
        config.setMinVersion("0.8");
        config.setPackageName(ctx.data().getAsString(MavenProjectKeys.GROUP_ID) + "." + ctx.data().getAsString(MavenProjectKeys.ARTIFACT_ID) + ".mixins");
        config.setCompatibilityLevel("JAVA_21"); // TODO: Grab from project data
        config.setMixins(new ArrayList<>());
        config.setClient(new ArrayList<>());
        if(!ctx.data().getAsBoolean(ForgeProjectKeys.CLIENT_SIDE_ONLY, false))
            config.setServer(new ArrayList<>());

        config.setInjectors(Map.of("defaultRequire", 1));
        var json = Railroad.GSON.toJson(config);
        files.writeString(mixinsPath, json);
    }
}
