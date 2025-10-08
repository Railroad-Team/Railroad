package dev.railroadide.railroad.project.onboarding;

import java.util.function.Predicate;

public class OnboardingTransition {
    private final String fromStepId;
    private String toStepId;
    private final Predicate<OnboardingContext> condition;

    public OnboardingTransition(String fromStepId, String toStepId, Predicate<OnboardingContext> condition) {
        this.fromStepId = fromStepId;
        this.toStepId = toStepId;
        this.condition = condition;
    }

    public String getFromStepId() {
        return fromStepId;
    }

    public String getToStepId() {
        return toStepId;
    }

    public void setToStepId(String toStepId) {
        this.toStepId = toStepId;
    }

    public Predicate<OnboardingContext> getCondition() {
        return condition;
    }

    public boolean isConditional() {
        return condition != null;
    }
}
