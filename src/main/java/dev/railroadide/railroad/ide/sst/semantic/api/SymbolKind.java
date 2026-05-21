package dev.railroadide.railroad.ide.sst.semantic.api;

/**
 * Semantic symbol categories used by declaration and resolution passes.
 */
public enum SymbolKind {
    PACKAGE,
    MODULE,
    IMPORT,
    CLASS,
    INTERFACE,
    ENUM,
    ANNOTATION,
    RECORD,
    FIELD,
    METHOD,
    CONSTRUCTOR,
    PARAMETER,
    LOCAL_VARIABLE,
    TYPE_PARAMETER,
    UNKNOWN
}
