package dev.railroadide.railroad.ide.sst.edit.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, snapshot-bound description of changes to several documents and files, applied as one transaction
 * and undone as one step. Computing an edit never mutates the workspace; see the package documentation for
 * validation, ordering, persistence and atomicity guarantees.
 *
 * @param label nonblank name shown for the operation, for example in undo history
 * @param changes ordered changes
 * @param annotations explanations referenced by {@link WorkspaceChange#annotationId()}, in insertion order
 * @param selection caret or selection requested after application, or {@code null}
 */
public record WorkspaceEdit(
    String label,
    List<WorkspaceChange> changes,
    Map<String, ChangeAnnotation> annotations,
    PostApplySelection selection
) {
    /**
     * Creates an edit with immutable copies of its changes and annotations.
     *
     * @param label nonblank operation name
     * @param changes ordered changes
     * @param annotations explanations by key
     * @param selection requested post-apply selection, or {@code null}
     * @throws IllegalArgumentException if the label is blank or a change references an unknown annotation
     */
    public WorkspaceEdit {
        Objects.requireNonNull(label, "label");
        if (label.isBlank())
            throw new IllegalArgumentException("Workspace edit label must not be blank");
        changes = List.copyOf(changes);
        annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        for (Map.Entry<String, ChangeAnnotation> entry : annotations.entrySet()) {
            Objects.requireNonNull(entry.getValue(), "annotation");
            if (entry.getKey() == null || entry.getKey().isBlank())
                throw new IllegalArgumentException("Annotation keys must not be blank");
        }
        for (WorkspaceChange change : changes) {
            String annotationId = change.annotationId();
            if (annotationId != null && !annotations.containsKey(annotationId))
                throw new IllegalArgumentException("Unknown change annotation: " + annotationId);
        }
    }

    /**
     * Starts building an edit.
     *
     * @param label nonblank operation name
     * @return empty builder
     */
    public static Builder builder(String label) {
        return new Builder(label);
    }

    /** Accumulates changes in declaration order. Builders are not thread-safe. */
    public static final class Builder {
        private final String label;
        private final List<WorkspaceChange> changes = new ArrayList<>();
        private final Map<String, ChangeAnnotation> annotations = new LinkedHashMap<>();
        private PostApplySelection selection;

        private Builder(String label) {
            this.label = label;
        }

        /**
         * Registers an annotation that later changes may reference.
         *
         * @param id nonblank annotation key
         * @param annotation explanation
         * @return this builder
         */
        public Builder annotation(String id, ChangeAnnotation annotation) {
            annotations.put(id, annotation);
            return this;
        }

        /**
         * Appends a change.
         *
         * @param change change to append
         * @return this builder
         */
        public Builder change(WorkspaceChange change) {
            changes.add(Objects.requireNonNull(change, "change"));
            return this;
        }

        /**
         * Appends unannotated replacements computed against a text snapshot.
         *
         * @param expected exact snapshot whose offsets the replacements use
         * @param replacements replacements to apply
         * @return this builder
         */
        public Builder replace(TextDocumentSnapshot expected, TextReplacement... replacements) {
            return change(new WorkspaceChange.TextEdit(expected, List.of(replacements)));
        }

        /**
         * Appends creation of a UTF-8 text file.
         *
         * @param uri file location
         * @param text initial text
         * @return this builder
         */
        public Builder createText(DocumentUri uri, String text) {
            return change(WorkspaceChange.CreateFile.text(uri, text));
        }

        /**
         * Appends deletion of a document.
         *
         * @param expected exact snapshot to delete
         * @return this builder
         */
        public Builder delete(DocumentSnapshot expected) {
            return change(new WorkspaceChange.DeleteFile(expected, null));
        }

        /**
         * Appends a rename or move of a document.
         *
         * @param expected exact snapshot to move
         * @param newUri destination file location
         * @return this builder
         */
        public Builder rename(DocumentSnapshot expected, DocumentUri newUri) {
            return change(new WorkspaceChange.RenameFile(expected, newUri, null));
        }

        /**
         * Requests a caret or selection after application.
         *
         * @param document target document
         * @param selection selection in post-edit offsets
         * @return this builder
         */
        public Builder select(DocumentId document, TextSelection selection) {
            this.selection = new PostApplySelection(document, selection);
            return this;
        }

        /**
         * Creates the immutable edit.
         *
         * @return workspace edit
         */
        public WorkspaceEdit build() {
            return new WorkspaceEdit(label, changes, annotations, selection);
        }
    }
}
