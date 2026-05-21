package dev.railroadide.railroad.ide.sst.syntax.api;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Identifies the syntactic role of a node or token.
 * <p>
 * Built-in kinds are provided for parser infrastructure. Language-specific kinds
 * can be created via {@link #of(String)}.
 */
public final class SyntaxKind {
    private static final ConcurrentMap<String, SyntaxKind> KINDS = new ConcurrentHashMap<>();

    public static final SyntaxKind ROOT = canonical("ROOT");
    public static final SyntaxKind NODE = canonical("NODE");
    public static final SyntaxKind TOKEN = canonical("TOKEN");
    public static final SyntaxKind MISSING_TOKEN = canonical("MISSING_TOKEN");

    private final String id;

    private SyntaxKind(String id) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.isBlank())
            throw new IllegalArgumentException("id cannot be blank");
    }

    /**
     * Creates a custom syntax kind for language-specific constructs.
     *
     * @param id stable identifier, e.g. {@code JAVA_COMPILATION_UNIT}
     * @return custom syntax kind
     */
    public static SyntaxKind of(String id) {
        return canonical(id);
    }

    private static SyntaxKind canonical(String id) {
        Objects.requireNonNull(id, "id");
        return KINDS.computeIfAbsent(id, SyntaxKind::new);
    }

    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof SyntaxKind that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
