package dev.railroadide.railroad.project.onboarding.ui;

import javafx.scene.Node;

/**
 * Supplies the JavaFX content displayed for an onboarding step.
 */
public interface OnboardingSection {
    /**
     * Creates the JavaFX content for this section.
     *
     * @return the node to display for the step
     */
    Node createUI();
}
