package dev.railroadide.railroad.project.onboarding.creation.step;

import dev.railroadide.railroad.project.ProjectContext;
import dev.railroadide.railroad.project.creation.CreationStep;
import dev.railroadide.railroad.project.creation.ProgressReporter;
import dev.railroadide.railroad.project.creation.service.GradleService;

/**
 * Runs the new project's {@code genSources} Gradle task to prepare Minecraft sources.
 *
 * @param gradle service used to execute the task in the project directory
 */
public record RunGenSourcesStep(GradleService gradle) implements CreationStep {
    @Override
    public String id() {
        return "railroad:run_gen_sources";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.running_gen_sources";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        reporter.info("Running gradlew genSources...");
        gradle.runTasks(ctx.projectDir(), "genSources");
    }
}
