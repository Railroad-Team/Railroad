package dev.railroadide.railroad.project.onboarding.flow;

import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

@Getter
public class OnboardingTransition {
    private final String fromStepId;
    @Setter
    private String toStepId;
    private final Predicate<OnboardingContext> condition;

    public OnboardingTransition(String fromStepId, String toStepId, Predicate<OnboardingContext> condition) {
        this.fromStepId = fromStepId;
        this.toStepId = toStepId;
        this.condition = condition;
    }

    public boolean isConditional() {
        return condition != null;
    }
}
