package dev.railroadide.railroad.project.onboarding.ui;

import dev.railroadide.railroad.form.Form;
import javafx.scene.Node;

/**
 * Adapts a form into the content of an onboarding step.
 *
 * @param form form displayed by the onboarding section
 */
public record FormOnboardingSection(Form form) implements OnboardingSection {
    @Override
    public Node createUI() {
        return form.createUI();
    }
}
