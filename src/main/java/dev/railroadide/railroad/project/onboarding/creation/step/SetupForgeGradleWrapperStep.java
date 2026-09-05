package dev.railroadide.railroad.project.onboarding.creation.step;

import dev.railroadide.railroad.project.ProjectContext;
import dev.railroadide.railroad.project.creation.CreationStep;
import dev.railroadide.railroad.project.creation.ProgressReporter;
import dev.railroadide.railroad.project.creation.service.GradleService;

/**
 * Runs the Forge project's {@code wrapper} Gradle task to generate or update its wrapper files.
 *
 * @param gradle service used to execute the wrapper task in the project directory
 */
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
