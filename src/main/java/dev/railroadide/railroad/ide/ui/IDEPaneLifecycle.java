package dev.railroadide.railroad.ide.ui;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns resources whose lifetime must not exceed that of an {@link IDEPane}.
 */
public final class IDEPaneLifecycle implements AutoCloseable {
    private final Node owner;
    private final List<Runnable> cleanupActions = new ArrayList<>();

    private boolean wasAttached;
    private boolean closed;

    private final ChangeListener<Scene> sceneListener = (_, _, newScene) -> updateAttachment(newScene != null);

    private void updateAttachment(boolean attached) {
        if (attached) {
            wasAttached = true;
        } else if (wasAttached) {
            close();
        }
    }

    /**
     * Tracks a node attachment and disposes registered resources after it leaves its scene.
     *
     * @param owner node whose scene attachment controls the resource lifetime
     */
    public IDEPaneLifecycle(Node owner) {
        this.owner = Objects.requireNonNull(owner, "Lifecycle owner cannot be null");
        this.wasAttached = owner.getScene() != null;
        owner.sceneProperty().addListener(sceneListener);
    }

    /**
     * Registers a cleanup action, or runs it immediately if the lifecycle is already closed.
     *
     * @param cleanupAction cleanup callback, run immediately if the lifecycle is already closed
     */
    public void onDispose(Runnable cleanupAction) {
        Objects.requireNonNull(cleanupAction, "Cleanup action cannot be null");
        if (closed) {
            cleanupAction.run();
            return;
        }

        cleanupActions.add(cleanupAction);
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        owner.sceneProperty().removeListener(sceneListener);
        RuntimeException failure = null;
        for (int index = cleanupActions.size() - 1; index >= 0; index--) {
            try {
                cleanupActions.get(index).run();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        cleanupActions.clear();

        if (failure != null)
            throw failure;
    }
}
