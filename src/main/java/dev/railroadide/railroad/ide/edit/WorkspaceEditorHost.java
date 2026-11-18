package dev.railroadide.railroad.ide.edit;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Editor and identity operations a {@link WorkspaceEditService} delegates to the IDE that owns open editors.
 * Methods are called on the JavaFX application thread.
 */
public interface WorkspaceEditorHost {
    /**
     * Returns the live source editor that owns a document's buffer and persistence.
     *
     * @param id document identity
     * @return open, unclosed editor
     */
    Optional<TextEditorPane> openEditor(DocumentId id);

    /**
     * Reports whether any editor tab shows a document, including tabs whose source editor has not been created.
     *
     * @param id document identity
     * @return whether the document is open
     */
    boolean isOpen(DocumentId id);

    /**
     * Moves a document's identity, store location and any open editor to a file already moved on disk.
     *
     * @param identity identity at the previous location
     * @param newPath existing destination file
     */
    void relocate(DocumentIdentity identity, Path newPath);
}
