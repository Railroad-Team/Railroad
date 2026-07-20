package dev.railroadide.railroad.ide.sst.syntax.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;

import java.util.Objects;

/**
 * Entry point for a parsed concrete syntax tree.
 * <p>
 * Most consumers start here, obtain the {@linkplain #root() root node}, and then traverse
 * via {@link SyntaxNode#children()} or helpers such as
 * {@link dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext#traverse}.
 */
public final class SyntaxTree {
    private final DocumentId documentId;
    private final SyntaxNode root;

    /**
     * Creates a syntax tree with the supplied root node and a fresh anonymous document
     * identity. Prefer {@link #SyntaxTree(DocumentId, SyntaxNode)} when the caller owns a
     * stable identity for the parsed document.
     *
     * @param root root node covering the full document
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public SyntaxTree(SyntaxNode root) {
        this(DocumentId.create(), root);
    }

    /**
     * Creates a syntax tree for a known logical document.
     *
     * @param documentId stable identity of the parsed document
     * @param root root node covering the full document
     * @throws NullPointerException if any argument is {@code null}
     */
    public SyntaxTree(DocumentId documentId, SyntaxNode root) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.root = Objects.requireNonNull(root, "root");
    }

    /**
     * Returns the stable identity of the logical document represented by this tree.
     *
     * @return document identity
     */
    public DocumentId documentId() {
        return documentId;
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
