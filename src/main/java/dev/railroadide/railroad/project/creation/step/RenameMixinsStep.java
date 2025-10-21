package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.mixin.MixinConfig;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.data.FabricProjectKeys;
import dev.railroadide.railroad.project.data.MavenProjectKeys;
import dev.railroadide.railroad.project.data.MinecraftProjectKeys;

import java.nio.file.Path;

public record RenameMixinsStep(FilesService files) implements CreationStep {
    @Override
    public String id() {
        return "railroad:rename_mixins";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.rename-mixins";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Renaming mixin configuration files...");

        String modid = ctx.data().getAsString(MinecraftProjectKeys.MOD_ID);
        String groupId = ctx.data().getAsString(MavenProjectKeys.GROUP_ID);

        Path resourcesDir = ctx.projectDir().resolve("src/main/resources");
        Path newPath = resourcesDir.resolve(modid + ".mixins.json");

        files.move(resourcesDir.resolve("modid.mixins.json"), newPath);

        reporter.info("Updating mixin configuration...");
        MixinConfig config = Railroad.GSON.fromJson(files.readString(newPath), MixinConfig.class);
        config.setPackageName(groupId + "." + modid + ".mixins");
        files.writeString(newPath, Railroad.GSON.toJson(config));

        if (ctx.data().getAsBoolean(FabricProjectKeys.SPLIT_SOURCES)) {
            reporter.info("Renaming client mixin configuration files...");
            Path clientResourcesDir = ctx.projectDir().resolve("src/client/resources");
            Path newClientPath = clientResourcesDir.resolve(modid + ".client.mixins.json");

            files.move(clientResourcesDir.resolve("modid.client.mixins.json"), newClientPath);

            reporter.info("Updating client mixin configuration...");
            MixinConfig clientConfig = Railroad.GSON.fromJson(files.readString(newClientPath), MixinConfig.class);
            clientConfig.setPackageName(groupId + "." + modid + ".mixins");
            files.writeString(newClientPath, Railroad.GSON.toJson(clientConfig));
        }
    }
}
