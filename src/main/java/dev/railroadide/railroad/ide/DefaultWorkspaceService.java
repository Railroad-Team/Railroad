package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.events.WorkspaceModeAvailabilityChangedEvent;
import dev.railroadide.railroad.plugin.spi.events.WorkspaceModeChangedEvent;
import dev.railroadide.railroad.plugin.spi.services.WorkspaceService;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Application implementation of the plugin-facing workspace API. */
public final class DefaultWorkspaceService implements WorkspaceService {
    private final ObjectProperty<WorkspaceMode> currentViewMode = new SimpleObjectProperty<>(
        WorkspaceMode.defaultMode());
    private volatile WorkspaceAdapter activeWorkspace;
    private volatile Set<WorkspaceMode> availableModes = Set.of();

    public DefaultWorkspaceService() {
        currentViewMode.addListener((_, previousMode, currentMode) -> {
            WorkspaceMode previous = resolve(previousMode);
            WorkspaceMode current = resolve(currentMode);
            if (previous != current) {
                Railroad.EVENT_BUS.publish(new WorkspaceModeChangedEvent(previous.getId(), current.getId()));
            }
        });
    }

    /** Creates the internal controller used by an IDE pane without exposing JavaFX state through the SPI. */
    public WorkspaceModeController createModeController(Predicate<WorkspaceMode> availability) {
        return new WorkspaceModeController(currentViewMode, availability);
    }

    /**
     * Attaches the currently displayed IDE workspace to this service. The returned registration must be closed with
     * the pane lifecycle.
     */
    public Registration attachWorkspace(
        Predicate<WorkspaceMode> activation,
        Predicate<WorkspaceMode> availability) {
        var adapter = new WorkspaceAdapter(
            Objects.requireNonNull(activation, "Activation predicate cannot be null"),
            Objects.requireNonNull(availability, "Availability predicate cannot be null"));
        WorkspaceAdapter previous = activeWorkspace;
        activeWorkspace = adapter;
        if (previous != null && previous != adapter) {
            publishAvailabilityChanges(availableModes, Set.of());
        }
        refreshAvailability();
        return new Registration(() -> detachWorkspace(adapter));
    }

    /** Recomputes availability after project capabilities such as Git change. */
    public void refreshAvailability() {
        WorkspaceAdapter workspace = activeWorkspace;
        Set<WorkspaceMode> nextModes = workspace == null
            ? Set.of()
            : WorkspaceMode.REGISTRY.values().stream()
                .filter(workspace.availability)
                .collect(Collectors.toUnmodifiableSet());
        Set<WorkspaceMode> previousModes = availableModes;
        availableModes = nextModes;
        publishAvailabilityChanges(previousModes, nextModes);
    }

    @Override
    public Set<String> getModeIds() {
        return Set.copyOf(WorkspaceMode.REGISTRY.keys());
    }

    @Override
    public Set<String> getAvailableModeIds() {
        return availableModes.stream()
            .map(WorkspaceMode::getId)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getActiveModeId() {
        return activeWorkspace == null ? null : resolve(currentViewMode.get()).getId();
    }

    @Override
    public boolean isModeAvailable(String modeId) {
        return WorkspaceMode.fromId(modeId).filter(availableModes::contains).isPresent();
    }

    @Override
    public CompletableFuture<ActivationResult> activateMode(String modeId) {
        WorkspaceMode requestedMode = WorkspaceMode.fromId(modeId).orElse(null);
        if (requestedMode == null)
            return CompletableFuture.completedFuture(ActivationResult.UNKNOWN_MODE);

        var result = new CompletableFuture<ActivationResult>();
        JavaFXUtils.runOnApplicationThread(() -> {
            WorkspaceAdapter workspace = activeWorkspace;
            if (workspace == null) {
                result.complete(ActivationResult.NO_ACTIVE_WORKSPACE);
            } else if (!workspace.availability.test(requestedMode)) {
                refreshAvailability();
                result.complete(ActivationResult.UNAVAILABLE);
            } else if (workspace.activation.test(requestedMode)) {
                refreshAvailability();
                result.complete(ActivationResult.ACTIVATED);
            } else {
                refreshAvailability();
                result.complete(ActivationResult.UNAVAILABLE);
            }
        });
        return result;
    }

    private void detachWorkspace(WorkspaceAdapter adapter) {
        if (activeWorkspace != adapter)
            return;

        activeWorkspace = null;
        Set<WorkspaceMode> previousModes = availableModes;
        availableModes = Set.of();
        publishAvailabilityChanges(previousModes, Set.of());
        currentViewMode.set(WorkspaceMode.defaultMode());
    }

    private void publishAvailabilityChanges(Set<WorkspaceMode> previous, Set<WorkspaceMode> current) {
        Set<WorkspaceMode> modes = new LinkedHashSet<>(WorkspaceMode.REGISTRY.values());
        modes.addAll(previous);
        modes.addAll(current);
        for (WorkspaceMode mode : modes) {
            boolean wasAvailable = previous.contains(mode);
            boolean isAvailable = current.contains(mode);
            if (wasAvailable != isAvailable) {
                Railroad.EVENT_BUS.publish(new WorkspaceModeAvailabilityChangedEvent(mode.getId(), isAvailable));
            }
        }
    }

    private static WorkspaceMode resolve(WorkspaceMode mode) {
        return mode == null ? WorkspaceMode.defaultMode() : mode;
    }

    private record WorkspaceAdapter(
        Predicate<WorkspaceMode> activation,
        Predicate<WorkspaceMode> availability) {
    }

    public static final class Registration implements AutoCloseable {
        private Runnable removal;

        private Registration(Runnable removal) {
            this.removal = removal;
        }

        @Override
        public void close() {
            if (removal != null) {
                removal.run();
                removal = null;
            }
        }
    }
}
