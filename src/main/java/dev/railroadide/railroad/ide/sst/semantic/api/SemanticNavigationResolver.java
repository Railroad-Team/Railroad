package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.Optional;

/**
 * Resolves navigation targets within one captured semantic model.
 */
public final class SemanticNavigationResolver {
    private SemanticNavigationResolver() {
    }

    /**
     * Finds the local declaration range for the symbol resolved at an offset.
     *
     * @param model semantic model whose syntax tree defines the navigation scope
     * @param offset zero-based source offset in the captured syntax tree
     * @return declaration range in this model, or an empty optional when unresolved or external
     */
    public static Optional<Location.TextLocation> definitionAt(SemanticModel model, int offset) {
        return symbolAt(model, offset)
            .flatMap(symbol -> declarationLocation(model, symbol));
    }

    /**
     * Finds the resolved symbol at the innermost syntax node containing an offset.
     *
     * @param model semantic model whose syntax tree defines the navigation scope
     * @param offset zero-based source offset in the captured syntax tree
     * @return symbol resolved at the innermost node, or an empty optional when unresolved or outside the tree
     */
    public static Optional<Symbol> symbolAt(SemanticModel model, int offset) {
        SyntaxNode root = model.syntaxTree().root();
        if (offset < root.start() || offset >= root.end())
            return Optional.empty();

        SyntaxNode node = findNodeAt(root, offset);
        return model.resolvedSymbol(node);
    }

    /**
     * Locates a declaration only when it belongs to the supplied model syntax tree.
     *
     * @param model semantic model whose syntax tree defines the navigation scope
     * @param symbol symbol whose declaration is queried
     * @return declaration range, or an empty optional if the declaration does not belong to this model
     */
    public static Optional<Location.TextLocation> declarationLocation(SemanticModel model, Symbol symbol) {
        return symbol.declaration()
            .filter(node -> belongsToTree(node, model.syntaxTree().root()))
            .map(node -> Location.TextLocation.from(
                model.syntaxTree().documentSnapshot(),
                node.start(),
                node.end()));
    }

    private static boolean belongsToTree(SyntaxNode node, SyntaxNode root) {
        SyntaxNode current = node;

        while (current != null) {
            if (current == root)
                return true;

            current = current.parent().orElse(null);
        }

        return false;
    }

    private static SyntaxNode findNodeAt(SyntaxNode node, int offset) {
        for (SyntaxNode child : node.children()) {
            if (offset >= child.start() && offset < child.end())
                return findNodeAt(child, offset);
        }

        return node;
    }
}
