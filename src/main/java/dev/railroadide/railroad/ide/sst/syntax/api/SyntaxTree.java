package dev.railroadide.railroad.ide.sst.syntax.api;

import java.util.Objects;

/**
 * Entry point for a parsed concrete syntax tree.
 * <p>
 * Most consumers start here, obtain the {@linkplain #root() root node}, and then traverse
 * via {@link SyntaxNode#children()} or helpers such as
 * {@link dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext#traverse}.
 */
public final class SyntaxTree {
    private final SyntaxNode root;

    /**
     * Creates a syntax tree with the supplied root node.
     *
     * @param root root node covering the full file
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public SyntaxTree(SyntaxNode root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /**
     * Returns the root node covering the full source file.
     *
     * @return the root syntax node
     */
    public SyntaxNode root() {
        return root;
    }
}
