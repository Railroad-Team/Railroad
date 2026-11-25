package dev.railroadide.railroad.ide.sst.edit.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One step of a {@link WorkspaceEdit}. Every step that targets an existing document is bound to the exact
 * snapshot it was computed against; application rejects the whole edit if any bound document has changed.
 */
public sealed interface WorkspaceChange permits WorkspaceChange.TextEdit, WorkspaceChange.CreateFile,
    WorkspaceChange.DeleteFile, WorkspaceChange.RenameFile {
    /**
     * Returns the annotation explaining why this change is required.
     *
     * @return key into {@link WorkspaceEdit#annotations()}, or {@code null} when unannotated
     */
    String annotationId();

    /**
     * Replaces text in one document. Several text edits may target the same document only when they share the
     * same expected revision; their replacements are then validated together for overlaps.
     *
     * @param expected exact snapshot whose UTF-16 offsets the replacements use
     * @param replacements replacements in any order; overlaps and duplicate start offsets are rejected
     * @param annotationId annotation key, or {@code null}
     */
    record TextEdit(TextDocumentSnapshot expected, List<TextReplacement> replacements, String annotationId)
        implements WorkspaceChange {
        /**
         * Creates a text edit with an immutable copy of its replacements.
         *
         * @param expected exact snapshot whose offsets the replacements use
         * @param replacements replacements to apply
         * @param annotationId annotation key, or {@code null}
         * @throws NullPointerException if the snapshot, list or an element is {@code null}
         */
        public TextEdit {
            Objects.requireNonNull(expected, "expected");
            replacements = List.copyOf(replacements);
        }

        /**
         * Creates an unannotated text edit.
         *
         * @param expected exact snapshot whose offsets the replacements use
         * @param replacements replacements to apply
         */
        public TextEdit(TextDocumentSnapshot expected, List<TextReplacement> replacements) {
            this(expected, replacements, null);
        }
    }

    /**
     * Creates a new file with initial content. The file must not exist when the edit is applied; missing
     * parent directories are created and removed again if the edit is rolled back or undone.
     *
     * @param uri file location of the new document
     * @param content initial bytes, copied on construction and on access
     * @param annotationId annotation key, or {@code null}
     */
    record CreateFile(DocumentUri uri, byte[] content, String annotationId) implements WorkspaceChange {
        /**
         * Creates a file-creation step.
         *
         * @param uri file location of the new document
         * @param content initial bytes
         * @param annotationId annotation key, or {@code null}
         * @throws IllegalArgumentException if the URI is not a file URI
         */
        public CreateFile {
            Objects.requireNonNull(uri, "uri");
            if (!uri.isFile())
                throw new IllegalArgumentException("Only file documents can be created: " + uri);

            content = Arrays.copyOf(Objects.requireNonNull(content, "content"), content.length);
        }

        /**
         * Creates an unannotated UTF-8 text file.
         *
         * @param uri file location of the new document
         * @param text initial text, encoded as UTF-8
         * @return creation step
         */
        public static CreateFile text(DocumentUri uri, String text) {
            return new CreateFile(uri, text.getBytes(StandardCharsets.UTF_8), null);
        }

        @Override
        public byte[] content() {
            return Arrays.copyOf(content, content.length);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CreateFile create && uri.equals(create.uri)
                && Arrays.equals(content, create.content) && Objects.equals(annotationId, create.annotationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uri, Arrays.hashCode(content), annotationId);
        }

        @Override
        public String toString() {
            return "CreateFile[uri=" + uri + ", bytes=" + content.length + ", annotationId=" + annotationId + "]";
        }
    }

    /**
     * Deletes a file document whose current revision and on-disk content still match the expected snapshot.
     *
     * @param expected exact text or binary snapshot that will be deleted
     * @param annotationId annotation key, or {@code null}
     */
    record DeleteFile(DocumentSnapshot expected, String annotationId) implements WorkspaceChange {
        /**
         * Creates a deletion step.
         *
         * @param expected exact snapshot that will be deleted
         * @param annotationId annotation key, or {@code null}
         */
        public DeleteFile {
            Objects.requireNonNull(expected, "expected");
        }
    }

    /**
     * Moves or renames a file document, retaining its logical identity, content and version. Text edits in the
     * same workspace edit that target this document use the expected snapshot's offsets and are applied to its
     * content before the move.
     *
     * @param expected exact text or binary snapshot that will be moved
     * @param newUri destination file location, which must not exist
     * @param annotationId annotation key, or {@code null}
     */
    record RenameFile(DocumentSnapshot expected, DocumentUri newUri, String annotationId) implements WorkspaceChange {
        /**
         * Creates a rename or move step.
         *
         * @param expected exact snapshot that will be moved
         * @param newUri destination file location
         * @param annotationId annotation key, or {@code null}
         * @throws IllegalArgumentException if the destination is not a file URI
         */
        public RenameFile {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(newUri, "newUri");
            if (!newUri.isFile())
                throw new IllegalArgumentException("Documents can only be moved to file locations: " + newUri);
        }
    }
}
