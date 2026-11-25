package dev.railroadide.railroad.gradle.service.impl;

import dev.railroadide.railroad.ide.diagnostics.BuildProblems;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.problems.*;
import org.gradle.tooling.model.UnsupportedMethodException;

/**
 * Reads compiler-provided coordinates from Gradle versions supporting problem events.
 */
public final class GradleBuildProblems {
    private GradleBuildProblems() {
    }

    /**
     * Forwards the error-severity problems Gradle reports during a build, with their source locations.
     *
     * @param launcher build launcher to observe
     * @param report report that receives each problem's file, line, column and message
     */
    public static void listen(BuildLauncher launcher, BuildProblems.Report report) {
        launcher.addProgressListener((ProgressListener) event -> {
            if (!(event instanceof SingleProblemEvent single))
                return;

            Problem problem = single.getProblem();
            ProblemDefinition definition = problem.getDefinition();
            if (definition.getSeverity().getSeverity() != Severity.ERROR.getSeverity())
                return;

            String message = problem.getContextualLabel() == null
                ? null
                : problem.getContextualLabel().getContextualLabel();
            if (message == null || message.isBlank()) {
                message = definition.getId().getDisplayName();
            }

            try {
                for (Location origin : problem.getOriginLocations()) {
                    if (origin instanceof LineInFileLocation location) {
                        report.location(location.getPath(), location.getLine(), location.getColumn(), message);
                    }
                }
            } catch (UnsupportedMethodException _) {
                // Gradle 8.12 emits problem events but does not expose their origin locations.
            }
        }, OperationType.PROBLEMS);
    }
}
