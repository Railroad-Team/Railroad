package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysisProvider;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.ReferenceKindId;
import dev.railroadide.railroad.ide.sst.graph.api.ReferenceOccurrence;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.graph.api.WorkspaceSymbolGraph;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    public static Optional<DefinitionTarget> definitionOf(
        WorkspaceSymbolGraph graph,
        WorkspaceSnapshot workspace,
        SymbolId symbolId
    ) {
        Set<DefinitionTarget> matches = new LinkedHashSet<>();

        for (SymbolDescriptor symbol : graph.symbols(symbolId)) {
            for (SymbolLocation source : symbol.locations()) {
                if (!source.navigable() || !source.uri().isFile())
                    continue;

                Location<?> location = source.selection() != null
                    ? source.selection()
                    : source.declaration();

                if (!(location instanceof Location.TextLocation textLocation))
                    continue;

                DocumentSnapshot document = workspace.document(textLocation.documentId())
                    .orElse(null);
                if (!(document instanceof TextDocumentSnapshot snapshot))
                    continue;

                if (!snapshot.uri().equals(source.uri()))
                    continue;

                try {
                    textLocation.validateAgainst(snapshot);
                } catch (IllegalArgumentException | IndexOutOfBoundsException _) {
                    continue;
                }

                matches.add(new DefinitionTarget(snapshot, textLocation));
            }
        }

        return matches.size() == 1
            ? Optional.of(matches.iterator().next())
            : Optional.empty();
    }

    public static Optional<DefinitionTarget> definitionAt(
        WorkspaceSymbolGraph graph,
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot source,
        int offset
    ) {
        return symbolAt(graph, source, offset)
            .flatMap(id -> definitionOf(graph, workspace, id));
    }

    public static Optional<SymbolId> symbolAt(
        DocumentStore store,
        JavaWorkspaceAnalysisProvider provider,
        DocumentId documentId,
        int offset
    ) {
        WorkspaceSnapshot workspace = store.capture();
        TextDocumentSnapshot snapshot = (TextDocumentSnapshot) workspace.document(documentId).orElseThrow();
        JavaWorkspaceAnalysis analysis = provider.capture(workspace, documentId);
        WorkspaceSymbolGraph graph = analysis.resolvedGraph();

        return symbolAt(graph, snapshot, offset);
    }

    public static Optional<SymbolId> symbolAt(
        WorkspaceSymbolGraph graph,
        TextDocumentSnapshot snapshot,
        int offset
    ) {
        SemanticIndexShard shard = graph.shards().get(snapshot.id());
        if (shard == null
            || !shard.documentVersion().equals(snapshot.version())
            || !shard.uri().equals(snapshot.uri()))
            return Optional.empty();

        List<ReferenceOccurrence> references = shard.references().stream()
            .filter(ref -> {
                Range<?> range = ref.location().range();
                return offset >= range.start && offset < range.end;
            })
            .toList();

        List<ReferenceOccurrence> calls = references.stream()
            .filter(ref -> ref.kind().equals(ReferenceKindId.CALL))
            .toList();

        List<SymbolId> matches = references.stream()
            .filter(ref -> {
                if (!ref.kind().equals(ReferenceKindId.TYPE_USE))
                    return true;

                return calls.stream().noneMatch(call -> call.location().range().start == ref.location().range().start
                    && call.location().range().end == ref.location().range().end);
            })
            .map(ReferenceOccurrence::target)
            .distinct()
            .toList();

        return matches.size() == 1
            ? Optional.of(matches.getFirst())
            : Optional.empty();
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
