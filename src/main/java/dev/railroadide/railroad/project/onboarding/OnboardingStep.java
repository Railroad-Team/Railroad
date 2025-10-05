package dev.railroadide.railroad.project.onboarding;

import javafx.beans.property.ReadOnlyBooleanProperty;

import java.util.concurrent.CompletableFuture;

public interface OnboardingStep {
    String id();
    String title();
    String description();

    OnboardingSection section();

    ReadOnlyBooleanProperty validProperty();

    default void onEnter(OnboardingContext ctx) {}
    default void onExit(OnboardingContext ctx) {}
    default void dispose(OnboardingContext ctx) {}

    default CompletableFuture<Void> beforeNext(OnboardingContext ctx) {
        return CompletableFuture.completedFuture(null);
    }
}
