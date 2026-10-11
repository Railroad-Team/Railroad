package dev.railroadide.railroad.debug.breakpoint;

import dev.railroadide.railroad.debug.source.DebugSource;

import java.util.UUID;

/**
 * An immutable source breakpoint definition with identity preserved when its line moves.
 *
 * @param id stable breakpoint identifier
 * @param source source containing the breakpoint
 * @param line one-based source line
 * @param enabled whether the session should install a runtime request
 */
public record SourceBreakpoint(
    UUID id,
    DebugSource source,
    int line,
    boolean enabled
) {
}
