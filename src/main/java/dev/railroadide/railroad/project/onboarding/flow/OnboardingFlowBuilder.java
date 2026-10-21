package dev.railroadide.railroad.project.onboarding.flow;

import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Collects step factories and ordered transitions for an onboarding flow.
 */
public class OnboardingFlowBuilder {
    private final Map<String, Supplier<OnboardingStep>> stepLookup = new HashMap<>();
    private final List<OnboardingTransition> transitions = new ArrayList<>();
    private String firstStepId;

    /**
     * Registers or replaces a step factory.
     * The first registered identifier becomes the initial step unless one has already been selected.
     *
     * @param id identifier of the step
     * @param step factory used to instantiate the step when first visited
     * @return this builder
     */
    public OnboardingFlowBuilder addStep(String id, Supplier<OnboardingStep> step) {
        stepLookup.put(id, step);
        if (firstStepId == null) {
            firstStepId = id;
        }
        return this;
    }

    /**
     * Selects the initial step identifier without checking whether it has been registered.
     *
     * @param id identifier of the step
     * @return this builder
     */
    public OnboardingFlowBuilder firstStep(String id) {
        this.firstStepId = id;
        return this;
    }

    /**
     * Appends an unconditional navigation edge.
     *
     * @param from identifier of the source step
     * @param to identifier of the destination step
     * @return this builder
     */
    public OnboardingFlowBuilder addTransition(String from, String to) {
        transitions.add(new OnboardingTransition(from, to, null));
        return this;
    }

    /**
     * Appends a navigation edge guarded by a context predicate.
     *
     * @param from identifier of the source step
     * @param to identifier of the destination step
     * @param condition predicate that enables the transition, or {@code null} for an unconditional transition
     * @return this builder
     */
    public OnboardingFlowBuilder addConditionalTransition(
        String from,
        String to,
        Predicate<OnboardingContext> condition
    ) {
        transitions.add(new OnboardingTransition(from, to, condition));
        return this;
    }

    /**
     * Collects the transitions entering a step in insertion order.
     *
     * @param stepId identifier of the step whose transitions are requested
     * @return a new list of matching transitions
     */
    public List<OnboardingTransition> getTransitionsTo(String stepId) {
        return transitions.stream()
            .filter(t -> t.getToStepId().equals(stepId))
            .collect(Collectors.toList());
    }

    /**
     * Collects the transitions leaving a step in insertion order.
     *
     * @param stepId identifier of the step whose transitions are requested
     * @return a new list of matching transitions
     */
    public List<OnboardingTransition> getTransitionsFrom(String stepId) {
        return transitions.stream()
            .filter(t -> t.getFromStepId().equals(stepId))
            .collect(Collectors.toList());
    }

    /**
     * Removes the first occurrence of the supplied transition, if present.
     *
     * @param transition transition instance to remove
     */
    public void removeTransition(OnboardingTransition transition) {
        transitions.remove(transition);
    }

    /**
     * Creates a snapshot of the registered factories and transition list without validating the graph.
     *
     * @return the configured flow
     */
    public OnboardingFlow build() {
        return new OnboardingFlow(stepLookup, transitions, firstStepId);
    }
}
