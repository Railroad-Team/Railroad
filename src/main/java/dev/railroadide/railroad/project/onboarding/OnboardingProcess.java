package dev.railroadide.railroad.project.onboarding;

import dev.railroadide.core.ui.RRBorderPane;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRButton.ButtonVariant;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class OnboardingProcess<N extends Parent & OnboardingUI> {
    private final OnboardingFlow flow;
    private final OnboardingContext context;
    private final N ui;
    private final Consumer<OnboardingContext> onFinish;

    protected OnboardingProcess(OnboardingFlow flow, OnboardingContext context, N ui, Consumer<OnboardingContext> onFinish) {
        this.flow = flow;
        this.context = context;
        this.ui = ui;
        this.onFinish = onFinish;
    }

    public static <N extends Parent & OnboardingUI> OnboardingProcess<N> create(OnboardingFlow flow, OnboardingContext context, N ui, Consumer<OnboardingContext> onFinish) {
        return new OnboardingProcess<>(flow, context, ui, onFinish);
    }

    public static OnboardingProcess<BasicOnboardingUI> createBasic(OnboardingFlow flow, OnboardingContext context, Node content, Consumer<OnboardingContext> onFinish) {
        return create(flow, context, new BasicOnboardingUI(content), onFinish);
    }

    public static OnboardingProcess<BasicOnboardingUI> createBasic(OnboardingFlow flow, OnboardingContext context, Consumer<OnboardingContext> onFinish) {
        return createBasic(flow, context, new RRBorderPane(), onFinish);
    }

    public void run(Scene scene) {
        List<String> order = flow.stepOrder();
        if (order.isEmpty())
            return;

        scene.setRoot(ui);

        Runnable runnable = () -> new Navigator(ui, order).showStep(0);
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    @Getter
    public static class BasicOnboardingUI extends RRBorderPane implements OnboardingUI {
        private final BorderPane buttonBar;
        private final RRButton backButton, nextButton, finishButton;
        private final VBox mainContainer;
        private final VBox header;
        private final LocalizedLabel titleLabel;
        private final LocalizedLabel descriptionLabel;
        private final Label progressLabel;
        private final ProgressBar progressBar;
        private final ScrollPane scrollPane;
        private final VBox contentContainer;
        private final StackPane contentFrame;
        private final StackPane busyOverlay;
        private Node content;

        public BasicOnboardingUI(Node content) {
            getStyleClass().add("onboarding-root");
            setPadding(new Insets(32, 40, 24, 40));

            this.mainContainer = new RRVBox(24);
            this.mainContainer.getStyleClass().add("onboarding-main");
            this.mainContainer.setFillWidth(true);
            setCenter(mainContainer);

            this.header = new RRVBox(12);
            this.header.getStyleClass().add("onboarding-header");

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

            var progressContainer = new RRHBox(12);
            progressContainer.setAlignment(Pos.CENTER_LEFT);
            progressContainer.getStyleClass().add("onboarding-progress");
            Region spacer = new Region();
            HBox.setHgrow(this.progressBar, Priority.ALWAYS);
            HBox.setHgrow(spacer, Priority.SOMETIMES);
            progressContainer.getChildren().addAll(this.progressBar, spacer, this.progressLabel);

            this.header.getChildren().addAll(this.titleLabel, this.descriptionLabel, progressContainer);

            this.contentContainer = new RRVBox();
            this.contentContainer.setSpacing(16);
            this.contentContainer.getStyleClass().add("onboarding-content-container");
            this.contentContainer.setFillWidth(true);

            this.scrollPane = new ScrollPane(contentContainer);
            this.scrollPane.setFitToWidth(true);
            this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            this.scrollPane.getStyleClass().add("onboarding-scroll-pane");

            this.busyOverlay = new StackPane();
            this.busyOverlay.getStyleClass().add("onboarding-busy-overlay");
            this.busyOverlay.setVisible(false);
            this.busyOverlay.setManaged(false);
            ProgressIndicator busyIndicator = new ProgressIndicator();
            busyIndicator.setMaxSize(48, 48);
            this.busyOverlay.getChildren().add(busyIndicator);

            this.contentFrame = new StackPane(this.scrollPane, this.busyOverlay);
            this.contentFrame.getStyleClass().add("onboarding-content-frame");

            VBox.setVgrow(this.contentFrame, Priority.ALWAYS);
            this.mainContainer.getChildren().addAll(this.header, this.contentFrame);

            this.backButton = new RRButton("railroad.generic.back");
            this.backButton.getStyleClass().addAll("onboarding-button", "onboarding-back-button");
            this.backButton.setVariant(ButtonVariant.SECONDARY);
            this.backButton.managedProperty().bind(this.backButton.visibleProperty());

            this.nextButton = new RRButton("railroad.generic.next");
            this.nextButton.getStyleClass().addAll("onboarding-button", "onboarding-next-button");
            this.nextButton.managedProperty().bind(this.nextButton.visibleProperty());

            this.finishButton = new RRButton("railroad.generic.finish");
            this.finishButton.getStyleClass().addAll("onboarding-button", "onboarding-finish-button");
            this.finishButton.managedProperty().bind(this.finishButton.visibleProperty());

            this.buttonBar = new RRBorderPane();
            this.buttonBar.setPadding(new Insets(20, 0, 0, 0));
            this.buttonBar.getStyleClass().add("onboarding-button-bar");
            this.buttonBar.setLeft(this.backButton);
            this.buttonBar.setRight(this.nextButton);
            setBottom(this.buttonBar);

            setContent(content);
        }

        @Override
        public void swapRightButton(Button newButton) {
            if (newButton != this.backButton && newButton != this.nextButton && newButton != this.finishButton)
                throw new IllegalArgumentException("The new button must be one of the predefined buttons");

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

            if (content != null) {
                if (!content.getStyleClass().contains("onboarding-step-content")) {
                    content.getStyleClass().add("onboarding-step-content");
                }
                this.contentContainer.getChildren().setAll(content);
            } else {
                this.contentContainer.getChildren().clear();
            }
        }

        @Override
        public void onStepChanged(OnboardingStep step, int currentIndex, int totalSteps) {
            if (step == null) {
                this.titleLabel.setKey("");
                this.descriptionLabel.setKey("");
                this.progressLabel.setText("");
                this.progressBar.setProgress(0);
                return;
            }

            this.titleLabel.setKey(step.title());
            this.descriptionLabel.setKey(step.description());

            int displayedIndex = currentIndex + 1;
            if (totalSteps <= 0) {
                this.progressBar.setProgress(0);
                this.progressLabel.setText("");
                return;
            }

            double progressValue = (double) displayedIndex / totalSteps;
            this.progressBar.setProgress(progressValue);

            this.progressLabel.setText(L18n.localize("railroad.onboarding.step_progress", displayedIndex, totalSteps));
        }

        @Override
        public void onBusyStateChanged(boolean busy) {
            this.scrollPane.setDisable(busy);
            this.busyOverlay.setVisible(busy);
            this.busyOverlay.setManaged(busy);
        }
    }

    private final class Navigator {
        private final N ui;
        private final List<String> stepOrder;

        private final ReadOnlyIntegerWrapper currentIndex = new ReadOnlyIntegerWrapper(-1);
        private OnboardingStep currentStep;
        private final BooleanProperty busy = new SimpleBooleanProperty(false);
        private EventHandler<KeyEvent> keyHandler;

        private final Map<String, OnboardingStep> stepCache = new HashMap<>();
        private final Map<String, Node> cachedUIs = new HashMap<>();

        Navigator(N ui, List<String> stepOrder) {
            this.ui = ui;
            this.stepOrder = stepOrder;
            busy.addListener((obs, oldValue, newValue) -> this.ui.onBusyStateChanged(newValue));
            this.ui.onBusyStateChanged(busy.get());
        }

        void showStep(int targetIndex) {
            if (targetIndex < 0 || targetIndex >= stepOrder.size())
                return;

            busy.set(true);
            Platform.runLater(() -> performStepTransition(targetIndex));
        }

        private void performStepTransition(int targetIndex) {
            cleanupCurrentStep(true);

            currentIndex.set(targetIndex);
            currentStep = stepAt(targetIndex);

            CompletableFuture.runAsync(() -> currentStep.onEnter(context)).thenRun(
                () -> Platform.runLater(() -> {
                    this.ui.setContent(
                        cachedUIs.computeIfAbsent(
                            currentStep.id(),
                            $ -> currentStep.section().createUI()
                        )
                    );
                    this.ui.onStepChanged(currentStep, currentIndex.get(), stepOrder.size());
                    configureNavigation();
                    busy.set(false);
                }));
        }

        private OnboardingStep stepAt(int idx) {
            String id = flow.stepOrder().get(idx);

            return stepCache.computeIfAbsent(id,
                $ -> {
                    Supplier<OnboardingStep> sup = flow.lookup(id);
                    if (sup == null)
                        throw new IllegalArgumentException("Unknown step id: " + id);
                    return sup.get();
                });
        }

        private void handleNext() {
            if (busy.get() || currentStep == null || !currentStep.validProperty().get())
                return;

            int nextIndex = currentIndex.get() + 1;
            busy.set(true);

            currentStep.beforeNext(context).whenComplete(
                (ignored, throwable) -> Platform.runLater(() -> {
                    busy.set(false);
                    if (throwable != null) {
                        Railroad.LOGGER.error("Error during onboarding step's next operation", throwable);
                        return;
                    }

                    if (nextIndex < stepOrder.size()) {
                        showStep(nextIndex);
                        return;
                    }

                    cleanupCurrentStep(true);
                    currentStep = null;
                    currentIndex.set(nextIndex);
                }));
        }

        private void handleBack() {
            if (busy.get() || currentStep == null || currentIndex.get() == 0)
                return;

            showStep(currentIndex.get() - 1);
        }

        private void configureNavigation() {
            Button backButton = this.ui.getBackButton();
            Button nextButton = this.ui.getNextButton();
            Button finishButton = this.ui.getFinishButton();

            ReadOnlyBooleanProperty valid = currentStep.validProperty();

            backButton.setOnAction(null);
            backButton.disableProperty().unbind();
            backButton.disableProperty().bind(busy.or(currentIndex.isEqualTo(0)));
            backButton.visibleProperty().unbind();
            backButton.visibleProperty().bind(currentIndex.greaterThan(0));
            backButton.setOnAction(event -> {
                event.consume();
                handleBack();
            });

            nextButton.setOnAction(null);
            nextButton.disableProperty().unbind();
            nextButton.disableProperty().bind(busy.or(valid.not()));
            nextButton.visibleProperty().bind(currentIndex.lessThan(stepOrder.size() - 1));
            nextButton.setOnAction(event -> {
                event.consume();
                handleNext();
            });

            finishButton.setOnAction(null);
            finishButton.disableProperty().unbind();
            finishButton.disableProperty().bind(busy.or(valid.not()));
            finishButton.visibleProperty().unbind();
            finishButton.visibleProperty().bind(currentIndex.isEqualTo(stepOrder.size() - 1));
            finishButton.setOnAction(this::onFinish);

            if (finishButton.isVisible()) {
                this.ui.swapRightButton(finishButton);
            } else {
                this.ui.swapRightButton(nextButton);
            }

            if (keyHandler != null) {
                this.ui.removeEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
            }

            keyHandler = event -> {
                if (event.getCode() == KeyCode.ENTER && valid.get() && !busy.get()) {
                    event.consume();
                    handleNext();
                } else if (event.getCode() == KeyCode.ESCAPE && currentIndex.get() > 0 && !busy.get()) {
                    event.consume();
                    handleBack();
                }
            };
            this.ui.addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        }

        private void cleanupCurrentStep(boolean callOnExit) {
            if (currentStep == null)
                return;

            var nextButton = this.ui.getNextButton();
            nextButton.setOnAction(null);
            nextButton.disableProperty().unbind();

            var backButton = this.ui.getBackButton();
            backButton.setOnAction(null);
            backButton.disableProperty().unbind();

            var finishButton = this.ui.getFinishButton();
            finishButton.setOnAction(null);
            finishButton.disableProperty().unbind();

            if (keyHandler != null) {
                this.ui.removeEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
                keyHandler = null;
            }

            if (callOnExit) {
                currentStep.onExit(context);
            }
        }

        private void onFinish(ActionEvent event) {
            event.consume();
            if (busy.get() || currentStep == null || !currentStep.validProperty().get())
                return;

            busy.set(true);
            currentStep.beforeNext(context).whenComplete((ignored, throwable) -> Platform.runLater(() -> {
                busy.set(false);
                if (throwable != null) {
                    Railroad.LOGGER.error("Error during onboarding step's finish operation", throwable);
                    return;
                }

                cleanupCurrentStep(true);
                currentStep = null;
                currentIndex.set(stepOrder.size());

                for (OnboardingStep step : this.stepCache.values()) {
                    step.dispose(context);
                }

                this.stepCache.clear();
                this.cachedUIs.clear();
                this.ui.setContent(new RRBorderPane());
                this.ui.onStepChanged(null, currentIndex.get(), stepOrder.size());

                onFinish.accept(context);
            }));
        }
    }
}
