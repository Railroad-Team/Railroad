package dev.railroadide.railroad.project.onboarding.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ui.RRBorderPane;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

import java.util.function.Consumer;

public class OnboardingProjectCreationPane extends RRVBox {
    private final Consumer<Scene> onStartOnboarding;

    public OnboardingProjectCreationPane(Consumer<Scene> onStartOnboarding) {
        super();

        this.onStartOnboarding = onStartOnboarding;

        var startOnboardingButton = new RRButton("railroad.project.creation.onboarding.start_button");
        startOnboardingButton.setButtonSize(ButtonSize.LARGE);
        startOnboardingButton.setVariant(ButtonVariant.PRIMARY);
        startOnboardingButton.getStyleClass().add("start-onboarding-button");
        startOnboardingButton.setOnAction(event -> startOnboarding());

        getChildren().add(startOnboardingButton);
        VBox.setVgrow(startOnboardingButton, Priority.ALWAYS);
        setAlignment(Pos.CENTER);
        getStyleClass().addAll("project-details-pane", "onboarding-project-creation-pane");
    }

    private void startOnboarding() {
        Screen screen = Screen.getPrimary();

        double screenW = screen.getBounds().getWidth();
        double screenH = screen.getBounds().getHeight();

        double windowW = screenW * 0.75;
        double windowH = screenH * 0.75;

        this.onStartOnboarding.accept(WindowBuilder.create()
            .minSize(windowW + 10, windowH + 10)
            .owner(Railroad.WINDOW_MANAGER.getPrimaryStage())
            .title("railroad.project.creation.onboarding.title", true)
            .scene(new Scene(new RRBorderPane(), windowW, windowH))
            .build().getScene());
    }
}
