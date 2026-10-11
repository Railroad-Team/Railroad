package dev.railroadide.railroad.ide.debug;

import java.util.UUID;

/**
 * A project watch expression, retained across debug sessions.
 *
 * @param id stable watch identifier
 * @param expression expression to evaluate in the selected frame
 */
public record DebugWatch(UUID id, String expression) {
}
