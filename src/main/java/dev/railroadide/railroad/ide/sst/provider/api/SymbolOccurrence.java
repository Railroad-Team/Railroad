package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import java.util.Objects;

/**
 * A named declaration and its versioned text or binary location.
 *
 * @param symbol immutable declaration descriptor
 * @param location versioned text or binary location of the declaration
 */
public record SymbolOccurrence(Symbol symbol, Location<?> location) {
    /**
     * Pairs a non-null declaration with its non-null versioned location.
     *
     * @param symbol immutable declaration descriptor
     * @param location versioned text or binary location of the declaration
     */
    public SymbolOccurrence {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(location, "location");
    }
}
