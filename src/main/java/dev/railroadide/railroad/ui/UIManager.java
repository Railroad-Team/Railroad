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

public final class UIManager {
    private final Map<UIId<?>, Assignment> idMap = new ConcurrentHashMap<>(UIIds.size());

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

    public <T extends Node> Optional<T> lookup(UIId<T> id) {
        Assignment assignment = idMap.get(id);
        return Optional.ofNullable(assignment == null ? null : id.type().cast(assignment.node()));
    }

    public <T extends Node> T lookupOrThrow(UIId<T> id) {
        return lookup(id)
            .orElseThrow(() -> new IllegalArgumentException("UIId '" + id.path() + "' is not registered."));
    }

    public <T extends Node> boolean isRegistered(UIId<T> id) {
        return idMap.containsKey(id);
    }

    public <T extends Node> Registration assignWhileAttached(UIId<T> id, T node) {
        return assignWhileAttached(id, node, node);
    }

    /**
     * Assigns a node while a separate owner is attached to a scene.
     * <p>
     * This is useful for nodes that belong to an attached UI component but are temporarily absent from its scene
     * graph, such as an inactive editor dock in a view-mode workspace.
     *
     * @param id ID to assign
     * @param owner node whose scene attachment controls the registration lifetime
     * @param node node exposed by the ID
     * @param <T> exposed node type
     * @return a registration that can stop tracking the owner and remove the assignment
     */
    public <T extends Node> Registration assignWhileAttached(UIId<T> id, Node owner, T node) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(node);

        class SceneBinding {
            private Registration active;
            private boolean closed;

            void update(Scene scene) {
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

            void close() {
                closed = true;
                owner.sceneProperty().removeListener(listener);

                if (active != null) {
                    active.close();
                    active = null;
                }
            }

            final ChangeListener<Scene> listener = (_, _, scene) -> update(scene);
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
     *
     * @param root root of the UI subtree being disposed
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
     *
     * @param scene scene whose graph is being retired
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
