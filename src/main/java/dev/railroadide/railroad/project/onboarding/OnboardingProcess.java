package dev.railroadide.railroad.project.onboarding;

import dev.railroadide.core.ui.*;
import dev.railroadide.core.ui.RRButton.ButtonVariant;
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
import java.util.Optional;
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
            this.backButton = createButton("railroad.generic.back", ButtonVariant.SECONDARY, "onboarding-back-button");
            this.nextButton = createButton("railroad.generic.next", ButtonVariant.PRIMARY, "onboarding-next-button");
            this.finishButton = createButton("railroad.generic.finish", ButtonVariant.PRIMARY, "onboarding-finish-button");

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

        private RRButton createButton(String key, ButtonVariant variant, String... styleClasses) {
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
                progressLabel.setText(L18n.localize("railroad.onboarding.step_progress", displayedIndex, totalSteps));
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
