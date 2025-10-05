package dev.railroadide.railroad.project.onboarding;

import dev.railroadide.core.ui.RRBorderPane;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.railroad.Railroad;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        private final Button backButton, nextButton, finishButton;
        private Node content;

        public BasicOnboardingUI(Node content) {
            setPadding(new Insets(10));
            getStyleClass().add("onboarding-core-ui");

            setContent(content);

            this.backButton = new RRButton("railroad.generic.back");
            this.backButton.getStyleClass().addAll("onboarding-button", "onboarding-back-button");

            this.nextButton = new RRButton("railroad.generic.next");
            this.nextButton.getStyleClass().addAll("onboarding-button", "onboarding-next-button");

            this.finishButton = new RRButton("railroad.generic.finish");
            this.finishButton.getStyleClass().addAll("onboarding-button", "onboarding-finish-button");

            this.buttonBar = new RRBorderPane();
            this.buttonBar.setPadding(new Insets(5));
            this.buttonBar.getStyleClass().add("onboarding-button-bar");
            this.buttonBar.setLeft(this.backButton);
            this.buttonBar.setRight(this.nextButton);
            setBottom(this.buttonBar);
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
            this.content = content;
            this.content.getStyleClass().add("onboarding-content");
            setCenter(content);
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
        }

        void showStep(int targetIndex) {
            if (targetIndex < 0 || targetIndex >= stepOrder.size())
                return;

            cleanupCurrentStep(true);

            currentIndex.set(targetIndex);
            currentStep = stepAt(targetIndex);

            try {
                currentStep.onEnter(context);

                this.ui.setContent(
                    cachedUIs.computeIfAbsent(
                        currentStep.id(),
                        $ -> currentStep.section().createUI()
                    )
                );
                configureNavigation();
            } catch (Throwable throwable) {
                Railroad.LOGGER.error("Error during onboarding step's onEnter operation", throwable);
                cleanupCurrentStep(false);
                currentStep = null;
                currentIndex.set(-1);
            }
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
            backButton.visibleProperty().bind(busy.or(currentIndex.isEqualTo(0)));
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

                onFinish.accept(context);
            }));
        }
    }
}
