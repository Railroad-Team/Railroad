package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.GradleService;

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
