package dev.railroadide.railroad.project.creation;

import dev.railroadide.railroad.project.ProjectType;

public interface CreationStepProvider {
    void provideSteps(ProjectServiceRegistry services, CreationStepRegistry registry);

    boolean supports(ProjectType type);
}
