package dev.railroadide.core.project.creation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class CreationStepRegistry {
    private final List<CreationStep> steps = new ArrayList<>();

    public void add(CreationStep step) {
        steps.add(step);
    }

    public void addAll(Collection<? extends CreationStep> newSteps) {
        steps.addAll(newSteps);
    }

    public void addAll(CreationStep... newSteps) {
        steps.addAll(Arrays.asList(newSteps));
    }

    public void addBefore(String stepId, CreationStep newStep) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).id().equals(stepId)) {
                steps.add(index, newStep);
                return;
            }
        }

        throw new IllegalArgumentException("Step with id " + stepId + " not found");
    }

    public void addAfter(String stepId, CreationStep newStep) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).id().equals(stepId)) {
                steps.add(index + 1, newStep);
                return;
            }
        }

        throw new IllegalArgumentException("Step with id " + stepId + " not found");
    }

    public boolean remove(String stepId) {
        return steps.removeIf(step -> step.id().equals(stepId));
    }

    public boolean contains(String stepId) {
        return steps.stream().anyMatch(step -> step.id().equals(stepId));
    }

    public boolean replace(String stepId, CreationStep newStep) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).id().equals(stepId)) {
                steps.set(index, newStep);
                return true;
            }
        }

        return false;
    }

    public List<CreationStep> getSteps() {
        return List.copyOf(steps);
    }
}
