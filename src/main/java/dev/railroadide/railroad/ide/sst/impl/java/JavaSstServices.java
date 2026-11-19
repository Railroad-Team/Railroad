package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysisProvider;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.graph.api.WorkspaceSymbolGraph;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.sst.request.api.RequestContext;
import dev.railroadide.railroad.ide.sst.request.api.WorkspaceRequestContext;
import dev.railroadide.railroad.ide.sst.semantic.api.DefinitionTarget;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticModel;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticNavigationResolver;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Session-owned Java implementations of the neutral semantic, declaration, navigation, rename and code action
 * contracts.
 */
public final class JavaSstServices
    implements
        SemanticProvider,
        IndexExtractionProvider,
        IndexQueryProvider,
        AutoCloseable,
        NavigationProvider,
        RenameProvider,
        CodeActionProvider {
    private final JavaWorkspaceAnalysisProvider workspaces = new JavaWorkspaceAnalysisProvider();

    /**
     * Returns the shared captured Java inputs used by all features in this language session.
     *
     * @param context captured workspace and request activity controls
     * @return the Java analysis bound to the captured workspace
     */
    public JavaWorkspaceAnalysis capture(RequestContext context) {
        return workspaces.capture(context);
    }

    @Override
    public FeatureResult<SemanticModel> analyze(FeatureRequestContext context) {
        context.checkActive();
        var model = JavaSemanticAnalyzer.analyzeFacts(context, capture(context));
        context.checkActive();
        return new FeatureResult<>(context, model);
    }

    @Override
    public FeatureResult<List<SymbolOccurrence>> extract(FeatureRequestContext context) {
        SemanticModel model = analyze(context).value();
        TextDocumentSnapshot snapshot = model.syntaxTree().documentSnapshot();
        var declarations = new ArrayList<SymbolOccurrence>();
        Deque<SyntaxNode> pending = new ArrayDeque<>();
        pending.add(model.syntaxTree().root());
        while (!pending.isEmpty()) {
            context.checkActive();
            SyntaxNode node = pending.removeFirst();
            model.declaredSymbol(node).ifPresent(symbol -> declarations.add(new SymbolOccurrence(symbol,
                Location.TextLocation.from(snapshot, node.start(), node.end()))));
            pending.addAll(node.children());
        }
        context.checkActive();
        return new FeatureResult<>(context, List.copyOf(declarations));
    }

    @Override
    public FeatureResult<List<SymbolOccurrence>> query(WorkspaceRequestContext context, String name) {
        Objects.requireNonNull(name, "name");
        var found = new ArrayList<SymbolOccurrence>();
        for (DocumentSnapshot snapshot : context.workspaceSnapshot().documents().values()) {
            context.checkActive();
            if (!(snapshot instanceof TextDocumentSnapshot) || !snapshot.languageId().equals("java"))
                continue;
            var request = new FeatureRequestContext(context.workspaceSnapshot(), snapshot,
                context.cancellationToken(), context.deadline());
            extract(request).value().stream().filter(entry -> entry.symbol().simpleName().equals(name)
                || entry.symbol().qualifiedName().filter(name::equals).isPresent()).forEach(found::add);
        }
        context.checkActive();
        return new FeatureResult<>(context, List.copyOf(found));
    }

    @Override
    public FeatureResult<List<Location<?>>> definitions(FeatureRequestContext context, int offset) {
        return targets(context, offset, (graph, id) -> SemanticNavigationResolver.definitionsOf(graph,
            context.workspaceSnapshot(), id));
    }

    @Override
    public FeatureResult<List<Location<?>>> implementations(FeatureRequestContext context, int offset) {
        return targets(context, offset, (graph, id) -> SemanticNavigationResolver.implementationsOf(graph,
            context.workspaceSnapshot(), id, context::checkActive));
    }

    private FeatureResult<List<Location<?>>> targets(
        FeatureRequestContext context,
        int offset,
        BiFunction<WorkspaceSymbolGraph, SymbolId, List<DefinitionTarget>> query
    ) {
        context.checkActive();
        if (!(context.documentSnapshot() instanceof TextDocumentSnapshot source))
            return new FeatureResult<>(context, List.of());

        WorkspaceSymbolGraph graph = capture(context).resolvedGraph(context::checkActive);
        List<Location<?>> locations = SemanticNavigationResolver.symbolAt(graph, source, offset)
            .map(id -> query.apply(graph, id))
            .orElseGet(List::of)
            .stream()
            .<Location<?>>map(DefinitionTarget::location)
            .toList();
        context.checkActive();
        return new FeatureResult<>(context, locations);
    }

    @Override
    public FeatureResult<UsageSearch> usages(FeatureRequestContext context, UsageQuery query, UsageSink sink) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(sink, "sink");
        context.checkActive();

        WorkspaceSnapshot workspace = context.workspaceSnapshot();
        WorkspaceSymbolGraph graph = capture(context).resolvedGraph(context::checkActive);
        Optional<SymbolId> symbol = switch (query) {
            case UsageQuery.OfSymbol(SymbolId id) -> graph.symbols(id).isEmpty() ? Optional.empty() : Optional.of(id);
            case UsageQuery.AtOffset(int offset) -> context.documentSnapshot() instanceof TextDocumentSnapshot source
                ? SemanticNavigationResolver.symbolAt(graph, source, offset)
                : Optional.empty();
        };
        if (symbol.isEmpty())
            return new FeatureResult<>(context, null);

        List<Location.TextLocation> usages = SemanticNavigationResolver.usagesOf(graph, workspace, symbol.get());
        Map<DocumentId, List<Location<?>>> byDocument = new LinkedHashMap<>();
        for (Location.TextLocation usage : usages) {
            byDocument.computeIfAbsent(usage.documentId(), _ -> new ArrayList<>()).add(usage);
        }

        int searched = 0;
        sink.accept(List.of(), searched, byDocument.size());
        for (List<Location<?>> batch : byDocument.values()) {
            context.checkActive();
            sink.accept(List.copyOf(batch), ++searched, byDocument.size());
        }

        context.checkActive();
        return new FeatureResult<>(context, new UsageSearch(symbol.get(),
            SemanticNavigationResolver.displayName(graph, symbol.get()), List.<Location<?>>copyOf(usages)));
    }

    @Override
    public FeatureResult<RenamePreparation> prepareRename(FeatureRequestContext context, int offset) {
        context.checkActive();
        RenamePreparation preparation = new JavaRenameEngine(context, this::capture).prepare(offset);
        context.checkActive();
        return new FeatureResult<>(context, preparation);
    }

    @Override
    public FeatureResult<RenameProposal> rename(
        FeatureRequestContext context,
        int offset,
        SymbolId symbol,
        String newName
    ) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(newName, "newName");
        context.checkActive();
        RenameProposal proposal = new JavaRenameEngine(context, this::capture).rename(offset, symbol, newName);
        context.checkActive();
        return new FeatureResult<>(context, proposal);
    }

    @Override
    public FeatureResult<List<CodeAction>> actions(FeatureRequestContext context, CodeActionRequest request) {
        Objects.requireNonNull(request, "request");
        context.checkActive();
        List<CodeAction> actions = new JavaCodeActions(context, this::capture).discover(request);
        context.checkActive();
        return new FeatureResult<>(context, actions);
    }

    @Override
    public FeatureResult<CodeAction> resolve(FeatureRequestContext context, CodeAction action) {
        Objects.requireNonNull(action, "action");
        context.checkActive();
        CodeAction resolved = new JavaCodeActions(context, this::capture).resolve(action);
        context.checkActive();
        return new FeatureResult<>(context, resolved);
    }

    @Override
    public void close() {
        workspaces.close();
    }
}
