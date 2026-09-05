package dev.railroadide.railroad.project.onboarding.flow;

import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Snapshot of step factories and ordered transitions used to navigate an onboarding process.
 */
public class OnboardingFlow {
    private final Map<String, Supplier<OnboardingStep>> stepLookup;
    @Getter
    private final List<OnboardingTransition> transitions;
    @Getter
    private final String firstStepId;

    /**
     * Copies the step registry and transition list into a flow.
     *
     * @param stepLookup step identifiers mapped to their factories; copied by this constructor
     * @param transitions ordered navigation edges; the list is copied but the transitions are shared
     * @param firstStepId identifier of the starting step
     */
    public OnboardingFlow(
        Map<String, Supplier<OnboardingStep>> stepLookup,
        List<OnboardingTransition> transitions,
        String firstStepId
    ) {
        this.stepLookup = Map.copyOf(stepLookup);
        this.transitions = List.copyOf(transitions);
        this.firstStepId = firstStepId;
    }

    /**
     * Creates a builder for an onboarding flow.
     *
     * @return an empty flow builder
     */
    public static OnboardingFlowBuilder builder() {
        return new OnboardingFlowBuilder();
    }

    /**
     * Looks up the factory registered for a step identifier.
     *
     * @param id identifier of the step
     * @return the factory, or {@code null} if the identifier is unknown
     */
    public Supplier<OnboardingStep> lookup(String id) {
        return stepLookup.get(id);
    }

    /**
     * Counts all registered steps, including those on alternative branches.
     *
     * @return the number of step factories
     */
    public int getTotalSteps() {
        return stepLookup.size();
    }
}
