package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Coordinates view-mode changes for a single IDE pane.
 * <p>
 * The workspace service owns the shared state property, while consumers of this controller receive a read-only
 * property and lifecycle-bound callbacks.
 */
public final class WorkspaceModeController implements AutoCloseable {
    private final ObjectProperty<WorkspaceMode> stateProperty;
    private final ReadOnlyObjectWrapper<WorkspaceMode> currentViewMode;
    private final Executor applicationThreadExecutor;
    private final Predicate<WorkspaceMode> availability;
    private final List<Consumer<WorkspaceMode>> listeners = new ArrayList<>();
    private final ChangeListener<WorkspaceMode> stateListener = (_, _, newMode) -> acceptExternalState(newMode);

    private boolean closed;
    private boolean updatingState;

    public WorkspaceModeController(ObjectProperty<WorkspaceMode> stateProperty) {
        this(stateProperty, _ -> true);
    }

    public WorkspaceModeController(ObjectProperty<WorkspaceMode> stateProperty, Predicate<WorkspaceMode> availability) {
        this(stateProperty, availability, JavaFXUtils::runOnApplicationThread);
    }

    WorkspaceModeController(ObjectProperty<WorkspaceMode> stateProperty, Executor applicationThreadExecutor) {
        this(stateProperty, _ -> true, applicationThreadExecutor);
    }

    WorkspaceModeController(
        ObjectProperty<WorkspaceMode> stateProperty,
        Predicate<WorkspaceMode> availability,
        Executor applicationThreadExecutor) {
        this.stateProperty = Objects.requireNonNull(stateProperty, "State property cannot be null");
        this.availability = Objects.requireNonNull(availability, "Availability predicate cannot be null");
        this.applicationThreadExecutor = Objects.requireNonNull(applicationThreadExecutor,
            "Application thread executor cannot be null");
        WorkspaceMode initialMode = resolve(stateProperty.get());
        if (!availability.test(initialMode)) {
            initialMode = WorkspaceMode.defaultMode();
        }
        this.currentViewMode = new ReadOnlyObjectWrapper<>(initialMode);
        stateProperty.addListener(stateListener);
        restoreStateProperty(initialMode);
    }

    public WorkspaceMode getCurrentViewMode() {
        return currentViewMode.get();
    }

    public ReadOnlyObjectProperty<WorkspaceMode> currentViewModeProperty() {
        return currentViewMode.getReadOnlyProperty();
    }

    /**
     * Requests a transition through this controller's availability policy.
     *
     * @return whether the request was accepted for delivery to the application thread
     */
    public boolean requestViewMode(WorkspaceMode viewMode) {
        WorkspaceMode resolvedMode = resolve(viewMode);
        if (closed || !availability.test(resolvedMode))
            return false;

        applicationThreadExecutor.execute(() -> {
            if (!closed && availability.test(resolvedMode)) {
                transitionTo(resolvedMode);
            }
        });
        return true;
    }

    /**
     * Registers a callback and immediately supplies the active mode.
     *
     * @param listener callback to invoke for view-mode changes
     * @return a registration that can remove the callback early
     */
    public Registration onViewModeChanged(Consumer<WorkspaceMode> listener) {
        Objects.requireNonNull(listener, "Listener cannot be null");
        if (closed)
            throw new IllegalStateException("View mode controller is closed");

        listeners.add(listener);
        applicationThreadExecutor.execute(() -> {
            if (!closed && listeners.contains(listener)) {
                listener.accept(getCurrentViewMode());
            }
        });
        return new Registration(() -> listeners.remove(listener));
    }

    private void acceptExternalState(WorkspaceMode viewMode) {
        if (updatingState)
            return;

        WorkspaceMode resolvedMode = resolve(viewMode);
        applicationThreadExecutor.execute(() -> {
            if (closed)
                return;

            if (!availability.test(resolvedMode)) {
                restoreStateProperty(currentViewMode.get());
                return;
            }

            transitionTo(resolvedMode);
        });
    }

    private void transitionTo(WorkspaceMode viewMode) {
        if (stateProperty.get() != viewMode) {
            restoreStateProperty(viewMode);
        }
        if (currentViewMode.get() == viewMode)
            return;

        currentViewMode.set(viewMode);
        List.copyOf(listeners).forEach(listener -> listener.accept(viewMode));
    }

    private void restoreStateProperty(WorkspaceMode viewMode) {
        if (stateProperty.get() == viewMode)
            return;

        updatingState = true;
        try {
            stateProperty.set(viewMode);
        } finally {
            updatingState = false;
        }
    }

    private static WorkspaceMode resolve(WorkspaceMode viewMode) {
        return viewMode == null ? WorkspaceMode.defaultMode() : viewMode;
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        stateProperty.removeListener(stateListener);
        listeners.clear();
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
