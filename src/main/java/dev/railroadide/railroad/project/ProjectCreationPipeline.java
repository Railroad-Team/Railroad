package dev.railroadide.railroad.project;

import dev.railroadide.railroad.project.creation.CreationStep;
import dev.railroadide.railroad.project.creation.ProgressReporter;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A pipeline for creating a project, consisting of multiple creation steps.
 */
@Getter
public class ProjectCreationPipeline {
    private final List<CreationStep> steps;

    /**
     * Constructs a new ProjectCreationPipeline with the given list of creation steps.
     *
     * @param steps the list of creation steps to be executed in the pipeline
     */
    public ProjectCreationPipeline(List<CreationStep> steps) {
        this.steps = new ArrayList<>(steps);
    }

    /**
     * Executes the project creation pipeline, running each creation step in order.
     *
     * @param ctx      the project context containing necessary information for creation
     * @param reporter the progress reporter to report progress and information during execution
     * @throws Exception if any step in the pipeline fails
     */
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
