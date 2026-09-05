package dev.railroadide.railroad.project.onboarding.flow;

import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

/**
 * Navigation edge with an optional predicate evaluated against the onboarding context.
 */
@Getter
public class OnboardingTransition {
    private final String fromStepId;
    @Setter
    private String toStepId;
    private final Predicate<OnboardingContext> condition;

    /**
     * Creates a navigation edge and its optional guard.
     *
     * @param fromStepId identifier of the source step
     * @param toStepId identifier of the destination step
     * @param condition predicate that enables the transition, or {@code null} for an unconditional transition
     */
    public OnboardingTransition(String fromStepId, String toStepId, Predicate<OnboardingContext> condition) {
        this.fromStepId = fromStepId;
        this.toStepId = toStepId;
        this.condition = condition;
    }

    /**
     * Checks whether this edge has a predicate.
     *
     * @return {@code true} if a condition is present
     */
    public boolean isConditional() {
        return condition != null;
    }
}
