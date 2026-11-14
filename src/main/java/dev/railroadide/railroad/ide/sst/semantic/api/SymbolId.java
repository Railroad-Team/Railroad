package dev.railroadide.railroad.ide.sst.semantic.api;

import java.util.Objects;

/**
 * Immutable provider-owned identity, independent of location, origin, revision and syntax nodes.
 * The namespace before the first colon belongs to the provider. Consumers compare the complete value;
 * they must not parse it or infer availability from it. IDs identify declarations within a compilation
 * context, so separate workspaces may contain different declarations with the same ID.
 *
 * @param value nonblank namespaced canonical identity
 */
public record SymbolId(String value) implements Comparable<SymbolId> {
    public SymbolId {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || value.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("Symbol IDs require a namespace and a whitespace-free value");
    }

    @Override
    public int compareTo(SymbolId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
