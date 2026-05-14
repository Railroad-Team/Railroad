package dev.railroadide.railroad.plugin.spi.dto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Represents a document in the Railroad plugin API.
 * A document can be a file or any other content that has a name, path, and content.
 */
public interface Document {
    /**
     * Gets the name of the document.
     *
     * @return the name of the document
     */
    String getName();

    /**
     * Gets the path of the document.
     *
     * @return the path of the document
     */
    Path getPath();

    /**
     * Gets the content of the document as a byte array.
     *
     * @return the content of the document
     */
    byte[] getContent();

    /**
     * Gets the content of the document as a string using UTF-8 encoding.
     *
     * @return the content of the document as a string
     */
    default String getContentAsString() {
        return new String(getContent(), StandardCharsets.UTF_8);
    }

    /**
     * Gets the number of lines in the document.
     *
     * @return the number of lines in the document
     */
    long getLineCount();

    /**
     * Gets the language ID of the document.
     *
     * @return the language ID of the document
     */
    String getLanguageId();

    /**
     * Checks if the document has unsaved changes.
     *
     * @return true if the document is dirty, false otherwise
     */
    boolean isDirty();
}
