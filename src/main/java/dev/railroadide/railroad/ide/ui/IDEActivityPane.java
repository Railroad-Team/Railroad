package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.ide.activity.BackgroundActivities.Activity;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.geometry.Point2D;

/**
 * Shows the most recently started background activity, such as indexing or a build import, with a count of the
 * others still running. Its toggle opens a task popup over the workspace until explicitly dismissed.
 */
public class IDEActivityPane extends ToggleButton {
    private final BackgroundActivities activities;
    private final ProgressBar indicator = new ProgressBar();
    private final Label phase = new Label();
    private final Label detail = new Label();
    private final LocalizedLabel title = new LocalizedLabel("");
    private final LocalizedLabel others = new LocalizedLabel("");
    private final VBox taskRows = new VBox(4);
    private final ScrollPane taskList = new ScrollPane(taskRows);
    private final Pane taskOverlay = new Pane(taskList) {
        @Override
        protected void layoutChildren() {
            double width = Math.min(getWidth(), taskList.maxWidth(-1));
            double bottom = Math.max(0, getHeight() - 4);
            double height = Math.min(bottom, Math.min(taskList.maxHeight(width), taskList.prefHeight(width)));
            Point2D anchor = sceneToLocal(IDEActivityPane.this.localToScene(IDEActivityPane.this.getWidth(), 0));
            double left = Math.clamp(anchor.getX() - width, 0, getWidth() - width);
            taskList.resizeRelocate(snapPositionX(left), snapPositionY(bottom - height), width, height);
        }
    };

    private final LocalizedLabel empty = new LocalizedLabel("railroad.activity.none_running");
    private final Map<Long, ActivityRow> rows = new HashMap<>();
    private final PauseTransition etaTick = new PauseTransition(Duration.seconds(1));
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private BackgroundActivities.Subscription subscription;

    /**
     * Creates a pane observing the shared activity registry while it is attached to a scene.
     */
    public IDEActivityPane() {
        this(BackgroundActivities.shared());
    }

    /**
     * Creates a pane observing the given registry while it is attached to a scene.
     *
     * @param activities registry whose running work is shown
     */
    public IDEActivityPane(BackgroundActivities activities) {
        this.activities = activities;
        getStyleClass().add("rr-activity-pane");
        setAccessibleText(L18n.localize("railroad.activity.running_tasks"));
        indicator.setPrefWidth(110);
        indicator.setMinWidth(64);
        phase.setMaxWidth(180);
        phase.setMinWidth(0);
        detail.setMinWidth(USE_PREF_SIZE);
        phase.managedProperty().bind(phase.textProperty().isNotEmpty());
        detail.managedProperty().bind(detail.textProperty().isNotEmpty());
        others.getStyleClass().add("rr-activity-others");
        var arrow = new Region();
        arrow.getStyleClass().add("rr-activity-arrow");
        var summary = new HBox(6, indicator, title, phase, detail, others, arrow);
        summary.setAlignment(Pos.CENTER_LEFT);
        summary.setMouseTransparent(true);
        setGraphic(summary);
        taskList.getStyleClass().add("rr-activity-list");
        taskList.setFitToWidth(true);
        taskList.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        taskList.setMaxSize(360, 240);
        taskList.setPrefViewportWidth(356);
        taskList.setMinSize(0, 0);
        taskOverlay.setPickOnBounds(false);
        taskOverlay.setMinSize(0, 0);
        taskOverlay.setPrefSize(0, 0);
        taskOverlay.visibleProperty().bind(selectedProperty());
        taskOverlay.managedProperty().bind(selectedProperty());
        widthProperty().addListener(_ -> taskOverlay.requestLayout());
        localToSceneTransformProperty().addListener((_, _, _) -> taskOverlay.requestLayout());
        selectedProperty().addListener(_ -> show(activities.running()));
        etaTick.setOnFinished(_ -> show(activities.running()));
        show(List.of());

        sceneProperty().addListener((_, _, scene) -> {
            if (subscription != null) {
                etaTick.stop();
                subscription.close();
                subscription = null;
            }

            if (scene != null) {
                subscription = activities.observe(this::queueRefresh);
                show(activities.running());
            }
        });
    }

    /**
     * Returns the layer to stack over the workspace content immediately above the status bar.
     */
    public Region getTaskOverlay() {
        return taskOverlay;
    }

    private void queueRefresh() {
        if (refreshQueued.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                refreshQueued.set(false);
                if (subscription != null) {
                    show(activities.running());
                }
            });
        }
    }

    private static String phaseText(Activity activity) {
        String phase = activity.phase();
        return phase.startsWith("railroad.activity.") ? L18n.localize(phase) : phase;
    }

    private static String progressText(Activity activity) {
        List<String> parts = new ArrayList<String>();
        if (activity.total() >= 0) {
            parts.add(switch (activity.unit()) {
                case BYTES -> byteCount(activity.completed(), activity.total());
                case PERCENT -> activity.completed() + "%";
                default -> activity.completed() + "/" + activity.total();
            });
        }

        long seconds = activity.remainingSeconds();
        if (seconds > 0) {
            long minutes = Math.ceilDiv(seconds, 60);
            if (seconds < 60) {
                parts.add(L18n.localize("railroad.activity.eta.seconds", seconds));
            } else if (minutes < 60) {
                parts.add(L18n.localize("railroad.activity.eta.minutes", minutes));
            } else {
                parts.add(L18n.localize("railroad.activity.eta.hours", minutes / 60, minutes % 60));
            }
        }

        return String.join(" · ", parts);
    }

    private static String byteCount(long done, long total) {
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};

        double divisor = 1;
        int unit = 0;
        while (unit < units.length - 1 && total / divisor >= 1024) {
            divisor *= 1024;
            unit++;
        }

        if (unit == 0)
            return done + "/" + total + " B";

        return "%.1f/%.1f %s".formatted(done / divisor, total / divisor, units[unit]);
    }

    private void show(List<Activity> running) {
        boolean active = !running.isEmpty();
        setVisible(active || isSelected());
        setManaged(isVisible());
        indicator.setVisible(active);
        indicator.setManaged(active);
        if (isSelected()) {
            updateRows(running);
        } else if (!taskRows.getChildren().isEmpty()) {
            taskRows.getChildren().clear();
            rows.clear();
        }

        if (running.stream().anyMatch(item -> item.remainingSeconds() > 0)) {
            etaTick.play();
        } else {
            etaTick.stop();
        }

        if (!active) {
            indicator.setProgress(0);
            phase.setText("");
            detail.setText("");
            title.setKey("railroad.activity.running_tasks");
            others.setKey("");
            return;
        }

        Activity activity = running.getLast();
        indicator.setProgress(activity.fraction());
        title.setKey(activity.titleKey());
        phase.setText(phaseText(activity));
        detail.setText(progressText(activity));
        if (running.size() > 1) {
            others.setKey("railroad.activity.others", running.size() - 1);
        } else {
            others.setKey("");
        }
    }

    private void updateRows(List<Activity> running) {
        if (running.isEmpty()) {
            rows.clear();
            taskRows.getChildren().setAll(empty);
            return;
        }

        List<ActivityRow> items = new ArrayList<ActivityRow>(running.size());
        for (Activity activity : running.reversed()) {
            ActivityRow row = rows.computeIfAbsent(activity.id(), _ -> new ActivityRow(activity));
            row.update(activity);
            items.add(row);
        }

        if (!taskRows.getChildren().equals(items)) {
            rows.values().retainAll(items);
            taskRows.getChildren().setAll(items);
        }
    }

    private static final class ActivityRow extends VBox {
        private final Label phase = new Label();
        private final Label detail = new Label();
        private final ProgressBar indicator = new ProgressBar();

        private ActivityRow(Activity activity) {
            super(4);
            var title = new Label(L18n.localize(activity.titleKey()));
            title.getStyleClass().add("rr-activity-title");
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);
            detail.setMinWidth(USE_PREF_SIZE);
            var header = new HBox(8, title, detail);
            header.setAlignment(Pos.BASELINE_LEFT);
            phase.managedProperty().bind(phase.textProperty().isNotEmpty());
            indicator.setMaxWidth(Double.MAX_VALUE);
            getStyleClass().add("rr-activity-row");
            getChildren().addAll(header, phase, indicator);
        }

        private void update(Activity activity) {
            phase.setText(phaseText(activity));
            detail.setText(progressText(activity));
            indicator.setProgress(activity.fraction());
        }
    }
}
