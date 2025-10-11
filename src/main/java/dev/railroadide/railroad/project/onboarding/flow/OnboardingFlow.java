package dev.railroadide.railroad.project.onboarding.flow;

import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class OnboardingFlow {
    private final Map<String, Supplier<OnboardingStep>> stepLookup;
    @Getter
    private final List<OnboardingTransition> transitions;
    @Getter
    private final String firstStepId;

    public OnboardingFlow(Map<String, Supplier<OnboardingStep>> stepLookup, List<OnboardingTransition> transitions, String firstStepId) {
        this.stepLookup = Map.copyOf(stepLookup);
        this.transitions = List.copyOf(transitions);
        this.firstStepId = firstStepId;
    }

    public static OnboardingFlowBuilder builder() {
        return new OnboardingFlowBuilder();
    }

    public Supplier<OnboardingStep> lookup(String id) {
        return stepLookup.get(id);
    }
}
