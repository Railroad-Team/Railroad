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

    public String getId() {
        return id;
    }

    public WorkspaceMode getMode() {
        return mode;
    }

    public UIId<DetachableTabPane> getDockId() {
        return dockId;
    }

    public static WorkspaceContentTarget register(
        String id,
        WorkspaceMode mode,
        UIId<DetachableTabPane> dockId) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Workspace content-target ID cannot be null or blank");

        return REGISTRY.register(id, new WorkspaceContentTarget(
            id,
            Objects.requireNonNull(mode, "Workspace mode cannot be null"),
            Objects.requireNonNull(dockId, "Workspace content-target dock cannot be null")));
    }

    public static Optional<WorkspaceContentTarget> fromId(String id) {
        if (id == null || id.isBlank())
            return Optional.empty();
        return Optional.ofNullable(REGISTRY.get(id.trim()));
    }
}
