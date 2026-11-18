package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * An action offered for a document range, such as a quick fix for a diagnostic.
 * <p>
 * Computing an action never mutates a document. An action is either <em>resolved</em>, carrying the snapshot-bound
 * {@link WorkspaceEdit} it would apply, or <em>deferred</em>, carrying only what its provider needs to compute the
 * edit later through {@link CodeActionProvider#resolve}. Hosts preview and apply edits through the workspace-edit
 * service, which rejects stale snapshots, overlaps and read-only targets and records the edit as one undo step.
 * <p>
 * An <em>unavailable</em> action explains why a fix that a user might expect is not offered, for example because
 * its target is ambiguous; it is shown but can never be applied.
 *
 * @param title nonblank label displayed for this action
 * @param diagnostics diagnostics of the requested document revision that this action fixes
 * @param preferred whether this action is the most likely fix and should be listed first
 * @param edit changes the action makes, or {@code null} while the action is deferred or unavailable
 * @param data provider-owned immutable data used to resolve a deferred action, or {@code null}
 * @param unavailableReason user-facing reason the action cannot be applied, or {@code null} when it can
 */
public record CodeAction(
    String title,
    List<TextEditorDiagnostic> diagnostics,
    boolean preferred,
    @Nullable WorkspaceEdit edit,
    @Nullable String data,
    @Nullable String unavailableReason
) {
    /**
     * Validates an action and copies its diagnostics.
     *
     * @param title nonblank label displayed for this action
     * @param diagnostics diagnostics this action fixes
     * @param preferred whether this action is the most likely fix
     * @param edit changes the action makes, or {@code null}
     * @param data provider-owned resolution data, or {@code null}
     * @param unavailableReason reason the action cannot be applied, or {@code null}
     * @throws IllegalArgumentException if the title or reason is blank, or an unavailable action carries an edit
     */
    public CodeAction {
        Objects.requireNonNull(title, "title");
        diagnostics = List.copyOf(diagnostics);
        if (title.isBlank())
            throw new IllegalArgumentException("Action title must not be blank");
        if (unavailableReason != null && unavailableReason.isBlank())
            throw new IllegalArgumentException("Unavailable actions need a reason");
        if (unavailableReason != null && edit != null)
            throw new IllegalArgumentException("Unavailable actions cannot carry an edit");
    }

    /**
     * Creates an action whose edit is already computed.
     *
     * @param title nonblank label
     * @param edit changes the action makes
     * @return resolved action
     */
    public static CodeAction resolved(String title, WorkspaceEdit edit) {
        return new CodeAction(title, List.of(), false, Objects.requireNonNull(edit, "edit"), null, null);
    }

    /**
     * Creates an action whose edit is computed only when the action is selected.
     *
     * @param title nonblank label
     * @param data provider-owned immutable data identifying what to compute
     * @return deferred action
     */
    public static CodeAction deferred(String title, String data) {
        return new CodeAction(title, List.of(), false, null, Objects.requireNonNull(data, "data"), null);
    }

    /**
     * Creates an action that is shown but cannot be applied.
     *
     * @param title nonblank label
     * @param reason user-facing reason the action cannot be applied
     * @return unavailable action
     */
    public static CodeAction unavailable(String title, String reason) {
        return new CodeAction(title, List.of(), false, null, null, Objects.requireNonNull(reason, "reason"));
    }

    /**
     * Returns this action marked as fixing the given diagnostics.
     *
     * @param fixed diagnostics of the requested document revision
     * @return copy fixing the diagnostics
     */
    public CodeAction withDiagnostics(List<TextEditorDiagnostic> fixed) {
        return new CodeAction(title, fixed, preferred, edit, data, unavailableReason);
    }

    /**
     * Returns this action marked as the preferred fix.
     *
     * @return preferred copy
     */
    public CodeAction asPreferred() {
        return new CodeAction(title, diagnostics, true, edit, data, unavailableReason);
    }

    /**
     * Returns this action with its computed edit; used by providers when resolving a deferred action.
     *
     * @param resolvedEdit changes the action makes
     * @return resolved copy
     * @throws IllegalArgumentException if this action is unavailable
     */
    public CodeAction withEdit(WorkspaceEdit resolvedEdit) {
        return new CodeAction(title, diagnostics, preferred, Objects.requireNonNull(resolvedEdit, "resolvedEdit"),
            data, unavailableReason);
    }

    /**
     * Returns this action marked as unavailable; used by providers when an action no longer applies at resolution.
     *
     * @param reason user-facing reason the action cannot be applied
     * @return unavailable copy without an edit
     */
    public CodeAction asUnavailable(String reason) {
        return new CodeAction(title, diagnostics, preferred, null, data, Objects.requireNonNull(reason, "reason"));
    }

    /**
     * Reports whether this action can be applied.
     *
     * @return {@code true} unless the action is unavailable
     */
    public boolean available() {
        return unavailableReason == null;
    }

    /**
     * Reports whether this action's edit is computed.
     *
     * @return {@code true} when the action carries an edit
     */
    public boolean isResolved() {
        return edit != null;
    }
}
