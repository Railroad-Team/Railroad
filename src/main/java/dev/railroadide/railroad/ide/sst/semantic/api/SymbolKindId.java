package dev.railroadide.railroad.ide.sst.semantic.api;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Value-based extensible symbol category. Providers own their namespace and vocabulary.
 *
 * @param id nonblank namespaced category
 */
public record SymbolKindId(String id) implements SymbolKind {
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*:[A-Za-z0-9_.-]+");
    private static final Map<String, SymbolKindId> SHARED = new ConcurrentHashMap<>();

    /**
     * Creates a validated provider-owned category identifier.
     *
     * @param id namespaced identifier
     */
    public SymbolKindId {
        Objects.requireNonNull(id, "id");
        if (!VALID_ID.matcher(id).matches())
            throw new IllegalArgumentException("Symbol kinds must be namespaced identifiers");
    }

    /**
     * Converts a provider category into a value with stable equality. Equal identifiers share one instance.
     *
     * @param kind semantic category of the declaration or relation
     * @return value-based category preserving the provider identifier
     */
    public static SymbolKindId from(SymbolKind kind) {
        if (Objects.requireNonNull(kind, "kind") instanceof SymbolKindId id)
            return id;
        return SHARED.computeIfAbsent(kind.id(), SymbolKindId::new);
    }
}
