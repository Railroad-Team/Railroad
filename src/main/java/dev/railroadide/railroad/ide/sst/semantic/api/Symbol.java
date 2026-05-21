package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.Optional;

/**
 * Public semantic symbol contract.
 * <p>
 * A symbol represents something that can be declared and/or referenced in Java source,
 * such as a type, field, method, parameter, or local variable.
 */
public interface Symbol {
    /**
     * Returns the broad symbol category.
     *
     * @return the symbol kind
     */
    SymbolKind kind();

    /**
     * Returns the simple source-level name of the symbol.
     *
     * @return the simple symbol name
     */
    String simpleName();

    /**
     * Returns the fully qualified name when one exists.
     *
     * @return the qualified name, if available
     */
    Optional<String> qualifiedName();

    /**
     * Returns the syntax node that declared this symbol when it came from source.
     *
     * @return the declaration node, if this symbol came from source
     */
    Optional<SyntaxNode> declaration();
}
