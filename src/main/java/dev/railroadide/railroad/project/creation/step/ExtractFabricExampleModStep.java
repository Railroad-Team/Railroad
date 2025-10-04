package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.service.ZipService;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;

import java.nio.file.Path;

public record ExtractFabricExampleModStep(FilesService files, ZipService zip) implements CreationStep {
    @Override
    public String id() {
        return "railroad:extract_fabric_example_mod";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.extracting_example_mod";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        Path projectDir = ctx.projectDir();
        Path archive = projectDir.resolve("example-mod.zip");
        if (!files.exists(archive))
            throw new IllegalStateException("Example mod archive not found: " + archive);

        reporter.info("Extracting example mod archive...");
        zip.unzip(archive, projectDir);

        reporter.info("Deleting example mod archive...");
        files.delete(archive);
    }
}
