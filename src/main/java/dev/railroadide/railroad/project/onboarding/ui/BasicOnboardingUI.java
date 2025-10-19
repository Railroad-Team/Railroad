package dev.railroadide.railroad.project.onboarding.ui;

import dev.railroadide.core.ui.*;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;

import java.util.Optional;

@Getter
public class BasicOnboardingUI extends RRBorderPane implements OnboardingUI {
    private final RRButton backButton, nextButton, finishButton;
    private final LocalizedLabel titleLabel, descriptionLabel;
    private final Label progressLabel;
    private final ProgressBar progressBar;
    private final VBox contentContainer;
    private final ScrollPane scrollPane;
    private final StackPane busyOverlay;
    private final BorderPane buttonBar;
    private Node content;

    public BasicOnboardingUI(Node content) {
        getStyleClass().add("onboarding-root");
        setPadding(new Insets(32, 40, 24, 40));

        var mainContainer = new RRVBox(24);
        mainContainer.getStyleClass().add("onboarding-main");
        mainContainer.setFillWidth(true);
        setCenter(mainContainer);

        // Header
        this.titleLabel = new LocalizedLabel("");
        this.titleLabel.getStyleClass().add("onboarding-title");
        this.titleLabel.setWrapText(true);

        this.descriptionLabel = new LocalizedLabel("");
        this.descriptionLabel.getStyleClass().add("onboarding-description");
        this.descriptionLabel.setWrapText(true);

        this.progressBar = new ProgressBar(0);
        this.progressBar.getStyleClass().add("onboarding-progress-bar");
        this.progressBar.setMaxWidth(Double.MAX_VALUE);

        this.progressLabel = new Label();
        this.progressLabel.getStyleClass().add("onboarding-progress-label");

        var header = new RRVBox(12);
        header.getChildren().addAll(titleLabel, descriptionLabel, createProgressContainer());
        header.getStyleClass().add("onboarding-header");

        // Content
        this.contentContainer = new RRVBox(16);
        this.contentContainer.getStyleClass().add("onboarding-content-container");
        this.contentContainer.setFillWidth(true);

        this.scrollPane = new ScrollPane(contentContainer);
        this.scrollPane.setFitToWidth(true);
        this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.scrollPane.getStyleClass().add("onboarding-scroll-pane");

        this.busyOverlay = createBusyOverlay();
        var contentFrame = new RRStackPane();
        contentFrame.getChildren().addAll(scrollPane, busyOverlay);
        contentFrame.getStyleClass().add("onboarding-content-frame");

        VBox.setVgrow(contentFrame, Priority.ALWAYS);
        mainContainer.getChildren().addAll(header, contentFrame);

        // Buttons
        this.backButton = createButton("railroad.generic.back", RRButton.ButtonVariant.SECONDARY, "onboarding-back-button");
        this.nextButton = createButton("railroad.generic.next", RRButton.ButtonVariant.PRIMARY, "onboarding-next-button");
        this.finishButton = createButton("railroad.generic.finish", RRButton.ButtonVariant.PRIMARY, "onboarding-finish-button");

        this.buttonBar = new RRBorderPane();
        this.buttonBar.getStyleClass().add("onboarding-button-bar");
        this.buttonBar.setLeft(backButton);
        this.buttonBar.setRight(nextButton);

        setBottom(buttonBar);
        setContent(content);
    }

    private Node createProgressContainer() {
        var progressContainer = new RRHBox(12);
        progressContainer.getChildren().addAll(progressBar, progressLabel);
        progressContainer.setAlignment(Pos.CENTER_LEFT);
        progressContainer.getStyleClass().add("onboarding-progress");
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        return progressContainer;
    }

    private StackPane createBusyOverlay() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(48, 48);
        StackPane overlay = new StackPane(indicator);
        overlay.getStyleClass().add("onboarding-busy-overlay");
        overlay.setVisible(false);
        overlay.setManaged(false);
        return overlay;
    }

    private RRButton createButton(String key, RRButton.ButtonVariant variant, String... styleClasses) {
        var button = new RRButton(key);
        button.setVariant(variant);
        button.getStyleClass().addAll("onboarding-button");
        button.getStyleClass().addAll(styleClasses);
        button.managedProperty().bind(button.visibleProperty());
        return button;
    }

    @Override
    public void swapRightButton(Button newButton) {
        this.buttonBar.setRight(newButton);
    }

    @Override
    public Node getContent() {
        return content;
    }

    @Override
    public void setContent(Node content) {
        if (this.content != null) {
            this.content.getStyleClass().remove("onboarding-step-content");
        }

        this.content = content;
        Optional.ofNullable(content).ifPresentOrElse(node -> {
            node.getStyleClass().add("onboarding-step-content");
            contentContainer.getChildren().setAll(node);
        }, () -> contentContainer.getChildren().clear());
    }

    @Override
    public void onStepChanged(OnboardingStep step, int currentIndex, int totalSteps) {
        if (step == null) {
            titleLabel.setKey("");
            descriptionLabel.setKey("");
            progressLabel.setText("");
            progressBar.setProgress(0);
            return;
        }

        titleLabel.setKey(step.title());
        descriptionLabel.setKey(step.description());

        int displayedIndex = currentIndex + 1;
        if (totalSteps > 0) {
            progressBar.setProgress((double) displayedIndex / totalSteps);
            progressLabel.setText(displayedIndex + " / " + totalSteps);
        } else {
            progressBar.setProgress(0);
            progressLabel.setText("");
        }
    }

    @Override
    public void onBusyStateChanged(boolean busy) {
        scrollPane.setDisable(busy);
        busyOverlay.setVisible(busy);
        busyOverlay.setManaged(busy);
    }
}
