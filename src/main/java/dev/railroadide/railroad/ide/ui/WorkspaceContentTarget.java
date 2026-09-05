package dev.railroadide.railroad.ide.ui;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.ide.WorkspaceMode;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import dev.railroadide.railroad.ui.id.UIId;

import java.util.Objects;
import java.util.Optional;

/** Registered destination for content routed into a workspace mode. */
public final class WorkspaceContentTarget {
    /**
     * Registry of workspace content destinations by stable identifier.
     */
    public static final Registry<WorkspaceContentTarget> REGISTRY = RegistryManager
        .createOrderedRegistry("railroad:workspace_content_target", WorkspaceContentTarget.class);

    private final String id;
    private final WorkspaceMode mode;
    private final UIId<DetachableTabPane> dockId;

    private WorkspaceContentTarget(String id, WorkspaceMode mode, UIId<DetachableTabPane> dockId) {
        this.id = id;
        this.mode = mode;
        this.dockId = dockId;
    }

    /**
     * Returns the stable destination identifier.
     *
     * @return content-target identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the workspace mode containing this destination.
     *
     * @return destination workspace mode
     */
    public WorkspaceMode getMode() {
        return mode;
    }

    /**
     * Returns the UI registry key of the destination pane.
     *
     * @return destination tab-pane identifier
     */
    public UIId<DetachableTabPane> getDockId() {
        return dockId;
    }

    /**
     * Registers a named destination for opening content in a workspace pane.
     *
     * @param id stable content-target identifier
     * @param mode workspace mode containing the destination
     * @param dockId UI registry identifier of the destination tab pane
     * @return registered content target
     */
    public static WorkspaceContentTarget register(
        String id,
        WorkspaceMode mode,
        UIId<DetachableTabPane> dockId
    ) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Workspace content-target ID cannot be null or blank");

        return REGISTRY.register(id, new WorkspaceContentTarget(
            id,
            Objects.requireNonNull(mode, "Workspace mode cannot be null"),
            Objects.requireNonNull(dockId, "Workspace content-target dock cannot be null")));
    }

    /**
     * Looks up a registered destination by identifier.
     *
     * @param id stable content-target identifier
     * @return matching destination, or an empty optional for an unknown or blank identifier
     */
    public static Optional<WorkspaceContentTarget> fromId(String id) {
        if (id == null || id.isBlank())
            return Optional.empty();
        return Optional.ofNullable(REGISTRY.get(id.trim()));
    }
}
