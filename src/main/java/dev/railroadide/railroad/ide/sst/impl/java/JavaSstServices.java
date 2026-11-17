package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysisProvider;
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
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.*;

/** Session-owned Java implementations of the neutral semantic and declaration query contracts. */
public final class JavaSstServices
    implements
        SemanticProvider,
        IndexExtractionProvider,
        IndexQueryProvider,
        AutoCloseable,
        NavigationProvider {
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
        context.checkActive();

        WorkspaceSnapshot workspace = context.workspaceSnapshot();
        DocumentSnapshot source = context.documentSnapshot();
        if (!(source instanceof TextDocumentSnapshot textSource))
            return new FeatureResult<>(context, List.of());

        JavaWorkspaceAnalysis analysis = capture(context);
        WorkspaceSymbolGraph graph = analysis.resolvedGraph(context::checkActive);
        Optional<DefinitionTarget> target = SemanticNavigationResolver.definitionAt(graph, workspace, textSource,
            offset);

        List<Location<?>> locations = target.<List<Location<?>>>map(
            definitionTarget -> List.of(definitionTarget.location()))
            .orElseGet(List::of);
        context.checkActive();
        return new FeatureResult<>(context, locations);
    }

    @Override
    public void close() {
        workspaces.close();
    }
}
