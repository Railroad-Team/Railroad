package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.Optional;

/**
 * Public semantic symbol contract.
 * <p>
 * A symbol represents a language-defined entity that can be declared or referenced.
 * Source syntax is optional, so binary and synthetic symbols need no placeholder tree.
 * Implementations supplied to published semantic models or scopes must be deeply
 * immutable and safe for concurrent reads, including any attached syntax node.
 * Models retain provider instances and snapshot-local syntax attachments. Cross-document consumers
 * use {@link #id()} rather than Java object identity; a model does not clone provider implementations.
 */
public interface Symbol {
    /**
     * Returns the durable identity supplied by the language provider. Symbols without an ID are
     * snapshot-local and must not be used as cross-document graph keys.
     *
     * @return stable identity when this declaration has been identified
     */
    default Optional<SymbolId> id() {
        return Optional.empty();
    }

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
