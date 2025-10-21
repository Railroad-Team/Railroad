package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.FilesService;
import dev.railroadide.core.project.creation.service.ZipService;
import dev.railroadide.railroad.project.creation.ProjectContextKeys;

import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

        reporter.info("Moving example mod files...");
        Path extractedDir = projectDir.resolve("fabric-example-mod-" + ctx.get(ProjectContextKeys.EXAMPLE_MOD_BRANCH));
        if (!files.exists(extractedDir))
            throw new IllegalStateException("Extracted example mod directory not found: " + extractedDir);

        files.extractDirectoryContents(extractedDir, projectDir, StandardCopyOption.REPLACE_EXISTING);

        reporter.info("Deleting example mod archive...");
        files.delete(archive);

        reporter.info("Deleting extracted example mod directory...");
        files.deleteDirectory(extractedDir);
    }
}
