package dev.railroadide.railroad.plugin.spi.state;

/**
 * Represents the position of a cursor in the text editor.
 *
 * @param line   The line number of the cursor (0-based).
 * @param column The column number of the cursor (0-based).
 */
public record Cursor(int line, int column) {
}
