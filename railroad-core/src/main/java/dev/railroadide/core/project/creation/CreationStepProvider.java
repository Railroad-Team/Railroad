package dev.railroadide.core.project.creation;

import dev.railroadide.core.project.ProjectType;

public interface CreationStepProvider {
    void provideSteps(ProjectServiceRegistry services, CreationStepRegistry registry);

    boolean supports(ProjectType type);
}
