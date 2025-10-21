package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.railroad.project.data.ForgeProjectKeys;

import java.nio.file.Path;

public record CreateAccessTransformerStep(FilesService files) implements CreationStep {
    @Override
    public String id() {
        return "railroad:create_access_transformer";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.create_access_transformer";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        if(!ctx.data().getAsBoolean(ForgeProjectKeys.USE_ACCESS_TRANSFORMER, false)) {
            reporter.info("Skipping access transformer creation as access transformers are not used.");
            Thread.sleep(1000); // sleeping to allow the user to read the message
            return;
        }

        reporter.info("Creating access transformer...");

        Path atPath = ctx.projectDir().resolve("src/main/resources/META-INF/accesstransformer.cfg");
        files.createFile(atPath);
        files.writeString(atPath, "# Add your access transformer entries here\n");
    }
}
