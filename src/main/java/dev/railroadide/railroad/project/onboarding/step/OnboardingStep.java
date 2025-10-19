package dev.railroadide.railroad.project.onboarding.step;

import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import dev.railroadide.railroad.project.onboarding.ui.OnboardingSection;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface OnboardingStep {
    String id();
    String title();
    String description();

    OnboardingSection section();

    ReadOnlyBooleanProperty validProperty();

    default void onEnter(OnboardingContext ctx) {}
    default void onEnterAfterUI(OnboardingContext ctx) {}
    default void onExit(OnboardingContext ctx) {}
    default void dispose(OnboardingContext ctx) {}

    default CompletableFuture<Void> beforeNext(OnboardingContext ctx) {
        return CompletableFuture.completedFuture(null);
    }
}
