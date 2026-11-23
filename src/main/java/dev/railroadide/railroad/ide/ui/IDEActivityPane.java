package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ide.activity.BackgroundActivities;
import dev.railroadide.railroad.ide.activity.BackgroundActivities.Activity;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shows the most recently started background activity, such as indexing or a build import, with a count of the
 * others still running. The pane is hidden while nothing is running.
 */
public class IDEActivityPane extends RRHBox {
    private final BackgroundActivities activities;
    private final ProgressBar indicator = new ProgressBar();
    private final Label phase = new Label();
    private final Label detail = new Label();
    private final LocalizedLabel title = new LocalizedLabel("");
    private final LocalizedLabel others = new LocalizedLabel("");
    private final Tooltip tooltip = new Tooltip();
    private final PauseTransition etaTick = new PauseTransition(Duration.seconds(1));
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
        indicator.setPrefWidth(110);
        indicator.setMinWidth(64);
        phase.setMaxWidth(180);
        phase.setMinWidth(0);
        detail.setMinWidth(USE_PREF_SIZE);
        phase.managedProperty().bind(phase.textProperty().isNotEmpty());
        detail.managedProperty().bind(detail.textProperty().isNotEmpty());
        others.getStyleClass().add("rr-activity-others");
        getChildren().addAll(indicator, title, phase, detail, others);
        Tooltip.install(this, tooltip);
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
        var parts = new ArrayList<String>();
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
        setVisible(active);
        setManaged(active);

        if (running.stream().anyMatch(item -> item.remainingSeconds() > 0)) {
            etaTick.play();
        } else {
            etaTick.stop();
        }
        if (!active) {
            indicator.setProgress(0);
            phase.setText("");
            detail.setText("");
            title.setKey("");
            others.setKey("");
            tooltip.setText("");
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
        tooltip.setText(running.reversed().stream()
            .map(item -> Stream.of(L18n.localize(item.titleKey()), phaseText(item), progressText(item))
                .filter(part -> !part.isEmpty()).collect(Collectors.joining(" · ")))
            .collect(Collectors.joining("\n")));
    }
}
