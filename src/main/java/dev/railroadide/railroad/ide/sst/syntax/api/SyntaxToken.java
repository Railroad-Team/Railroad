package dev.railroadide.railroad.ide.sst.syntax.api;

import java.util.List;

/**
 * Leaf syntax node representing a concrete token from the source document.
 * <p>
 * Tokens are also {@link SyntaxNode} instances, so inspections can uniformly traverse the
 * tree and then specialise when token text is needed.
 */
public interface SyntaxToken extends SyntaxNode {
    /**
     * Returns the token text exactly as it appeared in the source document.
     *
     * @return the token text
     */
    String text();

    @Override
    default List<SyntaxNode> children() {
        return List.of();
    }
}
