package dev.railroadide.railroad.ide.sst.edit.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.plugin.spi.editor.TextSelection;

import java.util.Objects;

/**
 * Caret or selection requested after a successful edit. Offsets refer to the document's text after the whole
 * workspace edit has been applied. The request is presentation only: it is applied when the document is open
 * in a text editor and ignored otherwise, and it never affects whether the edit succeeds.
 *
 * @param document document whose editor should receive the selection
 * @param selection directed selection in post-edit UTF-16 offsets
 */
public record PostApplySelection(DocumentId document, TextSelection selection) {
    /**
     * Creates a selection request.
     *
     * @param document target document
     * @param selection post-edit selection
     */
    public PostApplySelection {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(selection, "selection");
    }
}
