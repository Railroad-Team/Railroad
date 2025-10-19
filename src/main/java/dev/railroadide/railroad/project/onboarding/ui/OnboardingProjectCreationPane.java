package dev.railroadide.railroad.project.onboarding.ui;

import dev.railroadide.core.ui.RRBorderPane;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.utility.MacUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

public class OnboardingProjectCreationPane extends RRVBox {
    private final Consumer<Scene> onStartOnboarding;

    public OnboardingProjectCreationPane(Consumer<Scene> onStartOnboarding) {
        super(16);

        this.onStartOnboarding = onStartOnboarding;

        var startOnboardingButton = new RRButton("railroad.project.creation.onboarding.start_button");
        startOnboardingButton.setButtonSize(RRButton.ButtonSize.LARGE);
        startOnboardingButton.setVariant(RRButton.ButtonVariant.PRIMARY);
        startOnboardingButton.getStyleClass().add("start-onboarding-button");
        startOnboardingButton.setOnAction(event -> {
            Window parentWindow = getScene().getWindow();
            startOnboarding(parentWindow);
        });

        getChildren().add(startOnboardingButton);
        VBox.setVgrow(startOnboardingButton, Priority.ALWAYS);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(24));
        getStyleClass().add("project-details-pane");
    }

    private void startOnboarding(Window parentWindow) {
        var onboardingStage = new Stage();

        Screen screen = Screen.getPrimary();

        double screenW = screen.getBounds().getWidth();
        double screenH = screen.getBounds().getHeight();

        double windowW = screenW * 0.75;
        double windowH = screenH * 0.75;
        var onboardingScene = new Scene(new RRBorderPane(), windowW, windowH);
        ThemeManager.apply(onboardingScene);

        MacUtils.initialize();
        onboardingStage.initOwner(parentWindow);
        onboardingStage.setTitle(L18n.localize("railroad.project.creation.onboarding.title"));
        onboardingStage.getIcons().add(new Image(AppResources.getResourceAsStream("images/logo.png")));
        onboardingStage.setMinWidth(onboardingScene.getWidth() + 10);
        onboardingStage.setMinHeight(onboardingScene.getHeight() + 10);
        onboardingStage.setScene(onboardingScene);
        onboardingStage.show();
        MacUtils.show(onboardingStage);

        this.onStartOnboarding.accept(onboardingScene);
    }
}
