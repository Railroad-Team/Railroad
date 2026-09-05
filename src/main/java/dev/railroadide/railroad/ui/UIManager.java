package dev.railroadide.railroad.ui;

import dev.railroadide.railroad.ui.id.UIId;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.beans.value.ChangeListener;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers JavaFX nodes under typed UI identifiers and manages their registration lifetimes.
 * Assignments may be explicitly closed or tied to a node's scene attachment. Operations that
 * observe or modify a live scene graph must follow JavaFX's application-thread requirements.
 */
public final class UIManager {
    private final Map<UIId<?>, Assignment> idMap = new ConcurrentHashMap<>(UIIds.size());

    /** Creates a manager with no registered nodes. */
    public UIManager() {
    }

    /**
     * Registers a node until the returned handle is closed or the assignment is explicitly removed.
     * The node also serves as the assignment's owner for subtree cleanup.
     *
     * @param id identifier under which to expose the node
     * @param node node to register
     * @param <T> node type represented by the identifier
     * @return a handle that removes this assignment when closed
     * @throws NullPointerException if {@code id} or {@code node} is {@code null}
     * @throws IllegalArgumentException if the identifier is already registered
     */
    public <T extends Node> Registration assign(UIId<T> id, T node) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(node);

        return assign(id, node, node);
    }

    private <T extends Node> Registration assign(UIId<T> id, Node owner, T node) {
        var assignment = new Assignment(owner, node);
        Assignment existing = idMap.putIfAbsent(id, assignment);
        if (existing != null)
            throw new IllegalArgumentException("UIId '" + id.path() + "' is already registered.");

        return new Registration(() -> idMap.remove(id, assignment));
    }

    /**
     * Looks up a registered node and casts it to the identifier's declared type.
     *
     * @param id identifier to look up
     * @param <T> node type represented by the identifier
     * @return the registered node, or an empty optional if no assignment exists
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws ClassCastException if an assignment made through unsafe generic usage has the wrong node type
     */
    public <T extends Node> Optional<T> lookup(UIId<T> id) {
        Assignment assignment = idMap.get(id);
        return Optional.ofNullable(assignment == null ? null : id.type().cast(assignment.node()));
    }

    /**
     * Looks up a registered node, requiring an assignment to exist.
     *
     * @param id identifier to look up
     * @param <T> node type represented by the identifier
     * @return the registered node
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if the identifier is not registered
     * @throws ClassCastException if an assignment made through unsafe generic usage has the wrong node type
     */
    public <T extends Node> T lookupOrThrow(UIId<T> id) {
        return lookup(id)
            .orElseThrow(() -> new IllegalArgumentException("UIId '" + id.path() + "' is not registered."));
    }

    /**
     * Checks whether an identifier currently has an assignment.
     *
     * @param id identifier to check
     * @param <T> node type represented by the identifier
     * @return {@code true} if the identifier is registered
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public <T extends Node> boolean isRegistered(UIId<T> id) {
        return idMap.containsKey(id);
    }

    /**
     * Tracks a node's scene attachment, registering it while attached and removing it on detachment.
     * Reattachment registers the node again until the returned tracking handle is closed.
     *
     * @param id identifier under which to expose the node
     * @param node node whose scene attachment controls its registration
     * @param <T> node type represented by the identifier
     * @return a handle that stops attachment tracking and removes any active assignment
     * @throws NullPointerException if {@code id} or {@code node} is {@code null}
     * @throws IllegalArgumentException if the node is already attached and the identifier is registered
     */
    public <T extends Node> Registration assignWhileAttached(UIId<T> id, T node) {
        return assignWhileAttached(id, node, node);
    }

    /**
     * Assigns a node while a separate owner is attached to a scene.
     * <p>
     * This is useful for nodes that belong to an attached UI component but are temporarily absent from its scene
     * graph, such as an inactive editor dock in a view-mode workspace.
     * Detachment removes the assignment; reattachment attempts to register it again. The identifier
     * must be available whenever attachment triggers registration.
     *
     * @param id ID to assign
     * @param owner node whose scene attachment controls the registration lifetime
     * @param node node exposed by the ID
     * @param <T> exposed node type
     * @return a registration that can stop tracking the owner and remove the assignment
     * @throws NullPointerException if {@code id}, {@code owner}, or {@code node} is {@code null}
     * @throws IllegalArgumentException if the owner is already attached and the identifier is registered
     */
    public <T extends Node> Registration assignWhileAttached(UIId<T> id, Node owner, T node) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(node);

        class SceneBinding {
            private Registration active;
            private boolean closed;

            private void update(Scene scene) {
                if (closed)
                    return;

                if (scene == null) {
                    if (active != null) {
                        active.close();
                        active = null;
                    }
                } else if (active == null) {
                    active = assign(id, owner, node);
                }
            }

            private void close() {
                closed = true;
                owner.sceneProperty().removeListener(listener);

                if (active != null) {
                    active.close();
                    active = null;
                }
            }

            private final ChangeListener<Scene> listener = (_, _, scene) -> update(scene);
        }

        var binding = new SceneBinding();
        owner.sceneProperty().addListener(binding.listener);
        binding.update(owner.getScene());
        return new Registration(binding::close);
    }

    /**
     * Removes all assignments owned by, or contained within, a UI subtree.
     * <p>
     * This is a defensive cleanup operation for top-level UI disposal. Normal registration handles remain safe to
     * close afterwards because assignments are removed using both their id and assignment identity.
     * Attachment listeners remain installed until their tracking handles are closed.
     *
     * @param root root of the UI subtree being disposed
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public void unregisterSubtree(Node root) {
        Objects.requireNonNull(root, "Root cannot be null");
        idMap.forEach((id, assignment) -> {
            if (isInSubtree(assignment.owner(), root) || isInSubtree(assignment.node(), root)) {
                idMap.remove(id, assignment);
            }
        });
    }

    /**
     * Unregisters and detaches a scene's current graph so its nodes are no longer considered attached to JavaFX.
     * Replaces the scene root with an empty group.
     *
     * @param scene scene whose graph is being retired
     * @throws NullPointerException if {@code scene} is {@code null}
     */
    public void releaseScene(Scene scene) {
        Objects.requireNonNull(scene, "Scene cannot be null");
        Parent root = scene.getRoot();
        unregisterSubtree(root);
        scene.setRoot(new Group());
    }

    private static boolean isInSubtree(Node node, Node root) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == root)
                return true;
        }
        return false;
    }

    private record Assignment(Node owner, Node node) {
        private Assignment {
            Objects.requireNonNull(owner, "Owner cannot be null");
            Objects.requireNonNull(node, "Node cannot be null");
        }
    }

    /**
     * A closeable handle for removing an assignment or stopping attachment tracking.
     * Closing a successfully closed handle again has no effect and does not remove a later replacement assignment.
     */
    public static final class Registration implements AutoCloseable {
        private Runnable removal;

        private Registration(Runnable removal) {
            this.removal = removal;
        }

        /**
         * Runs this handle's cleanup once, removing its assignment and any attachment listener it owns.
         * Subsequent calls have no effect after cleanup succeeds.
         */
        @Override
        public void close() {
            if (removal != null) {
                removal.run();
                removal = null;
            }
        }
    }
}
