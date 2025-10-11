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

public class OnboardingFlowBuilder {
    private final Map<String, Supplier<OnboardingStep>> stepLookup = new HashMap<>();
    private final List<OnboardingTransition> transitions = new ArrayList<>();
    private String firstStepId;

    public OnboardingFlowBuilder addStep(String id, Supplier<OnboardingStep> step) {
        stepLookup.put(id, step);
        if (firstStepId == null) {
            firstStepId = id;
        }
        return this;
    }

    public OnboardingFlowBuilder firstStep(String id) {
        this.firstStepId = id;
        return this;
    }

    public OnboardingFlowBuilder addTransition(String from, String to) {
        transitions.add(new OnboardingTransition(from, to, null));
        return this;
    }

    public OnboardingFlowBuilder addConditionalTransition(String from, String to, Predicate<OnboardingContext> condition) {
        transitions.add(new OnboardingTransition(from, to, condition));
        return this;
    }

    public List<OnboardingTransition> getTransitionsTo(String stepId) {
        return transitions.stream()
            .filter(t -> t.getToStepId().equals(stepId))
            .collect(Collectors.toList());
    }

    public List<OnboardingTransition> getTransitionsFrom(String stepId) {
        return transitions.stream()
            .filter(t -> t.getFromStepId().equals(stepId))
            .collect(Collectors.toList());
    }

    public void removeTransition(OnboardingTransition transition) {
        transitions.remove(transition);
    }

    public OnboardingFlow build() {
        return new OnboardingFlow(stepLookup, transitions, firstStepId);
    }
}
