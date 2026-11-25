package dev.railroadide.railroad.ide.sst.edit.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.TextRange;

import java.util.List;
import java.util.Objects;

/**
 * Validated, side-effect-free view of what applying an edit would change, suitable for a diff UI. A preview
 * describes the workspace at the moment it was computed; application validates again and may still reject.
 *
 * @param label operation name
 * @param documents text documents whose content would change, in first-change order
 * @param resources file operations in application order
 * @param problems validation problems; application is only possible when this is empty
 */
public record WorkspaceEditPreview(
    String label,
    List<DocumentPreview> documents,
    List<ResourcePreview> resources,
    List<WorkspaceEditProblem> problems
) {
    /**
     * Creates a preview with immutable copies of its lists.
     *
     * @param label operation name
     * @param documents changed text documents
     * @param resources file operations
     * @param problems validation problems
     */
    public WorkspaceEditPreview {
        Objects.requireNonNull(label, "label");
        documents = List.copyOf(documents);
        resources = List.copyOf(resources);
        problems = List.copyOf(problems);
    }

    /**
     * Reports whether validation found no problems.
     *
     * @return {@code true} when the edit was applicable when previewed
     */
    public boolean applicable() {
        return problems.isEmpty();
    }

    /**
     * Where a text change would be applied and how it would be persisted.
     */
    public enum Target {
        /**
         * An open editor's buffer: one undoable editor change, persisted by the editor's normal autosave.
         */
        EDITOR,
        /**
         * An unsaved draft retained without an editor: it stays unsaved and is restored when reopened.
         */
        DRAFT,
        /**
         * A file with no editor or draft: written to disk during application.
         */
        DISK
    }

    /**
     * Full before and after text of one changed document.
     *
     * @param document logical document identity
     * @param uri location before any rename in the same edit
     * @param target where the change would be applied
     * @param before current text
     * @param after resulting text
     * @param changes individual replacements in document order
     */
    public record DocumentPreview(
        DocumentId document,
        DocumentUri uri,
        Target target,
        String before,
        String after,
        List<ChangePreview> changes
    ) {
        /**
         * Creates a document preview with an immutable copy of its changes.
         *
         * @param document logical identity
         * @param uri location before renames
         * @param target application target
         * @param before current text
         * @param after resulting text
         * @param changes individual replacements
         */
        public DocumentPreview {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(uri, "uri");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            changes = List.copyOf(changes);
        }
    }

    /**
     * One replacement mapped into both texts.
     *
     * @param before replaced range in the current text
     * @param after inserted range in the resulting text
     * @param replacement inserted text
     * @param annotationId annotation key, or {@code null}
     */
    public record ChangePreview(TextRange before, TextRange after, String replacement, String annotationId) {
        /**
         * Creates a replacement preview.
         *
         * @param before replaced range in the current text
         * @param after inserted range in the resulting text
         * @param replacement inserted text
         * @param annotationId annotation key, or {@code null}
         */
        public ChangePreview {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            Objects.requireNonNull(replacement, "replacement");
        }
    }

    /**
     * One file operation.
     *
     * @param kind operation
     * @param source existing location for delete and rename, otherwise {@code null}
     * @param target new location for create and rename, otherwise {@code null}
     * @param annotationId annotation key, or {@code null}
     */
    public record ResourcePreview(Kind kind, DocumentUri source, DocumentUri target, String annotationId) {
        /**
         * Creates a file-operation preview.
         *
         * @param kind operation
         * @param source existing location, or {@code null}
         * @param target new location, or {@code null}
         * @param annotationId annotation key, or {@code null}
         */
        public ResourcePreview {
            Objects.requireNonNull(kind, "kind");
        }

        /**
         * File operation kinds.
         */
        public enum Kind {
            /**
             * A new file is created.
             */
            CREATE,
            /**
             * An existing file is deleted.
             */
            DELETE,
            /**
             * An existing file is renamed or moved.
             */
            RENAME
        }
    }
}
