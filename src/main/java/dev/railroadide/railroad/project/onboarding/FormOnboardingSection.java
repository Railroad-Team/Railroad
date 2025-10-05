package dev.railroadide.railroad.project.onboarding;

import dev.railroadide.core.form.Form;
import javafx.scene.Node;

public record FormOnboardingSection(Form form) implements OnboardingSection {
    @Override
    public Node createUI() {
        return form.createUI();
    }
}
