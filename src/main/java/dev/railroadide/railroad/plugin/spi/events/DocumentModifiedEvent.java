package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.dto.Document;

import java.util.List;

/**
 * Represents an event that is triggered when a file is modified in the Railroad IDE.
 * This event contains the file that was modified and a list of changes made to it.
 */
public record DocumentModifiedEvent(Document file, List<Change> changes) implements GenericDocumentEvent {
    /**
     * Constructs a new FileModifiedEvent.
     *
     * @param file    The file associated with this event. Must not be null.
     * @param changes The list of changes made to the file. Must not be null or empty.
     * @throws IllegalArgumentException if file is null or changes is null or empty.
     */
    public DocumentModifiedEvent {
        if (file == null)
            throw new IllegalArgumentException("file cannot be null");

        if (changes == null || changes.isEmpty())
            throw new IllegalArgumentException("changes cannot be null or empty");
    }

    /**
     * Represents a change made to a file.
     * Each change has a type (added, removed, modified), the old content, the new content,
     * and the range in the file where the change occurred.
     */
    public record Change(Type type, String oldContent, String newContent, Range range) {
        /**
         * Constructs a new Change.
         *
         * @param type        The type of change (added, removed, modified). Must not be null.
         * @param oldContent  The content before the change. Must not be null.
         * @param newContent  The content after the change. Must not be null.
         * @param range       The range in the file where the change occurred. Must not be null.
         * @throws IllegalArgumentException if type, oldContent, newContent, or range is null.
         */
        public Change {
            if (type == null)
                throw new IllegalArgumentException("type cannot be null");

            if (oldContent == null)
                throw new IllegalArgumentException("oldContent cannot be null");

            if (newContent == null)
                throw new IllegalArgumentException("newContent cannot be null");

            if (range == null)
                throw new IllegalArgumentException("range cannot be null");
        }

        /**
         * Represents the type of change made to a file.
         */
        public enum Type {
            /**
             * Indicates that content was added to the file.
             */
            ADDED,
            /**
             * Indicates that content was removed from the file.
             */
            REMOVED,
            /**
             * Indicates that content in the file was modified.
             */
            MODIFIED
        }
    }

    /**
     * Represents a range in the file where a change occurred.
     * The range is defined by start and end lines and columns.
     */
    public record Range(int startLine, int endLine, int startColumn, int endColumn) {
        /**
         * Constructs a new Range.
         *
         * @param startLine   The starting line of the range (0-based).
         * @param endLine     The ending line of the range (0-based).
         * @param startColumn The starting column of the range (0-based).
         * @param endColumn   The ending column of the range (0-based).
         * @throws IllegalArgumentException if any of the indices are negative.
         */
        public Range {
            if (startLine < 0 || endLine < 0 || startColumn < 0 || endColumn < 0) {
                throw new IllegalArgumentException("Line and column indices must be non-negative");
            }
        }
    }
}
