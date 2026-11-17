package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;

import java.util.Objects;

/**
 * Identifies the symbol whose usages are searched. Searching by {@link SymbolId} lets a result view refresh
 * against a newer workspace without reusing offsets from an outdated document revision.
 */
public sealed interface UsageQuery permits UsageQuery.AtOffset, UsageQuery.OfSymbol {
    /**
     * Searches usages of the symbol resolved at an offset in the request's captured document.
     *
     * @param offset UTF-16 offset in the captured document
     */
    record AtOffset(int offset) implements UsageQuery {
    }

    /**
     * Searches usages of a previously resolved provider-owned symbol identity.
     *
     * @param symbol stable symbol identity
     */
    record OfSymbol(SymbolId symbol) implements UsageQuery {
        /**
         * Creates a query for a stable symbol identity.
         *
         * @param symbol stable symbol identity
         */
        public OfSymbol {
            Objects.requireNonNull(symbol, "symbol");
        }
    }
}
