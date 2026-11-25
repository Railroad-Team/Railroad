package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysisProvider;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.graph.api.WorkspaceSymbolGraph;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySources;
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

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

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
    private final JavaLibrarySources librarySources;
    private final Function<RequestContext, JavaWorkspaceAnalysis> captures;
    private final @Nullable JavaWorkspaceAnalysisProvider ownedWorkspaces;

    /**
     * Creates services that open library and JDK classes through the application's shared library sources.
     */
    public JavaSstServices() {
        this(JavaLibrarySources.shared());
    }

    /**
     * Creates services that open library and JDK classes through the given library sources and own their analysis
     * caches.
     *
     * @param librarySources provider of readable views of compiled classes
     */
    public JavaSstServices(JavaLibrarySources librarySources) {
        this.librarySources = Objects.requireNonNull(librarySources, "librarySources");
        ownedWorkspaces = new JavaWorkspaceAnalysisProvider();
        captures = ownedWorkspaces::capture;
    }

    /**
     * Creates services that capture Java analysis through a shared owner, such as the project's analysis context,
     * so features reuse its library and JDK indexes. Closing these services does not close that owner.
     *
     * @param librarySources provider of readable views of compiled classes
     * @param captures captures the Java analysis for a request
     */
    public JavaSstServices(
        JavaLibrarySources librarySources,
        Function<RequestContext, JavaWorkspaceAnalysis> captures
    ) {
        this.librarySources = Objects.requireNonNull(librarySources, "librarySources");
        this.captures = Objects.requireNonNull(captures, "captures");
        ownedWorkspaces = null;
    }

    /**
     * Returns the shared captured Java inputs used by all features in this language session.
     *
     * @param context captured workspace and request activity controls
     * @return the Java analysis bound to the captured workspace
     */
    public JavaWorkspaceAnalysis capture(RequestContext context) {
        return captures.apply(context);
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
    public FeatureResult<List<DefinitionTarget>> definitions(FeatureRequestContext context, int offset) {
        context.checkActive();
        if (!(context.documentSnapshot() instanceof TextDocumentSnapshot source))
            return new FeatureResult<>(context, List.of());

        List<DefinitionTarget> targets = capture(context)
            .definitionsAt(context.workspaceSnapshot(), source, offset, librarySources, context::checkActive);
        context.checkActive();
        return new FeatureResult<>(context, targets);
    }

    @Override
    public FeatureResult<List<DefinitionTarget>> implementations(FeatureRequestContext context, int offset) {
        context.checkActive();
        if (!(context.documentSnapshot() instanceof TextDocumentSnapshot source))
            return new FeatureResult<>(context, List.of());

        var queries = new JavaSourceSetQueries(context, this::capture);
        WorkspaceSymbolGraph graph = queries.graph(null, false);
        List<DefinitionTarget> targets = SemanticNavigationResolver.symbolAt(graph, source, offset)
            .map(id -> SemanticNavigationResolver.implementationsOf(queries.graph(id, false),
                context.workspaceSnapshot(), id, context::checkActive))
            .orElseGet(List::of);
        context.checkActive();
        return new FeatureResult<>(context, targets);
    }

    @Override
    public FeatureResult<UsageSearch> usages(FeatureRequestContext context, UsageQuery query, UsageSink sink) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(sink, "sink");
        context.checkActive();

        UsageSearch search = new JavaSourceSetQueries(context, this::capture).usages(query, sink);
        context.checkActive();
        return new FeatureResult<>(context, search);
    }

    @Override
    public FeatureResult<RenamePreparation> prepareRename(FeatureRequestContext context, int offset) {
        context.checkActive();
        RenamePreparation preparation = new JavaRenameEngine(context, this::capture, null).prepare(offset);
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
        RenameProposal proposal = new JavaRenameEngine(context, this::capture, symbol).rename(offset, symbol, newName);
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
        if (ownedWorkspaces != null) {
            ownedWorkspaces.close();
        }
    }
}
