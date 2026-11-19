package dev.railroadide.railroad.ide.sst.java.api;

import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import java.util.Locale;

/**
 * Semantic symbol categories used by declaration and resolution passes.
 */
public enum JavaSymbolKind implements SymbolKind {
    /**
     * A Java package declaration.
     */
    PACKAGE,
    /**
     * A Java module declaration.
     */
    MODULE,
    /**
     * An import declaration.
     */
    IMPORT,
    /**
     * A class declaration.
     */
    CLASS,
    /**
     * An interface declaration.
     */
    INTERFACE,
    /**
     * An enum declaration.
     */
    ENUM,
    /**
     * An annotation type declaration.
     */
    ANNOTATION,
    /**
     * A record declaration.
     */
    RECORD,
    /**
     * A field declaration.
     */
    FIELD,
    /**
     * A method declaration.
     */
    METHOD,
    /**
     * A constructor declaration.
     */
    CONSTRUCTOR,
    /**
     * A callable parameter declaration.
     */
    PARAMETER,
    /**
     * A local variable declaration.
     */
    LOCAL_VARIABLE,
    /**
     * A generic type parameter declaration.
     */
    TYPE_PARAMETER,
    /**
     * A symbol whose category is not known.
     */
    UNKNOWN;

    @Override
    public String id() {
        return "java:" + name().toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves a persisted Java symbol category.
     *
     * @param id the namespaced category ID
     * @return the Java category
     */
    public static JavaSymbolKind fromId(String id) {
        for (JavaSymbolKind kind : values()) {
            if (kind.id().equals(id))
                return kind;
        }
        throw new IllegalArgumentException("Unknown Java symbol kind: " + id);
    }
}
