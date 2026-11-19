package dev.railroadide.railroad.ide.sst.semantic.api;

/**
 * An open, language-defined symbol category. Implementations must be immutable.
 * Use a stable namespaced ID and a finite vocabulary; core consumers cannot assume Java kinds.
 */
@FunctionalInterface
public interface SymbolKind {
    /**
     * Identifies this symbol category across provider boundaries.
     *
     * @return the namespaced category identifier
     */
    String id();
}
