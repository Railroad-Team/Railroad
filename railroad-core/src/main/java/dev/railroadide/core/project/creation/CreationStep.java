package dev.railroadide.core.project.creation;

import dev.railroadide.core.project.ProjectContext;

public interface CreationStep {
    String id();
    String translationKey();
    void run(ProjectContext ctx, ProgressReporter reporter) throws Exception;
}
