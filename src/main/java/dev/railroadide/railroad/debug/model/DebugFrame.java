package dev.railroadide.railroad.debug.model;

import dev.railroadide.railroad.debug.source.DebugSource;

/**
 * A source location and display name for a frame in a suspended thread.
 *
 * @param id suspension-scoped frame identifier
 * @param displayName qualified declaring type and method name
 * @param source resolved source, or null when unavailable
 * @param line one-based source line, or a negative value when unavailable
 */
public record DebugFrame(
    DebugFrameId id,
    String displayName,
    DebugSource source,
    int line
) {
}
