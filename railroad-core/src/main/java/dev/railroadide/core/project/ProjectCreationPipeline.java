package dev.railroadide.core.project;

import dev.railroadide.core.project.creation.CreationStep;
import dev.railroadide.core.project.creation.ProgressReporter;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ProjectCreationPipeline {
    private final List<CreationStep> steps;

    public ProjectCreationPipeline(List<CreationStep> steps) {
        this.steps = new ArrayList<>(steps);
    }

    public void run(ProjectContext ctx, ProgressReporter reporter) throws Exception {
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            reporter.progress(i, steps.size());
            reporter.info("→ " + step.translationKey());
            step.run(ctx, reporter);
        }

        reporter.progress(steps.size(), steps.size());
    }
}
