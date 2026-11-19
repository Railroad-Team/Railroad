package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * Internal bridge for constructing public {@link SyntaxTree} instances from
 * green/red implementation types.
 */
@ApiStatus.Internal
public final class SyntaxInternalFactory {
    private SyntaxInternalFactory() {
    }

    /**
     * Creates an immutable token, treating null text as empty.
     *
     * @param kind the token's syntax kind
     * @param text the token text, or {@code null} for empty text
     * @return the new green token
     */
    public static GreenToken greenToken(SyntaxKind kind, String text) {
        return new GreenToken(kind, text);
    }

    /**
     * Creates a green node by copying its ordered children and summing their widths.
     *
     * @param kind the new node's syntax kind
     * @param children the child elements in source order
     * @return the new green node
     */
    public static GreenNode greenNode(SyntaxKind kind, List<? extends GreenElement> children) {
        return greenNode(kind, children, () -> {
        });
    }

    /**
     * Creates an immutable green node with cooperative activity checks.
     * The callback's work budget is polled before construction, including for an empty
     * node, and during width calculation. The callback is not retained by the
     * completed node. Callback failures propagate without returning a partial node.
     *
     * @param kind the new node's syntax kind
     * @param children child elements in source order, copied by the node
     * @param activityCheck cooperative activity check, invoked on the constructing thread
     * @return the completed immutable green node
     * @throws NullPointerException if the kind, children, callback, or any child is {@code null}
     */
    public static GreenNode greenNode(SyntaxKind kind, List<? extends GreenElement> children, Runnable activityCheck) {
        Objects.requireNonNull(children, "children");
        Objects.requireNonNull(activityCheck, "activityCheck");
        ActivityCheckpoint.poll(activityCheck);
        return new GreenNode(kind, children, activityCheck);
    }

    /**
     * Creates and validates a syntax tree around a generic root containing the supplied children.
     *
     * @param children the immutable elements to place under the root, in source order
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromRootChildren(List<? extends GreenElement> children) {
        return treeFromRootChildren(DocumentId.create(), children);
    }

    /**
     * Creates and validates a syntax tree around a generic root containing the supplied children.
     *
     * @param documentId the stable identity associated with the syntax tree
     * @param children the immutable elements to place under the root, in source order
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromRootChildren(DocumentId documentId, List<? extends GreenElement> children) {
        return treeFromRootChildren(documentId, DocumentUri.inMemory(documentId), children);
    }

    /**
     * Creates and validates a syntax tree around a generic root containing the supplied children.
     *
     * @param documentId the stable identity associated with the syntax tree
     * @param documentUri the current address associated with the syntax tree
     * @param children the immutable elements to place under the root, in source order
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromRootChildren(
        DocumentId documentId,
        DocumentUri documentUri,
        List<? extends GreenElement> children
    ) {
        return treeFromRootChildren(documentId, documentUri, DocumentVersion.initial(), children);
    }

    /**
     * Creates and validates a syntax tree around a generic root containing the supplied children.
     *
     * @param documentId the stable identity associated with the syntax tree
     * @param documentUri the current address associated with the syntax tree
     * @param documentVersion the content revision associated with the syntax tree
     * @param children the immutable elements to place under the root, in source order
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromRootChildren(
        DocumentId documentId,
        DocumentUri documentUri,
        DocumentVersion documentVersion,
        List<? extends GreenElement> children
    ) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(documentUri, "documentUri");
        Objects.requireNonNull(documentVersion, "documentVersion");
        Objects.requireNonNull(children, "children");
        GreenNode rootGreen = GreenNode.root(children);
        var tree = new SyntaxTree(documentId, documentUri, documentVersion, RedFactory.root(rootGreen));
        SyntaxTreeValidator.validate(tree.root());
        return tree;
    }

    /**
     * Creates and validates a syntax tree around a generic root containing the supplied children.
     *
     * @param documentSnapshot the document snapshot associated with the syntax tree
     * @param children the immutable elements to place under the root, in source order
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromRootChildren(
        TextDocumentSnapshot documentSnapshot,
        List<? extends GreenElement> children
    ) {
        Objects.requireNonNull(documentSnapshot, "documentSnapshot");
        Objects.requireNonNull(children, "children");
        GreenNode rootGreen = GreenNode.root(children);
        var tree = new SyntaxTree(documentSnapshot, RedFactory.root(rootGreen));
        SyntaxTreeValidator.validate(tree.root());
        return tree;
    }

    /**
     * Creates and validates a syntax tree around the supplied immutable green root.
     *
     * @param root the immutable green root to wrap
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromGreenRoot(GreenNode root) {
        return treeFromGreenRoot(DocumentId.create(), root);
    }

    /**
     * Creates and validates a syntax tree around the supplied immutable green root.
     *
     * @param documentId the stable identity associated with the syntax tree
     * @param root the immutable green root to wrap
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromGreenRoot(DocumentId documentId, GreenNode root) {
        return treeFromGreenRoot(documentId, DocumentUri.inMemory(documentId), root);
    }

    /**
     * Creates and validates a syntax tree around the supplied immutable green root.
     *
     * @param documentId the stable identity associated with the syntax tree
     * @param documentUri the current address associated with the syntax tree
     * @param root the immutable green root to wrap
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromGreenRoot(DocumentId documentId, DocumentUri documentUri, GreenNode root) {
        return treeFromGreenRoot(documentId, documentUri, DocumentVersion.initial(), root);
    }

    /**
     * Creates and validates a syntax tree around the supplied immutable green root.
     *
     * @param documentId the stable identity associated with the syntax tree
     * @param documentUri the current address associated with the syntax tree
     * @param documentVersion the content revision associated with the syntax tree
     * @param root the immutable green root to wrap
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromGreenRoot(
        DocumentId documentId,
        DocumentUri documentUri,
        DocumentVersion documentVersion,
        GreenNode root
    ) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(documentUri, "documentUri");
        Objects.requireNonNull(documentVersion, "documentVersion");
        var tree = new SyntaxTree(
            documentId,
            documentUri,
            documentVersion,
            RedFactory.root(Objects.requireNonNull(root, "root")));
        SyntaxTreeValidator.validate(tree.root());
        return tree;
    }

    /**
     * Creates and validates a syntax tree around the supplied immutable green root.
     *
     * @param documentSnapshot the document snapshot associated with the syntax tree
     * @param root the immutable green root to wrap
     * @return the validated syntax tree
     */
    public static SyntaxTree treeFromGreenRoot(TextDocumentSnapshot documentSnapshot, GreenNode root) {
        return treeFromGreenRoot(documentSnapshot, root, () -> {
        });
    }

    /**
     * Creates a tree around the exact captured document and validates it cooperatively.
     * Checks run before construction, during validation and internal child construction,
     * and before returning. Callback failures propagate without returning a partial tree.
     * Neither the completed tree nor its child caches retain the callback.
     *
     * @param documentSnapshot exact document snapshot represented by the tree
     * @param root immutable green root to wrap
     * @param activityCheck request activity check, invoked on the constructing thread
     * @return completed and validated syntax tree
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalStateException if the tree's ranges or parent links are inconsistent
     */
    public static SyntaxTree treeFromGreenRoot(
        TextDocumentSnapshot documentSnapshot,
        GreenNode root,
        Runnable activityCheck
    ) {
        Objects.requireNonNull(documentSnapshot, "documentSnapshot");
        Objects.requireNonNull(activityCheck, "activityCheck");
        activityCheck.run();
        var tree = new SyntaxTree(
            documentSnapshot,
            RedFactory.root(Objects.requireNonNull(root, "root")));
        SyntaxTreeValidator.validate(tree.root(), activityCheck);
        activityCheck.run();
        return tree;
    }

    /**
     * Retrieves the green node backing a syntax tree's root.
     *
     * @param tree the syntax tree backed by internal red nodes
     * @return the backing green root
     */
    public static GreenNode greenRoot(SyntaxTree tree) {
        Objects.requireNonNull(tree, "tree");
        return greenNode(tree.root());
    }

    /**
     * Retrieves the green node backing a positioned syntax node.
     *
     * @param node the positioned node backed by a green node
     * @return the green node
     * @throws IllegalArgumentException if the node is a token or is not backed by an internal red element
     */
    public static GreenNode greenNode(SyntaxNode node) {
        GreenElement element = greenElement(node);
        if (element instanceof GreenNode greenNode)
            return greenNode;

        throw new IllegalArgumentException("syntax node is not backed by a GreenNode: " + node.kind().id());
    }

    /**
     * Retrieves the immutable element backing an internal positioned syntax view.
     *
     * @param node the internal red syntax view
     * @return the backing green node or token
     * @throws IllegalArgumentException if the node is not backed by an internal red element
     */
    public static GreenElement greenElement(SyntaxNode node) {
        Objects.requireNonNull(node, "node");
        if (node instanceof RedElement redElement)
            return redElement.green();

        throw new IllegalArgumentException(
            "syntax node is not backed by internal red element: " + node.getClass().getName());
    }

    /**
     * Reconstructs the source text by concatenating green tokens in child order.
     *
     * @param element the green subtree to render
     * @return the complete text of the green subtree
     */
    public static String sourceText(GreenElement element) {
        return sourceText(element, () -> {
        });
    }

    /**
     * Reconstructs source with cooperative checks while traversing the green tree.
     *
     * @param element green subtree to render
     * @param activityCheck parse owner's activity check
     * @return complete subtree text
     */
    public static String sourceText(GreenElement element, Runnable activityCheck) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(activityCheck, "activityCheck").run();
        var text = new StringBuilder(element.width());
        appendSourceText(element, text, activityCheck);
        activityCheck.run();
        return text.toString();
    }

    private static void appendSourceText(GreenElement element, StringBuilder text, Runnable activityCheck) {
        ActivityCheckpoint.poll(activityCheck);
        if (element instanceof GreenToken token) {
            text.append(token.text());
            return;
        }

        GreenNode node = (GreenNode) element;
        for (GreenElement child : node.children()) {
            appendSourceText(child, text, activityCheck);
        }
    }
}
