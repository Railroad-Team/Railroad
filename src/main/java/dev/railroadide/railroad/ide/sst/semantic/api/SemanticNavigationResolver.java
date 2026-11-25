package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.*;

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
     * Finds every navigable declaration view of a symbol whose revision is captured by the workspace.
     * Views that are not navigable, not file-backed, lack a text range, or no longer match the captured
     * destination revision are skipped rather than adjusted. Distinct views are all returned in rank order so
     * that callers can present ambiguity instead of guessing.
     *
     * @param graph captured graph pinned for this query
     * @param workspace workspace capture the graph was built from
     * @param symbolId symbol whose declarations are queried
     * @return distinct ranked targets, or an empty list when none can be navigated
     */
    public static List<DefinitionTarget> definitionsOf(
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

                if (isValid(textLocation, snapshot)) {
                    matches.add(new DefinitionTarget(snapshot, textLocation));
                }
            }
        }

        return matches.stream().sorted(RANKING).toList();
    }

    /**
     * Resolves the symbol at an offset and returns its ranked declarations.
     *
     * @param graph captured graph pinned for this query
     * @param workspace workspace capture the graph was built from
     * @param source captured revision containing the offset
     * @param offset zero-based UTF-16 offset in the source revision
     * @return ranked targets, or an empty list when the offset is unresolved, ambiguous, or not navigable
     */
    public static List<DefinitionTarget> definitionAt(
        WorkspaceSymbolGraph graph,
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot source,
        int offset
    ) {
        return symbolAt(graph, source, offset)
            .map(id -> definitionsOf(graph, workspace, id))
            .orElseGet(List::of);
    }

    /**
     * Finds declarations that implement a symbol by following subtype and override relations transitively
     * towards specializations. The queried symbol itself is never included.
     *
     * @param graph captured graph pinned for this query
     * @param workspace workspace capture the graph was built from
     * @param symbolId type or callable whose implementations are queried
     * @param activityCheck callback invoked between graph steps; may throw to abort
     * @return distinct ranked navigable implementation targets
     */
    public static List<DefinitionTarget> implementationsOf(
        WorkspaceSymbolGraph graph,
        WorkspaceSnapshot workspace,
        SymbolId symbolId,
        Runnable activityCheck
    ) {
        Set<SymbolId> visited = new LinkedHashSet<>();
        Deque<SymbolId> pending = new ArrayDeque<>();
        visited.add(symbolId);
        pending.add(symbolId);
        while (!pending.isEmpty()) {
            activityCheck.run();
            for (SymbolEdge edge : graph.incomingEdges(pending.removeFirst())) {
                if ((edge.kind().equals(EdgeKindId.SUBTYPE) || edge.kind().equals(EdgeKindId.OVERRIDE))
                    && visited.add(edge.source())) {
                    pending.add(edge.source());
                }
            }
        }

        visited.remove(symbolId);
        Set<DefinitionTarget> targets = new LinkedHashSet<>();
        for (SymbolId implementation : visited) {
            activityCheck.run();
            targets.addAll(definitionsOf(graph, workspace, implementation));
        }

        return targets.stream().sorted(RANKING).toList();
    }

    /**
     * Collects resolved usages of a symbol whose revisions match the captured workspace. Declaration
     * occurrences are excluded, roles sharing one range are reported once, and occurrences from documents
     * whose captured revision differs from the graph are skipped rather than adjusted.
     *
     * @param graph captured graph pinned for this query
     * @param workspace workspace capture the graph was built from
     * @param symbolId symbol whose usages are queried
     * @return usages ordered by document address and offset
     */
    public static List<Location.TextLocation> usagesOf(
        WorkspaceSymbolGraph graph,
        WorkspaceSnapshot workspace,
        SymbolId symbolId
    ) {
        return usagesOf(graph.referencesTo(symbolId), workspace);
    }

    /**
     * Collects usages from a single resolved file using the same range and revision checks as a graph query.
     *
     * @param shard captured facts of one file
     * @param workspace workspace capture the facts were built from
     * @param symbolId symbol whose usages are queried
     * @return usages ordered by document address and offset
     */
    public static List<Location.TextLocation> usagesOf(
        SemanticIndexShard shard,
        WorkspaceSnapshot workspace,
        SymbolId symbolId
    ) {
        return usagesOf(shard.references().stream().filter(ref -> ref.target().equals(symbolId)).toList(), workspace);
    }

    private static List<Location.TextLocation> usagesOf(
        List<ReferenceOccurrence> references,
        WorkspaceSnapshot workspace
    ) {
        Map<Location.TextLocation, TextDocumentSnapshot> usages = new LinkedHashMap<>();
        for (ReferenceOccurrence reference : references) {
            if (reference.kind().equals(ReferenceKindId.DECLARATION)
                || !(reference.location() instanceof Location.TextLocation location))
                continue;

            if (workspace.document(location.documentId()).orElse(null) instanceof TextDocumentSnapshot snapshot
                && isValid(location, snapshot)) {
                usages.putIfAbsent(location, snapshot);
            }
        }

        List<DefinitionTarget> ordered = new ArrayList<>();
        usages.forEach((location, snapshot) -> ordered.add(new DefinitionTarget(snapshot, location)));
        ordered.sort(SemanticNavigationResolver::compareByAddress);
        return ordered.stream().map(DefinitionTarget::location).toList();
    }

    /**
     * Describes a symbol for user-facing navigation results.
     *
     * @param graph captured graph containing the symbol's declarations
     * @param symbolId symbol to describe
     * @return qualified name when known, otherwise the simple name or identity
     */
    public static String displayName(WorkspaceSymbolGraph graph, SymbolId symbolId) {
        return graph.symbols(symbolId).stream()
            .findFirst()
            .map(symbol -> symbol.qualifiedName() != null ? symbol.qualifiedName() : symbol.simpleName())
            .orElse(symbolId.value());
    }

    private static final Comparator<DefinitionTarget> RANKING = SemanticNavigationResolver::compareByRank;

    private static int compareByRank(DefinitionTarget first, DefinitionTarget second) {
        int provenance = Integer.compare(provenanceRank(first.snapshot().origin().provenance()),
            provenanceRank(second.snapshot().origin().provenance()));
        return provenance != 0 ? provenance : compareByAddress(first, second);
    }

    private static int compareByAddress(DefinitionTarget first, DefinitionTarget second) {
        int address = first.snapshot().uri().toString().compareTo(second.snapshot().uri().toString());
        if (address != 0)
            return address;

        int start = Integer.compare(first.location().range().start, second.location().range().start);
        return start != 0 ? start : Integer.compare(first.location().range().end, second.location().range().end);
    }

    private static int provenanceRank(ProvenanceKind provenance) {
        return switch (provenance) {
            case ORIGINAL -> 0;
            case ATTACHED_SOURCE -> 1;
            case GENERATED -> 2;
            case DECOMPILED -> 3;
            case UNSPECIFIED -> 4;
        };
    }

    private static boolean isValid(Location.TextLocation location, TextDocumentSnapshot snapshot) {
        try {
            location.validateAgainst(snapshot);
            return true;
        } catch (IllegalArgumentException | IndexOutOfBoundsException _) {
            return false;
        }
    }

    /**
     * Resolves the single symbol referenced or declared at an offset of a captured revision. A constructor call
     * takes precedence over the type use sharing its range; any other overlap is ambiguous and unresolved.
     *
     * @param graph captured graph pinned for this query
     * @param snapshot captured revision containing the offset
     * @param offset zero-based UTF-16 offset
     * @return resolved symbol, or an empty optional when the graph has no matching revision or is ambiguous
     */
    public static Optional<SymbolId> symbolAt(
        WorkspaceSymbolGraph graph,
        TextDocumentSnapshot snapshot,
        int offset
    ) {
        return graph.shard(snapshot.id()).flatMap(shard -> symbolAt(shard, snapshot, offset));
    }

    /**
     * Resolves the symbol at an offset using only the source file's captured facts.
     *
     * @param shard captured facts of the file
     * @param snapshot captured revision containing the offset
     * @param offset zero-based UTF-16 offset
     * @return resolved symbol, or an empty optional when the facts belong to another revision or are ambiguous
     */
    public static Optional<SymbolId> symbolAt(SemanticIndexShard shard, TextDocumentSnapshot snapshot, int offset) {
        if (!shard.documentId().equals(snapshot.id())
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
