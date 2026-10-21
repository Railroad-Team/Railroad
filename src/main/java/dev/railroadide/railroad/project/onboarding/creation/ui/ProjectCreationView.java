package dev.railroadide.railroad.project.onboarding.creation.ui;

import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.ProjectData;
import dev.railroadide.railroad.ui.*;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.localized.LocalizedTitledPane;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import dev.railroadide.railroad.utility.StringUtils;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * JavaFX view displaying project creation progress, the current task, elapsed time, and expandable logs.
 * Construct and bind this view on the JavaFX application thread.
 */
public class ProjectCreationView extends RRBorderPane {
    private final LocalizedLabel titleLabel = new LocalizedLabel("railroad.project.creation.status.creating.title");
    private final StackPane progressStack = new StackPane();
    private final MFXProgressSpinner spinner = new MFXProgressSpinner();

    private final HBox chipRow = new HBox();
    private final Label taskChip = chip("…");
    private final Label timeChip = chip("00:00");

    private final LocalizedTitledPane logsPane = new LocalizedTitledPane(null, "railroad.project.creation.status.logs");
    @Getter
    private final TextArea logArea = new TextArea();

    private final RRButton cancelBtn = new RRButton("railroad.generic.cancel");

    private final ObjectProperty<Instant> startInstant = new SimpleObjectProperty<>();
    private Timeline elapsedTicker;

    /**
     * Builds the progress display and log controls for the named project.
     *
     * @param data project data supplying the name displayed in the subtitle
     */
    public ProjectCreationView(ProjectData data) {
        getStyleClass().add("project-creation-root");
        var bg = new StackPane();
        var scroll = new ScrollPane(bg);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);

        var card = new VBox();
        card.setAlignment(Pos.CENTER);
        card.setMinHeight(Region.USE_PREF_SIZE);
        card.getStyleClass().add("project-creation-card");

        var title = titleLabel;
        title.getStyleClass().add("project-creation-title");

        var subtitle = new LocalizedLabel(
            "railroad.project.creation.status.creating.subtitle",
            data.getAsString(ProjectData.DefaultKeys.NAME));

        subtitle.getStyleClass().add("project-creation-subtitle");
        subtitle.setWrapText(true);
        var header = new VBox();
        header.setAlignment(Pos.CENTER);
        header.getStyleClass().add("project-creation-header");
        header.getChildren().addAll(title, subtitle);

        spinner.setRadius(64);
        spinner.setProgress(0);
        spinner.setPrefSize(160, 160);
        progressStack.getChildren().addAll(spinner);

        taskChip.setMinWidth(0);
        taskChip.setWrapText(true);
        timeChip.setMinWidth(55);
        HBox.setHgrow(taskChip, Priority.ALWAYS);
        chipRow.setAlignment(Pos.CENTER);
        chipRow.getStyleClass().add("project-creation-chip-row");
        chipRow.getChildren().addAll(taskChip, timeChip);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("rr-log-area");
        logArea.setPrefRowCount(12);
        logArea.setMinHeight(220);

        logsPane.setKey("railroad.project.creation.status.logs");
        logsPane.setContent(logArea);
        logsPane.setExpanded(false);
        logsPane.getStyleClass().add("rr-logs-pane");

        cancelBtn.setVariant(ButtonVariant.SECONDARY);
        cancelBtn.setTooltip(new LocalizedTooltip("railroad.project.creation.cancel.tooltip"));
        var footer = new HBox();
        footer.setAlignment(Pos.CENTER);
        footer.getStyleClass().add("project-creation-footer");
        footer.getChildren().add(cancelBtn);
        setBottom(footer);

        card.getChildren().addAll(
            header,
            new Separator(),
            progressStack,
            chipRow,
            new Separator(),
            logsPane);

        bg.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        var fade = new FadeTransition(Duration.millis(260), card);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();

        logArea.textProperty().addListener((_, _, _) -> logArea.setScrollTop(Double.MAX_VALUE));

        setOnKeyPressed(event -> {
            if (Objects.requireNonNull(event.getCode()) == KeyCode.ESCAPE) {
                logsPane.setExpanded(!logsPane.isExpanded());
            }
        });

        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                stopTicker();
            } else if (elapsedTicker != null && startInstant.get() != null) {
                elapsedTicker.play();
            }
        });
    }

    /**
     * Binds progress and task text to a service and installs its running, success, and failure handlers.
     * The service message is interpreted as a localization key. This method does not start the service.
     *
     * @param service service whose progress and lifecycle are displayed
     * @param onCancel action invoked by the cancel button, or {@code null} for no action
     * @param onSuccess action invoked on successful completion, or {@code null} for no action
     * @param onError consumer of the service failure, or {@code null} for no action
     */
    public void bindToService(
        Service<?> service,
        Runnable onCancel,
        Runnable onSuccess,
        Consumer<Throwable> onError
    ) {
        spinner.progressProperty().bind(service.progressProperty());

        // Task message → task chip
        taskChip.textProperty().bind(Bindings.createStringBinding(() -> {
            var key = service.getMessage();
            if (key == null || key.isBlank())
                return L18n.localize("railroad.project.creation.status.task");
            return key.startsWith("railroad.") ? L18n.localize(key) : key;
        }, service.messageProperty()));

        // Elapsed time ticker
        service.setOnRunning(_ -> startTicker());
        service.setOnSucceeded(_ -> {
            stopTicker();
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
        service.setOnCancelled(_ -> {
            stopTicker();
            ProjectCreationPane.returnToWelcome();
        });
        service.setOnFailed(_ -> {
            stopTicker();
            titleLabel.setKey("railroad.project.creation.error.title");
            progressStack.setVisible(false);
            progressStack.setManaged(false);
            taskChip.textProperty().unbind();
            taskChip.setText(L18n.localize("railroad.project.creation.error.header"));
            logArea.appendText("\n" + StringUtils.exceptionToString(service.getException()));
            logsPane.setExpanded(true);
            cancelBtn.disableProperty().unbind();
            cancelBtn.setDisable(false);
            cancelBtn.setLocalizedText("railroad.generic.back");
            cancelBtn.setOnAction(event -> ProjectCreationPane.returnToWelcome());
            if (onError != null) {
                onError.accept(service.getException());
            }
        });

        // Cancel
        cancelBtn.disableProperty().bind(service.runningProperty().not());
        cancelBtn.setOnAction(_ -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
    }

    private void startTicker() {
        startInstant.set(Instant.now());
        if (elapsedTicker != null) {
            elapsedTicker.stop();
        }
        elapsedTicker = new Timeline(new KeyFrame(Duration.seconds(1), _ -> {
            var start = startInstant.get();
            if (start == null)
                return;
            long secs = java.time.Duration.between(start, Instant.now()).getSeconds();
            long h = secs / 3600;
            secs %= 3600;
            long m = secs / 60;
            secs %= 60;
            String txt = (h > 0)
                ? String.format("%d:%02d:%02d", h, m, secs)
                : String.format("%02d:%02d", m, secs);
            timeChip.setText(txt);
        }));
        elapsedTicker.setCycleCount(Timeline.INDEFINITE);
        if (getScene() != null) {
            elapsedTicker.playFromStart();
        }
    }

    private void stopTicker() {
        if (elapsedTicker != null) {
            elapsedTicker.stop();
        }
    }

    private static Label chip(String text) {
        var l = new Label(text);
        l.getStyleClass().add("rr-chip");
        return l;
    }

}
