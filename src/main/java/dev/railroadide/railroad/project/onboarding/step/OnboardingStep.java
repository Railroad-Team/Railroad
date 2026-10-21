package dev.railroadide.railroad.project.onboarding.step;

import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import dev.railroadide.railroad.project.onboarding.ui.OnboardingSection;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.util.concurrent.CompletableFuture;

/**
 * A navigable onboarding step with validation and lifecycle hooks.
 * Default lifecycle hooks do nothing, and the default advance hook completes immediately.
 */
public interface OnboardingStep {
    /**
     * Identifies this step within its flow.
     *
     * @return the step identifier
     */
    String id();

    /**
     * Supplies the heading shown by localized onboarding views.
     *
     * @return the title localization key
     */
    String title();

    /**
     * Supplies the explanatory text shown by localized onboarding views.
     *
     * @return the description localization key
     */
    String description();

    /**
     * Supplies the content section for this step.
     *
     * @return the section whose UI is displayed
     */
    OnboardingSection section();

    /**
     * Exposes whether the step currently permits advancing or finishing.
     *
     * @return the observable validity state
     */
    ReadOnlyBooleanProperty validProperty();

    /**
     * Prepares the step before its UI is displayed.
     * The process invokes this hook asynchronously.
     *
     * @param ctx shared context for this onboarding session
     */
    default void onEnter(OnboardingContext ctx) {
    }

    /**
     * Updates the step after its UI has been installed.
     * The process invokes this hook on the JavaFX application thread.
     *
     * @param ctx shared context for this onboarding session
     */
    default void onEnterAfterUI(OnboardingContext ctx) {
    }

    /**
     * Handles leaving the current step, including backward navigation.
     *
     * @param ctx shared context for this onboarding session
     */
    default void onExit(OnboardingContext ctx) {
    }

    /**
     * Releases resources when the process discards this step or finishes.
     *
     * @param ctx shared context for this onboarding session
     */
    default void dispose(OnboardingContext ctx) {
    }

    /**
     * Performs any work required before advancing or finishing.
     * An exceptional completion prevents navigation; backward navigation does not call this hook.
     *
     * @param ctx shared context for this onboarding session
     * @return a future completing when the step may be left; completed immediately by default
     */
    default CompletableFuture<Void> beforeNext(OnboardingContext ctx) {
        return CompletableFuture.completedFuture(null);
    }
}
