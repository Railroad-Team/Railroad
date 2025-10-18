package dev.railroadide.railroad.project.onboarding;

import dev.railroadide.core.ui.*;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.onboarding.flow.OnboardingFlow;
import dev.railroadide.railroad.project.onboarding.flow.OnboardingTransition;
import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import dev.railroadide.railroad.project.onboarding.ui.BasicOnboardingUI;
import dev.railroadide.railroad.project.onboarding.ui.OnboardingUI;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.*;
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
        String firstStepId = flow.getFirstStepId();
        if (firstStepId == null || firstStepId.isEmpty())
            return;

        scene.setRoot(ui);

        Runnable runnable = () -> new Navigator(ui).showStep(firstStepId);
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    private final class Navigator {
        private final N ui;
        private final List<String> stepHistory = new ArrayList<>();
        private boolean suppressHistoryAppend;

        private OnboardingStep currentStep;
        private final BooleanProperty busy = new SimpleBooleanProperty(false);
        private EventHandler<KeyEvent> keyHandler;

        private final Map<String, OnboardingStep> stepCache = new HashMap<>();
        private final Map<String, Node> cachedUIs = new HashMap<>();

        Navigator(N ui) {
            this.ui = ui;
            busy.addListener((obs, oldValue, newValue) -> this.ui.onBusyStateChanged(newValue));
            this.ui.onBusyStateChanged(busy.get());
        }

        void showStep(String stepId) {
            if (stepId == null || stepId.isEmpty())
                return;

            busy.set(true);
            Platform.runLater(() -> performStepTransition(stepId));
        }

        private void performStepTransition(String stepId) {
            cleanupCurrentStep(true);
            if (!suppressHistoryAppend) {
                if (stepHistory.isEmpty() || !Objects.equals(stepHistory.getLast(), stepId)) {
                    stepHistory.add(stepId);
                }
            } else {
                suppressHistoryAppend = false;
            }
            currentStep = stepAt(stepId);

            CompletableFuture.runAsync(() -> currentStep.onEnter(context)).thenRun(
                () -> Platform.runLater(() -> {
                    this.ui.setContent(
                        cachedUIs.computeIfAbsent(
                            currentStep.id(),
                            $ -> currentStep.section().createUI()
                        )
                    );
                    this.ui.onStepChanged(currentStep, stepHistory.size() - 1, -1);
                    configureNavigation();
                    busy.set(false);
                })).exceptionally(throwable -> {
                    Railroad.LOGGER.error("Error during onboarding step's enter operation", throwable);

                    return null;
                });
        }

        private OnboardingStep stepAt(String id) {
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

            busy.set(true);

            currentStep.beforeNext(context).whenComplete(
                (ignored, throwable) -> Platform.runLater(() -> {
                    busy.set(false);
                    if (throwable != null) {
                        Railroad.LOGGER.error("Error during onboarding step's next operation", throwable);
                        return;
                    }

                    List<OnboardingTransition> possibleTransitions = flow.getTransitions().stream()
                        .filter(t -> t.getFromStepId().equals(currentStep.id()))
                        .toList();

                    Optional<OnboardingTransition> transition = possibleTransitions.stream()
                        .filter(t -> t.isConditional() && t.getCondition().test(context))
                        .findFirst();

                    if (transition.isPresent()) {
                        showStep(transition.get().getToStepId());
                        return;
                    }

                    Optional<OnboardingTransition> simpleTransition = possibleTransitions.stream()
                        .filter(t -> !t.isConditional())
                        .findFirst();

                    if (simpleTransition.isPresent()) {
                        showStep(simpleTransition.get().getToStepId());
                        return;
                    }

                    cleanupCurrentStep(true);
                    currentStep = null;
                }));
        }

        private void handleBack() {
            if (busy.get() || currentStep == null || stepHistory.size() <= 1)
                return;

            String lastStepId = stepHistory.removeLast();
            OnboardingStep lastStep = stepCache.get(lastStepId);
            if (lastStep != null) {
                lastStep.dispose(context);
                stepCache.remove(lastStepId);
                cachedUIs.remove(lastStepId);
            }

            String previousStepId = stepHistory.getLast();
            suppressHistoryAppend = true;
            showStep(previousStepId);
        }

        private void configureNavigation() {
            Button backButton = this.ui.getBackButton();
            Button nextButton = this.ui.getNextButton();
            Button finishButton = this.ui.getFinishButton();

            ReadOnlyBooleanProperty valid = currentStep.validProperty();

            backButton.setOnAction(null);
            backButton.disableProperty().unbind();
            backButton.disableProperty().bind(busy.or(Bindings.createBooleanBinding(() -> stepHistory.size() <= 1)));
            backButton.visibleProperty().set(stepHistory.size() > 1);
            backButton.setOnAction(event -> {
                event.consume();
                handleBack();
            });

            boolean hasNextStep = flow.getTransitions().stream().anyMatch(t -> t.getFromStepId().equals(currentStep.id()));

            nextButton.setOnAction(null);
            nextButton.disableProperty().unbind();
            nextButton.disableProperty().bind(busy.or(valid.not()));
            nextButton.visibleProperty().set(hasNextStep);
            nextButton.setOnAction(event -> {
                event.consume();
                handleNext();
            });

            finishButton.setOnAction(null);
            finishButton.disableProperty().unbind();
            finishButton.disableProperty().bind(busy.or(valid.not()));
            finishButton.visibleProperty().set(!hasNextStep);
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
                } else if (event.getCode() == KeyCode.ESCAPE && stepHistory.size() > 1 && !busy.get()) {
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

                for (OnboardingStep step : this.stepCache.values()) {
                    step.dispose(context);
                }

                this.stepCache.clear();
                this.cachedUIs.clear();
                this.ui.setContent(new RRBorderPane());
                this.ui.onStepChanged(null, -1, -1);

                onFinish.accept(context);
            }));
        }
    }
}
