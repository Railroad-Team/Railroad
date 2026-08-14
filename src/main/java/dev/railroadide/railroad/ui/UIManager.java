package dev.railroadide.railroad.ui;

import dev.railroadide.railroad.ui.id.UIId;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class UIManager {
    private final Map<UIId<?>, Node> idMap = new ConcurrentHashMap<>(UIIds.size());

    public <T extends Node> Registration assign(UIId<T> id, T node) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(node);

        Node existing = idMap.putIfAbsent(id, node);
        if (existing != null)
            throw new IllegalArgumentException("UIId '" + id.path() + "' is already registered.");

        return new Registration(() -> idMap.remove(id, node));
    }

    public <T extends Node> Optional<T> lookup(UIId<T> id) {
        return Optional.ofNullable(id.type().cast(idMap.get(id)));
    }

    public <T extends Node> T lookupOrThrow(UIId<T> id) {
        return lookup(id).orElseThrow(() -> new IllegalArgumentException("UIId '" + id.path() + "' is not registered."));
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
     * @param id    ID to assign
     * @param owner node whose scene attachment controls the registration lifetime
     * @param node  node exposed by the ID
     * @param <T>   exposed node type
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
                    active = assign(id, node);
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
