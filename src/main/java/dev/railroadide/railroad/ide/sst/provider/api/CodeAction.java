package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;

import java.util.Objects;

/**
 * A proposed fix described as a snapshot-bound workspace edit. Computing an action never mutates a document; hosts
 * preview and apply its edit through the workspace-edit applier, which rejects stale snapshots, overlaps and
 * read-only targets and records the edit as one undo step.
 *
 * @param title nonblank label displayed for this action
 * @param edit changes the action would make
 */
public record CodeAction(String title, WorkspaceEdit edit) {
    /**
     * Creates an action.
     *
     * @param title nonblank label displayed for this action
     * @param edit changes the action would make
     * @throws IllegalArgumentException if the title is blank
     */
    public CodeAction {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(edit, "edit");
        if (title.isBlank())
            throw new IllegalArgumentException("Action title must not be blank");
    }
}
