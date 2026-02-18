package dev.railroadide.railroad.project.creation;

import dev.railroadide.railroad.project.ProjectContext;

public interface CreationStep {
    String id();

    String translationKey();

    void run(ProjectContext ctx, ProgressReporter reporter) throws Exception;
}
