package dev.railroadide.railroad.ide.sst.semantic.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value-based extensible symbol category. Providers own their namespace and vocabulary.
 *
 * @param id nonblank namespaced category
 */
public record SymbolKindId(String id) implements SymbolKind {
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*:[A-Za-z0-9_.-]+");

    public SymbolKindId {
        Objects.requireNonNull(id, "id");
        if (!VALID_ID.matcher(id).matches())
            throw new IllegalArgumentException("Symbol kinds must be namespaced identifiers");
    }

    /** Copies an existing provider category into a value with stable equality. */
    public static SymbolKindId from(SymbolKind kind) {
        return new SymbolKindId(Objects.requireNonNull(kind, "kind").id());
    }
}
