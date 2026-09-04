package dev.railroadide.railroad.plugin.spi.services;

import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.util.List;
import java.util.Optional;

/**
 * Service to manage the state of the IDE, including the current project,
 * open documents, and active document.
 */
// TODO: Use <? extends Document> for the list of open documents to allow for subclasses of Document
public interface IDEStateService {
    /**
     * Gets the current project in the IDE.
     *
     * @return the current project, or null if no project is open
     */
    Project getCurrentProject();

    /**
     * Gets the list of open documents in the IDE.
     *
     * @return a list of open documents
     */
    List<Document> getOpenDocuments();

    /**
     * Gets the active document in the IDE.
     *
     * @return the active document, or null if no document is active
     */
    Document getActiveDocument();

    /**
     * Sets the current project in the IDE.
     *
     * @param project the project to set as current
     */
    void setCurrentProject(Project project);

    /**
     * Adds a document to the IDE's open-document state.
     *
     * @param document the document to mark as open
     */
    void openDocument(Document document);

    /**
     * Closes the specified document in the IDE.
     *
     * @param document the document to close
     */
    void closeDocument(Document document);

    /**
     * Sets the active document in the IDE.
     *
     * @param document the document to set as active
     */
    void setActiveDocument(Document document);

    /**
     * Sets the list of open documents in the IDE.
     *
     * @param documents the list of documents to set as open
     */
    void setOpenDocuments(List<Document> documents);

    /**
     * Clears the list of open documents in the IDE.
     */
    void clearOpenDocuments();

    /**
     * Gets the timestamp when the project was opened.
     *
     * @return the timestamp in milliseconds since epoch
     */
    long getProjectOpenedTimestamp();

    /**
     * Gets the timestamp when the specified document was opened.
     *
     * @param document the document to get the timestamp for
     * @return the timestamp in milliseconds since epoch, or 0 if the document was not opened
     */
    long getDocumentOpenedTimestamp(Document document);

    /**
     * Resolves the stable logical identity and current URI for a document.
     *
     * @param document document to identify
     * @return stable document identity
     */
    DocumentIdentity identifyDocument(Document document);

    /**
     * Finds an identity previously associated with a physical or virtual URI.
     *
     * @param uri current or registered document URI
     * @return the identity, if registered
     */
    Optional<DocumentIdentity> findDocumentIdentity(DocumentUri uri);

    /**
     * Rebinds an existing logical document after a move or rename.
     *
     * @param identity current document identity
     * @param newUri new physical or virtual address
     * @return updated identity retaining the same stable ID
     */
    DocumentIdentity rebindDocument(DocumentIdentity identity, DocumentUri newUri);

    /**
     * Restores a persisted identity association for the current workspace. If the URI is
     * already known, its live identity wins so duplicate session entries cannot fork it.
     *
     * @param identity persisted document identity
     * @return the live identity associated with the URI
     */
    DocumentIdentity restoreDocumentIdentity(DocumentIdentity identity);
}
