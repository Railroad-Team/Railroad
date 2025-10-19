package dev.railroadide.railroad.project.onboarding.ui;

import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import javafx.scene.Node;
import javafx.scene.control.Button;

public interface OnboardingUI {
    Button getBackButton();
    Button getNextButton();
    Button getFinishButton();

    void swapRightButton(Button newButton);

    Node getContent();
    void setContent(Node content);

    default void onStepChanged(OnboardingStep step, int currentIndex, int totalSteps) {}

    default void onBusyStateChanged(boolean busy) {}
}
