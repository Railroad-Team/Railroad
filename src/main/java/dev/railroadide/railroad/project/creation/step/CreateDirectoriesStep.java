package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.FilesService;

import java.nio.file.Path;

public record CreateDirectoriesStep(FilesService files) implements CreationStep {
    @Override
    public String id() {
        return "railroad:create_directories";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.creating_directory";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Creating project directory...");
        Path projectDir = ctx.projectDir();
        files.createDirectories(projectDir);
    }
}
