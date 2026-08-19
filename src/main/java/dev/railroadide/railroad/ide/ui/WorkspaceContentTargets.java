package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ui.id.UIIds;

/** Registers Railroad's built-in workspace content targets. */
public final class WorkspaceContentTargets {
    public static final WorkspaceContentTarget CODE_EDITOR = WorkspaceContentTarget.register(
        "railroad:code_editor",
        WorkspaceModes.CODE,
        UIIds.IDE.IDE_CODE_EDITOR_DOCK
    );
    public static final WorkspaceContentTarget GIT_EDITOR = WorkspaceContentTarget.register(
        "railroad:git_editor",
        WorkspaceModes.GIT,
        UIIds.IDE.IDE_GIT_EDITOR_DOCK
    );

    private WorkspaceContentTargets() {
    }

    public static void initialize() {
    }
}
