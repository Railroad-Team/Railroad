package dev.railroadide.railroad.project.creation.step;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import dev.railroadide.core.project.creation.service.GitService;

public record InitGitStep(GitService git) implements CreationStep {
    @Override
    public String id() {
        return "railroad:init_git";
    }

    @Override
    public String translationKey() {
        return "railroad.project.creation.task.creating_git";
    }

    @Override
    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        if (ctx.data().getAsBoolean(ProjectData.DefaultKeys.INIT_GIT)) {
            reporter.info("Initializing git repository...");
            git.init(ctx.projectDir());
        } else {
            reporter.info("Skipping git init");
            Thread.sleep(1000); // sleeping to allow the user to read the message
        }
    }
}
