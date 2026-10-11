package dev.railroadide.railroad.ide.debug;

import dev.railroadide.railroad.debug.model.DebugVariable;

/**
 * A watch value or inline evaluation error for the selected frame.
 *
 * @param watch watch whose evaluation this result represents
 * @param value evaluated value, or null when pending or unsuccessful
 * @param error evaluation failure message, or null when pending or successful
 */
public record WatchResult(DebugWatch watch, DebugVariable value, String error) {
}
