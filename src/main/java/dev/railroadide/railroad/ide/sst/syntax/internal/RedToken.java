package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;

/**
 * Positioned syntax-token view over an immutable green token.
 */
public final class RedToken extends RedElement implements SyntaxToken {
    /**
     * Creates a positioned syntax view over the supplied green element.
     *
     * @param green the immutable backing element
     * @param parent the containing syntax node, or {@code null} for a root
     * @param start the nonnegative absolute UTF-16 start offset
     */
    public RedToken(GreenToken green, RedNode parent, int start) {
        super(green, parent, start);
    }

    @Override
    public String text() {
        return ((GreenToken) green()).text();
    }
}
