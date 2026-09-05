package dev.railroadide.railroad.project.creation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Maintains the ordered steps used to assemble a project creation pipeline.
 */
public final class CreationStepRegistry {
    private final List<CreationStep> steps = new ArrayList<>();

    /**
     * Appends a step to the execution order.
     *
     * @param step the step to append
     */
    public void add(CreationStep step) {
        steps.add(step);
    }

    /**
     * Appends steps in the collection's iteration order.
     *
     * @param newSteps the steps to append
     */
    public void addAll(Collection<? extends CreationStep> newSteps) {
        steps.addAll(newSteps);
    }

    /**
     * Appends steps in the supplied order.
     *
     * @param newSteps the steps to append
     */
    public void addAll(CreationStep... newSteps) {
        steps.addAll(Arrays.asList(newSteps));
    }

    /**
     * Inserts a step immediately before the first step with the given identifier.
     *
     * @param stepId the identifier of the existing step
     * @param newStep the step to insert
     * @throws IllegalArgumentException if no step has the given identifier
     */
    public void addBefore(String stepId, CreationStep newStep) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).id().equals(stepId)) {
                steps.add(index, newStep);
                return;
            }
        }

        throw new IllegalArgumentException("Step with id " + stepId + " not found");
    }

    /**
     * Inserts a step immediately after the first step with the given identifier.
     *
     * @param stepId the identifier of the existing step
     * @param newStep the step to insert
     * @throws IllegalArgumentException if no step has the given identifier
     */
    public void addAfter(String stepId, CreationStep newStep) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).id().equals(stepId)) {
                steps.add(index + 1, newStep);
                return;
            }
        }

        throw new IllegalArgumentException("Step with id " + stepId + " not found");
    }

    /**
     * Removes every step with the given identifier.
     *
     * @param stepId the identifier to remove
     * @return {@code true} if at least one step was removed
     */
    public boolean remove(String stepId) {
        return steps.removeIf(step -> step.id().equals(stepId));
    }

    /**
     * Tests whether the registry contains a step with the given identifier.
     *
     * @param stepId the identifier to find
     * @return {@code true} if a matching step exists
     */
    public boolean contains(String stepId) {
        return steps.stream().anyMatch(step -> step.id().equals(stepId));
    }

    /**
     * Replaces the first matching step without changing its position.
     *
     * @param stepId the identifier of the step to replace
     * @param newStep the replacement step
     * @return {@code true} if a matching step was replaced
     */
    public boolean replace(String stepId, CreationStep newStep) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).id().equals(stepId)) {
                steps.set(index, newStep);
                return true;
            }
        }

        return false;
    }

    /**
     * Returns an immutable snapshot of the current execution order.
     *
     * @return the registered steps in execution order
     */
    public List<CreationStep> getSteps() {
        return List.copyOf(steps);
    }
}
