package dev.railroadide.railroad.project.onboarding.ui;

import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import javafx.scene.Node;
import javafx.scene.control.Button;

/**
 * View contract used by an onboarding process to display steps and configure navigation.
 */
public interface OnboardingUI {
    /**
     * Supplies the button that navigates to the previous visited step.
     *
     * @return the back navigation button
     */
    Button getBackButton();

    /**
     * Supplies the button that advances to the next step.
     *
     * @return the next navigation button
     */
    Button getNextButton();

    /**
     * Supplies the button that finishes onboarding on the last step.
     *
     * @return the finish button
     */
    Button getFinishButton();

    /**
     * Replaces the button in the right navigation slot.
     *
     * @param newButton button to place in the right navigation slot
     */
    void swapRightButton(Button newButton);

    /**
     * Returns the currently displayed step content.
     *
     * @return the content node, or {@code null} if none is displayed
     */
    Node getContent();

    /**
     * Replaces the displayed step content.
     *
     * @param content content to display, or {@code null} to leave the content area empty
     */
    void setContent(Node content);

    /**
     * Notifies the view that the current step or navigation position changed.
     * The default implementation does nothing.
     *
     * @param step current step, or {@code null} when onboarding finishes
     * @param currentIndex zero-based index in the navigation history, or {@code -1} when onboarding finishes
     * @param totalSteps number of registered steps, or {@code -1} when onboarding finishes
     */
    default void onStepChanged(OnboardingStep step, int currentIndex, int totalSteps) {
    }

    /**
     * Notifies the view that asynchronous step work started or finished.
     * The default implementation does nothing.
     *
     * @param busy whether a step transition or advance operation is in progress
     */
    default void onBusyStateChanged(boolean busy) {
    }
}
