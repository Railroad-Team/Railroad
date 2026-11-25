package dev.railroadide.railroad.ide.sst.edit.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;

import java.util.Objects;

/**
 * Reason a workspace edit, undo or redo could not be applied, or a step that could not be reverted.
 *
 * @param kind category of the problem
 * @param changeIndex index of the offending change in {@link WorkspaceEdit#changes()}, or {@code -1} when the
 *            problem is not tied to one declared change
 * @param uri affected location, or {@code null} when none applies
 * @param message human-readable description
 */
public record WorkspaceEditProblem(Kind kind, int changeIndex, DocumentUri uri, String message) {
    /**
     * Creates a problem description.
     *
     * @param kind category of the problem
     * @param changeIndex offending change index, or {@code -1}
     * @param uri affected location, or {@code null}
     * @param message human-readable description
     */
    public WorkspaceEditProblem {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(message, "message");
        if (changeIndex < -1)
            throw new IllegalArgumentException("changeIndex must be -1 or a change index");
    }

    /**
     * Problem categories.
     */
    public enum Kind {
        /**
         * A bound document changed after the edit was computed, including unobserved on-disk changes.
         */
        STALE,
        /**
         * Replacements overlap, share a start offset, or fall outside the expected document.
         */
        OVERLAP,
        /**
         * The edit is malformed, for example an invalid selection or undecodable new text file.
         */
        INVALID,
        /**
         * The target is read-only, generated, decompiled, attached source, or not writable on disk.
         */
        READ_ONLY,
        /**
         * The target is outside the project directory or in a directory Railroad does not manage.
         */
        OUTSIDE_WORKSPACE,
        /**
         * A bound document or file no longer exists.
         */
        MISSING,
        /**
         * A created or renamed file's destination already exists.
         */
        ALREADY_EXISTS,
        /**
         * Changes contradict each other, for example editing and deleting one document.
         */
        CONFLICT,
        /**
         * The target is in a state the applier will not change, such as deleting a file open in an editor.
         */
        IN_USE,
        /**
         * A filesystem operation failed while applying or reverting.
         */
        IO_FAILURE
    }
}
