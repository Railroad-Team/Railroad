package dev.railroadide.railroad.plugin.spi.state;

/**
 * Represents a text selection in the text editor, defined by a start and end cursor.
 *
 * @param start The starting cursor of the selection.
 * @param end   The ending cursor of the selection.
 */
public record Selection(dev.railroadide.railroad.plugin.spi.state.Cursor start, Cursor end) {
}
