package dev.railroadide.railroad.plugin.spi.services;

import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.plugin.spi.dto.Project;

import java.util.List;

/**
 * Service to manage the state of the IDE, including the current project,
 * open documents, and active document.
 */
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
     * Sets the active document in the IDE.
     *
     * @param document the document to set as active
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
}
