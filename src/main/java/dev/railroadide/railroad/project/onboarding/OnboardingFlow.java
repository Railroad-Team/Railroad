package dev.railroadide.railroad.project.onboarding;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class OnboardingFlow {
    private final Map<String, Supplier<OnboardingStep>> stepLookup;
    private final List<String> stepOrder;

    public OnboardingFlow(Map<String, Supplier<OnboardingStep>> stepLookup, List<String> stepOrder) {
        this.stepLookup = Map.copyOf(stepLookup);
        this.stepOrder = List.copyOf(stepOrder);
    }

    public Supplier<OnboardingStep> lookup(String id) {
        return stepLookup.get(id);
    }

    public List<String> stepOrder() {
        return stepOrder;
    }
}
