package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;

import java.util.List;
import java.util.Objects;

/**
 * Complete usages of one symbol in a captured workspace, ordered by document address and offset. Declarations
 * are not usages and are excluded.
 *
 * @param symbol stable identity of the searched symbol, usable to refresh the search later
 * @param displayName user-facing symbol name
 * @param usages ordered usage locations in captured document revisions
 */
public record UsageSearch(SymbolId symbol, String displayName, List<Location<?>> usages) {
    /**
     * Copies the ordered usages and validates the symbol identity and name.
     *
     * @param symbol stable identity of the searched symbol
     * @param displayName user-facing symbol name
     * @param usages ordered usage locations in captured document revisions
     */
    public UsageSearch {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(displayName, "displayName");
        usages = List.copyOf(usages);
    }
}
