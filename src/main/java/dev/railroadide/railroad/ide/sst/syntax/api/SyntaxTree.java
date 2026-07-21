package dev.railroadide.railroad.ide.sst.syntax.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;

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
    private final DocumentUri documentUri;
    private final DocumentVersion documentVersion;
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
        this(documentId, DocumentUri.inMemory(documentId), root);
    }

    /**
     * Creates a syntax tree for a known logical document and current location.
     *
     * @param documentId stable identity of the parsed document
     * @param documentUri location of the parsed document
     * @param root root node covering the full document
     * @throws NullPointerException if any argument is {@code null}
     */
    public SyntaxTree(DocumentId documentId, DocumentUri documentUri, SyntaxNode root) {
        this(documentId, documentUri, DocumentVersion.initial(), root);
    }

    /**
     * Creates a syntax tree for a specific immutable document revision.
     *
     * @param documentId stable identity of the parsed document
     * @param documentUri location of the parsed document
     * @param documentVersion content revision parsed into this tree
     * @param root root node covering the full document
     * @throws NullPointerException if any argument is {@code null}
     */
    public SyntaxTree(
        DocumentId documentId,
        DocumentUri documentUri,
        DocumentVersion documentVersion,
        SyntaxNode root
    ) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.documentUri = Objects.requireNonNull(documentUri, "documentUri");
        this.documentVersion = Objects.requireNonNull(documentVersion, "documentVersion");
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
     * Returns the physical or virtual location parsed into this tree.
     *
     * @return document URI
     */
    public DocumentUri documentUri() {
        return documentUri;
    }

    /**
     * Returns the immutable content revision parsed into this tree.
     *
     * @return document version
     */
    public DocumentVersion documentVersion() {
        return documentVersion;
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
