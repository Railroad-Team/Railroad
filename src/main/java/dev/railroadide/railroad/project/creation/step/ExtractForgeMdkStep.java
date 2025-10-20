package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.service.ZipService;

import java.nio.file.Path;

public record ExtractForgeMdkStep(FilesService files, ZipService zip) implements CreationStep {
    @Override
    public String id() {
        return "railroad:extract_forge_mdk";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.extracting_forge_mdk";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        Path projectDir = ctx.projectDir();
        Path archive = projectDir.resolve("forge-mdk.zip");
        if (!files.exists(archive))
            throw new IllegalStateException("Forge MDK archive not found: " + archive);

        reporter.info("Extracting Forge MDK archive...");
        zip.unzip(archive, projectDir);

        reporter.info("Deleting Forge MDK archive...");
        files.delete(archive);
    }
}
