package dev.railroadide.railroad.project.onboarding;

import javafx.scene.Node;
import javafx.scene.control.Button;

public interface OnboardingUI {
    Button getBackButton();
    Button getNextButton();
    Button getFinishButton();

    void swapRightButton(Button newButton);

    Node getContent();
    void setContent(Node content);
}
