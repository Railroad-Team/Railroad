/**
 * Language-neutral, snapshot-bound workspace edits shared by code actions, refactorings and other generated changes.
 *
 * <h2>Model</h2>
 * A {@link dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit} is immutable data computed from captured
 * snapshots; computing one never mutates the workspace. Text edits carry the exact
 * {@link dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot} their UTF-16 offsets refer to, and
 * file deletions and renames carry the exact text or binary snapshot they act on. Several text edits may target one
 * document only when they share its expected revision. Change annotations explain changes for a preview; they do
 * not affect application. Binary documents support create, delete and rename; binary content edits are not modelled.
 *
 * <h2>Validation</h2>
 * The applier validates the whole edit before changing anything. A bound document is stale when its current
 * revision, location or content differs from the expected snapshot, or, for a document without an editor or draft,
 * when the file on disk no longer matches. Replacements are rejected when they overlap, share a start offset, or
 * fall outside their document. Targets are rejected when read-only (non-editable editors, unwritable files or
 * directories, and generated, decompiled or attached sources), or when they are files outside the project directory
 * or inside directories Railroad does not manage. Non-file documents can only be edited while open in an editor or
 * held as an unsaved draft. Contradictory changes are rejected: editing and deleting one document, deleting or
 * moving one document twice, creating or moving onto an existing location, and edits computed against different
 * revisions of one document. A location freed by an earlier delete or move in the same edit may be reused. Deleting
 * a document that is open in an editor tab or has an unsaved draft is rejected; close the editor first. A
 * {@link dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditPreview} reports the same
 * validation with full before and after text for a diff UI. A preview is not a reservation: application validates
 * again and may still reject.
 *
 * <h2>Application and persistence</h2>
 * Text edits are applied to each document's content as it was when the edit was computed, before file operations;
 * file operations then run in declaration order. A document's content is changed by its current owner: an open
 * editor receives one undoable change and keeps it unsaved until its normal autosave; an unsaved draft without an
 * editor remains an unsaved draft; any other file is written to disk immediately and republished as backing.
 * Created files are written immediately, and missing parent directories are created. Moves retain document identity,
 * content and version and relocate any open editor. Successful application publishes every change to the project
 * document store, whose observers drive diagnostics, analysis and index refresh. An optional
 * {@link dev.railroadide.railroad.ide.sst.edit.api.PostApplySelection} is applied when the document is open in an
 * editor and ignored otherwise.
 *
 * <h2>Atomicity and rollback</h2>
 * Filesystem steps run first and in-memory publications second, all on the JavaFX thread without interleaving with
 * editor or file discovery publications. Each step is journaled before it runs, so a step that fails part-way is
 * reverted too. If a step fails, the journal is reverted in reverse order: written files are restored, created files
 * and directories removed, deleted files recreated, moved files moved back, and document store, identity and editor
 * changes reverted. Only when every reversal succeeds is the result
 * {@link dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult.Status#ROLLED_BACK}. Editor changes
 * reverted by rollback stay on that editor's redo stack, but redoing them there is refused. The following are not
 * guaranteed:
 * <ul>
 * <li>There is no cross-file filesystem transaction. A crash or power loss part-way leaves the files written so
 * far, and each file is overwritten in place rather than atomically replaced.</li>
 * <li>External processes can change files between validation and writing. Content is re-checked immediately
 * before each write, but a narrow window remains; rollback never overwrites a file that changed after the edit
 * wrote it and reports it instead.</li>
 * <li>Rollback itself can fail, for example when a disk becomes read-only. The result is then
 * {@link dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult.Status#INCONSISTENT} and names every
 * location that may still hold partial changes.</li>
 * <li>Case-only renames on case-insensitive filesystems go through a temporary name, so the file briefly has a
 * third name.</li>
 * <li>Reverted documents receive new versions with their original content. Store observers, file watchers and
 * other threads reading the store may observe intermediate states, since the store publishes one document at a
 * time.</li>
 * </ul>
 *
 * <h2>Undo and redo</h2>
 * Each applied edit is recorded, in a bounded per-project history, as one step that is undone or redone with the
 * same validation, ordering and rollback as application. Undo requires every affected document to hold exactly the
 * content the edit produced, and every created or moved file to be where the edit left it; redo requires the
 * reverse. Otherwise nothing changes and the edit is removed from shared history, since it can no longer be undone
 * or redone as a whole; an edit that failed only because files could not be read or written is kept for retry. An open
 * editor whose own history still has the edit's change next undoes or redoes its part
 * through that history, so its local undo and redo stacks stay aligned. Otherwise, for example when the document was
 * closed during the edit and opened later, its part is applied as a new editor change; undoing that change in the
 * editor then reverses the whole workspace edit again. Each editor's selection from before or after the edit is
 * restored. Requesting undo or redo in an editor whose next step belongs to a workspace edit undoes or redoes the
 * whole edit. If that is not possible, nothing changes, the edit is removed from shared history and a warning is
 * logged; repeating the request then undoes only that editor. Undo history does not survive closing the project. The
 * oldest entries are discarded beyond the history limit, after which an editor's undo reverts only that editor's part
 * of a discarded edit.
 */
package dev.railroadide.railroad.ide.sst.edit.api;
