package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.ide.activity.BackgroundActivities.Activity;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Shows the most recently started background activity, such as indexing or a build import, with a count of the
 * others still running. The pane is hidden while nothing is running.
 */
public class IDEActivityPane extends RRHBox {
    private final BackgroundActivities activities;
    private final ProgressIndicator indicator = new ProgressIndicator();
    private final LocalizedLabel title = new LocalizedLabel("");
    private final LocalizedLabel others = new LocalizedLabel("");
    private final Tooltip tooltip = new Tooltip();
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private BackgroundActivities.Subscription subscription;

    /** Creates a pane observing the shared activity registry while it is attached to a scene. */
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
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);
        indicator.setPrefSize(14, 14);
        indicator.setMaxSize(14, 14);
        others.getStyleClass().add("rr-activity-others");
        getChildren().addAll(indicator, title, others);
        Tooltip.install(this, tooltip);
        show(List.of());

        sceneProperty().addListener((_, _, scene) -> {
            if (subscription != null) {
                subscription.close();
                subscription = null;
            }
            if (scene != null) {
                subscription = activities.observe(this::queueRefresh);
                show(activities.running());
            }
        });
    }

    private void queueRefresh() {
        if (refreshQueued.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                refreshQueued.set(false);
                show(activities.running());
            });
        }
    }

    private void show(List<Activity> running) {
        boolean active = !running.isEmpty();
        setVisible(active);
        setManaged(active);
        indicator.setProgress(active ? ProgressIndicator.INDETERMINATE_PROGRESS : 0);
        if (!active) {
            title.setKey("");
            others.setKey("");
            tooltip.setText("");
            return;
        }

        title.setKey(running.getLast().titleKey());
        if (running.size() > 1) {
            others.setKey("railroad.activity.others", running.size() - 1);
        } else {
            others.setKey("");
        }
        tooltip.setText(running.reversed().stream().map(activity -> L18n.localize(activity.titleKey()))
            .collect(Collectors.joining("\n")));
    }
}
