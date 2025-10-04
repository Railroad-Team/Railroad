package dev.railroadide.railroad.project.creation.ui;

import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.ui.*;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.ui.localized.LocalizedTitledPane;
import dev.railroadide.core.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

public class ProjectCreationView extends RRBorderPane {
    private final StackPane progressStack = new RRStackPane();
    private final MFXProgressSpinner spinner = new MFXProgressSpinner();
    private final Label percentLabel = new Label("0%");

    private final HBox chipRow = new RRHBox(10);
    private final Label taskChip = chip("…");
    private final Label timeChip = chip("00:00");

    private final LocalizedTitledPane logsPane = new LocalizedTitledPane();
    @Getter
    private final TextArea logArea = new TextArea();

    private final RRButton cancelBtn = new RRButton("railroad.generic.cancel");

    private final ObjectProperty<Instant> startInstant = new SimpleObjectProperty<>();
    private Timeline elapsedTicker;

    public ProjectCreationView(ProjectData data) {
        StackPane bg = fancyBackground();
        setCenter(bg);

        var card = new RRVBox(18);
        card.getStyleClass().add("rr-card");
        card.setPadding(new Insets(24));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(760);

        var title = new LocalizedLabel("railroad.project.creation.status.creating.title");
        title.getStyleClass().add("rr-title");

        var subtitle = new LocalizedLabel(
            "railroad.project.creation.status.creating.subtitle",
            data.getAsString(ProjectData.DefaultKeys.NAME)
        );
        subtitle.getStyleClass().add("rr-subtitle");

        var header = new RRVBox(6);
        header.setAlignment(Pos.CENTER);
        header.getChildren().addAll(title, subtitle);

        spinner.setRadius(64);
        spinner.setProgress(0);
        spinner.setPrefSize(160, 160);
        percentLabel.getStyleClass().add("rr-progress-percent");
        percentLabel.setTextAlignment(TextAlignment.CENTER);
        progressStack.getChildren().addAll(spinner, percentLabel);

        chipRow.setAlignment(Pos.CENTER);
        chipRow.getChildren().addAll(taskChip, timeChip);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("rr-log-area");
        logArea.setPrefRowCount(12);

        var logScroll = new ScrollPane(logArea);
        logScroll.setFitToWidth(true);
        logScroll.setFitToHeight(true);
        logScroll.getStyleClass().add("rr-log-scroll");

        logsPane.setKey("railroad.project.creation.status.logs");
        logsPane.setContent(logScroll);
        logsPane.setExpanded(false);
        logsPane.getStyleClass().add("rr-logs-pane");

        cancelBtn.setVariant(RRButton.ButtonVariant.SECONDARY);
        cancelBtn.setTooltip(new LocalizedTooltip("railroad.project.creation.cancel.tooltip"));
        var footer = new RRHBox(12);
        footer.setAlignment(Pos.CENTER);
        footer.getChildren().add(cancelBtn);

        card.getChildren().addAll(
            header,
            new Separator(),
            progressStack,
            chipRow,
            new Separator(),
            logsPane,
            footer
        );

        bg.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        var fade = new FadeTransition(Duration.millis(260), card);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();

        logArea.textProperty().addListener((obs, ov, nv) -> logArea.setScrollTop(Double.MAX_VALUE));

        setOnKeyPressed(event -> {
            if (Objects.requireNonNull(event.getCode()) == KeyCode.ESCAPE) {
                logsPane.setExpanded(!logsPane.isExpanded());
            }
        });

        getScene().getStylesheets().add(Railroad.getResource("styles/project-creation-view.css").toExternalForm());
    }

    public void bindToService(Service<?> service,
                              Runnable onCancel,
                              Runnable onSuccess,
                              Consumer<Throwable> onError) {
        spinner.progressProperty().bind(service.progressProperty());
        percentLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            double p = service.getProgress();
            if (Double.isNaN(p) || p < 0) return "…";
            int pct = (int) Math.round(p * 100.0);
            pct = Math.max(0, Math.min(100, pct));
            return pct + "%";
        }, service.progressProperty()));

        // Task message → task chip
        taskChip.textProperty().bind(Bindings.createStringBinding(() -> {
            var key = service.getMessage();
            if (key == null || key.isBlank()) return L18n.localize("railroad.project.creation.status.task");
            return L18n.localize(key);
        }, service.messageProperty()));

        // Elapsed time ticker
        service.setOnRunning(e -> startTicker());
        service.setOnSucceeded(e -> {
            stopTicker();
            if (onSuccess != null) onSuccess.run();
        });
        service.setOnFailed(e -> {
            stopTicker();
            if (onError != null) onError.accept(service.getException());
        });

        // Cancel
        cancelBtn.disableProperty().bind(service.runningProperty().not());
        cancelBtn.setOnAction(e -> {
            if (onCancel != null) onCancel.run();
        });
    }

    // TODO: Figure out why this isn't used
    public void appendLog(String line) {
        if (!logsPane.isExpanded()) logsPane.setExpanded(true);
        if (logArea.getText().isEmpty()) logArea.appendText(line);
        else logArea.appendText("\n" + line);
    }

    private void startTicker() {
        startInstant.set(Instant.now());
        if (elapsedTicker != null) elapsedTicker.stop();
        elapsedTicker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            var start = startInstant.get();
            if (start == null) return;
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
        elapsedTicker.playFromStart();
    }

    private void stopTicker() {
        if (elapsedTicker != null) elapsedTicker.stop();
    }

    private static Label chip(String text) {
        var l = new Label(text);
        l.getStyleClass().add("rr-chip");
        l.setPadding(new Insets(6, 12, 6, 12));
        return l;
    }

    private static StackPane fancyBackground() {
        var root = new StackPane();
        root.getStyleClass().add("rr-bg");

        var gradient = new Region();
        gradient.getStyleClass().add("rr-gradient");

        var noiseGlass = new StackPane();
        noiseGlass.getStyleClass().add("rr-glass");
        var clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        noiseGlass.setClip(clip);

        root.getChildren().addAll(gradient, noiseGlass);
        return root;
    }
}
