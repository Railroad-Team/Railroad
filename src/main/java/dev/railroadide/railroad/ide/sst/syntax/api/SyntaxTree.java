package dev.railroadide.railroad.ide.sst.syntax.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Entry point for a parsed concrete syntax tree.
 * <p>
 * Most consumers start here, obtain the {@linkplain #root() root node}, and then traverse
 * via {@link SyntaxNode#children()} or helpers such as
 * {@link dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext#traverse}.
 */
public final class SyntaxTree {
    private final TextDocumentSnapshot documentSnapshot;
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
        this(compatibilitySnapshot(documentId, documentUri, documentVersion, root), root);
    }

    /**
     * Creates a syntax tree for an immutable text snapshot.
     *
     * @param documentSnapshot exact source snapshot parsed into the tree
     * @param root root node covering the complete snapshot text
     * @throws NullPointerException if any argument is {@code null}
     */
    public SyntaxTree(TextDocumentSnapshot documentSnapshot, SyntaxNode root) {
        this.documentSnapshot = Objects.requireNonNull(documentSnapshot, "documentSnapshot");
        this.root = Objects.requireNonNull(root, "root");
    }

    /**
     * Returns the immutable source snapshot parsed into this tree.
     *
     * @return text document snapshot
     */
    public TextDocumentSnapshot documentSnapshot() {
        return documentSnapshot;
    }

    /**
     * Returns the stable identity of the logical document represented by this tree.
     *
     * @return document identity
     */
    public DocumentId documentId() {
        return documentSnapshot.id();
    }

    /**
     * Returns the physical or virtual location parsed into this tree.
     *
     * @return document URI
     */
    public DocumentUri documentUri() {
        return documentSnapshot.uri();
    }

    /**
     * Returns the immutable content revision parsed into this tree.
     *
     * @return document version
     */
    public DocumentVersion documentVersion() {
        return documentSnapshot.version();
    }

    /**
     * Returns the root node covering the full source file.
     *
     * @return the root syntax node
     */
    public SyntaxNode root() {
        return root;
    }

    private static TextDocumentSnapshot compatibilitySnapshot(
        DocumentId documentId,
        DocumentUri documentUri,
        DocumentVersion documentVersion,
        SyntaxNode root
    ) {
        return new TextDocumentSnapshot(
            Objects.requireNonNull(documentId, "documentId"),
            Objects.requireNonNull(documentUri, "documentUri"),
            Objects.requireNonNull(documentVersion, "documentVersion"),
            "unknown",
            sourceText(Objects.requireNonNull(root, "root")),
            StandardCharsets.UTF_8
        );
    }

    private static String sourceText(SyntaxNode root) {
        StringBuilder text = new StringBuilder(Math.max(0, root.width()));
        appendSourceText(root, text);
        return text.toString();
    }

    private static void appendSourceText(SyntaxNode node, StringBuilder text) {
        if (node instanceof SyntaxToken token) {
            text.append(token.text());
            return;
        }
        for (SyntaxNode child : node.children())
            appendSourceText(child, text);
    }
}
