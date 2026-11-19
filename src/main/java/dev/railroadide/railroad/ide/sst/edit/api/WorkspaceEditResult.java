package dev.railroadide.railroad.ide.sst.edit.api;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of applying, undoing or redoing a workspace edit.
 *
 * @param status overall outcome
 * @param problems preflight problems, the failure that triggered rollback, and any step that could not be
 *            reverted; empty when the edit was applied
 */
public record WorkspaceEditResult(Status status, List<WorkspaceEditProblem> problems) {
    /**
     * Creates a result with an immutable copy of its problems.
     *
     * @param status overall outcome
     * @param problems problems explaining a non-applied outcome
     */
    public WorkspaceEditResult {
        Objects.requireNonNull(status, "status");
        problems = List.copyOf(problems);
    }

    /**
     * Reports whether every change was applied.
     *
     * @return {@code true} for {@link Status#APPLIED}
     */
    public boolean applied() {
        return status == Status.APPLIED;
    }

    /** Overall outcome of an application attempt. */
    public enum Status {
        /** Every change was applied and published. */
        APPLIED,
        /** Validation failed before anything was changed. */
        REJECTED,
        /**
         * A step failed after mutation began and every completed step was reverted. Reverted documents hold their
         * original content under new versions; observers may have seen the intermediate state.
         */
        ROLLED_BACK,
        /**
         * A step failed and at least one completed step could not be reverted. The problems name every location
         * that may still hold partially applied changes.
         */
        INCONSISTENT
    }
}
