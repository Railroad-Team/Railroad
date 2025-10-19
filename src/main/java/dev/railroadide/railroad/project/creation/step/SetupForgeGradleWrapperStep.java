package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.service.GradleService;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;

public record SetupForgeGradleWrapperStep(GradleService gradle) implements CreationStep {
    @Override
    public String id() {
        return "railroad:setup_forge_gradle_wrapper";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.setup_forge_gradle_wrapper";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Setting up Gradle wrapper for Forge...");
        gradle.runTasks(ctx.projectDir(), "wrapper");
    }
}
