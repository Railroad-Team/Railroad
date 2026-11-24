package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.ReferenceKindId;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.graph.api.WorkspaceSymbolGraph;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.impl.java.JavaUnicodeEscapes;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.project.*;
import dev.railroadide.railroad.ide.sst.provider.api.UsageQuery;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSearch;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSink;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import dev.railroadide.railroad.ide.sst.semantic.api.DefinitionTarget;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticNavigationResolver;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolDescriptor;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolSignature;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Immutable Java analysis inputs derived from one workspace capture. Workspace source comes from the
 * captured revision, and library and JDK symbols are pinned for the captured configuration.
 */
public final class JavaWorkspaceAnalysis implements JavaSourceSnapshotIndex {
    private final JavaProjectSemanticIndex sourceIndex;
    private final JavaSymbolIndex symbols;
    private final Map<Path, String> sourceTexts;
    private final List<Path> classpath;
    private final Path jdkHome;
    private final ReentrantLock graphLock = new ReentrantLock();
    private volatile WorkspaceSymbolGraph declarationGraph;
    private volatile WorkspaceSymbolGraph resolvedGraph;
    /**
     * Copies source text and dependency paths so later owners cannot change request inputs.
     *
     * @param sourceIndex captured source declarations
     * @param symbols immutable combined symbols
     * @param sourceTexts captured source text
     * @param classpath ordered dependency roots
     * @param jdkHome selected JDK home
     */
    public JavaWorkspaceAnalysis(
        JavaProjectSemanticIndex sourceIndex,
        JavaSymbolIndex symbols,
        Map<Path, String> sourceTexts,
        List<Path> classpath,
        Path jdkHome
    ) {
        this.sourceIndex = Objects.requireNonNull(sourceIndex);
        this.symbols = Objects.requireNonNull(symbols);
        this.sourceTexts = Map.copyOf(sourceTexts);
        this.classpath = List.copyOf(classpath);
        this.jdkHome = Objects.requireNonNull(jdkHome);
    }

    /**
     * Returns the source declarations pinned by this capture.
     *
     * @return captured source declaration index
     */
    public JavaProjectSemanticIndex sourceIndex() {
        return sourceIndex;
    }
    /**
     * Returns the combined symbols pinned by this capture.
     *
     * @return captured combined source and binary symbol index
     */
    public JavaSymbolIndex symbols() {
        return symbols;
    }
    /**
     * Returns source text copied when this analysis was captured.
     *
     * @return immutable captured source text keyed by path
     */
    public Map<Path, String> sourceTexts() {
        return sourceTexts;
    }
    /**
     * Returns the dependency roots pinned by this workspace capture.
     *
     * @return immutable ordered dependency roots captured for this analysis
     */
    public List<Path> classpath() {
        return classpath;
    }
    /**
     * Returns the JDK home pinned by this workspace capture.
     *
     * @return captured JDK home path
     */
    public Path jdkHome() {
        return jdkHome;
    }

    private static boolean extendsAny(ClassStub stub, Set<String> names) {
        if (stub.superClass() instanceof Type.ClassType parent && names.contains(parent.name()))
            return true;
        for (var type : stub.interfaces()) {
            if (type instanceof Type.ClassType parent && names.contains(parent.name()))
                return true;
        }
        return false;
    }

    @Override
    public @Nullable SemanticIndexShard declarationShard(String binaryName, Runnable activityCheck) {
        return symbols.declarationShard(binaryName, activityCheck);
    }

    /**
     * Captures source declarations and the library owners of resolved header references.
     * Body references require {@link #resolvedGraph(Runnable)}.
     *
     * @return immutable declaration graph for this capture
     */
    public WorkspaceSymbolGraph graph() {
        ActivityCheckpoint.checkInterrupted();
        var cached = declarationGraph;
        if (cached != null)
            return cached;
        lockGraph(ActivityCheckpoint::checkInterrupted);
        try {
            if (declarationGraph == null) {
                var result = WorkspaceSymbolGraph.of(withReferencedDeclarations(sourceIndex.semanticShards(),
                    ActivityCheckpoint::checkInterrupted));
                ActivityCheckpoint.checkInterrupted();
                declarationGraph = result;
            }
            return declarationGraph;
        } finally {
            graphLock.unlock();
        }
    }

    private void lockGraph(Runnable check) {
        try {
            while (!graphLock.tryLock(25, TimeUnit.MILLISECONDS)) {
                check.run();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Java graph capture interrupted while waiting for resolution");
        }
    }

    /**
     * Resolves all captured source bodies against these captured source and binary declarations.
     * Runs on a language worker, with cooperative cancellation and no live filesystem reads.
     * The graph includes referenced library declarations and their supertypes.
     * Cancellation throws without publishing or retaining a partially enriched graph. Successful
     * construction is single-flight and retained by this captured analysis until its owner releases it.
     *
     * @param activityCheck callback invoked to check cancellation or interruption; may throw to abort
     * @return cached immutable graph including resolved source references and override relations
     */
    public WorkspaceSymbolGraph resolvedGraph(Runnable activityCheck) {
        Objects.requireNonNull(activityCheck).run();
        var cached = resolvedGraph;
        if (cached != null) {
            activityCheck.run();
            return cached;
        }
        lockGraph(activityCheck);
        try {
            activityCheck.run();
            if (resolvedGraph == null) {
                var result = buildResolvedGraph(activityCheck);
                activityCheck.run();
                resolvedGraph = result;
            }
            activityCheck.run();
            return resolvedGraph;
        } finally {
            graphLock.unlock();
        }
    }

    private WorkspaceSymbolGraph buildResolvedGraph(Runnable activityCheck) {
        List<SemanticIndexShard> sources = new ArrayList<>();
        var extractor = new JavaProjectSemanticExtractor();
        for (var file : sourceIndex.files().values()) {
            activityCheck.run();
            sources.add(resolveShard(extractor, file, activityCheck));
        }
        var shards = withReferencedDeclarations(sources, activityCheck);
        var result = WorkspaceSymbolGraph.of(JavaOverrideGraphExtractor.extract(shards, symbols, activityCheck),
            activityCheck);
        activityCheck.run();
        return result;
    }

    private Collection<SemanticIndexShard> withReferencedDeclarations(
        List<SemanticIndexShard> sources,
        Runnable check
    ) {
        Map<DocumentId, SemanticIndexShard> shards = new LinkedHashMap<>();
        for (var source : sources) {
            check.run();
            shards.put(source.documentId(), source);
        }
        var pending = new ArrayDeque<>(sources);
        Set<String> owners = new HashSet<>();
        while (!pending.isEmpty()) {
            check.run();
            var shard = pending.removeFirst();
            for (var reference : shard.references()) {
                check.run();
                if (!reference.kind().equals(ReferenceKindId.DECLARATION)) {
                    addOwner(reference.target(), owners, shards, pending, check);
                }
            }
            for (var edge : shard.edges()) {
                check.run();
                addOwner(edge.target(), owners, shards, pending, check);
            }
        }
        return shards.values();
    }

    /**
     * Includes compiled subclasses of workspace types so a rename cannot break a library override, even when
     * no workspace file mentions that subclass.
     *
     * @param check checks request activity
     * @return the resolved graph with any additional binary descendants needed for rename checks
     */
    public WorkspaceSymbolGraph renameGraph(Runnable check) {
        var graph = resolvedGraph(check);
        Set<String> parents = new HashSet<>();
        for (String name : sourceIndex.declaredQualifiedNames()) {
            check.run();
            for (var type : sourceIndex.lookupQualifiedName(name)) {
                if (type.descriptor() != null) {
                    parents.add(JavaSymbolIds.owningTypeName(type.descriptor().id()));
                }
            }
        }
        List<SemanticIndexShard> additional = new ArrayList<>();
        Set<String> descendants = new HashSet<>();
        while (!parents.isEmpty()) {
            Set<String> next = new HashSet<>();
            for (var stub : symbols.classStubsByQualifiedName().values()) {
                check.run();
                if (!extendsAny(stub, parents))
                    continue;
                String name = stub.getFullName();
                if (descendants.add(name)) {
                    next.add(name);
                    if (graph.symbols(JavaSymbolIds.type(name)).isEmpty()) {
                        var shard = symbols.declarationShard(name, check);
                        if (shard != null) {
                            additional.add(shard);
                        }
                    }
                }
            }
            parents = next;
        }
        if (additional.isEmpty())
            return graph;
        additional.addAll(graph.shards().values());
        return WorkspaceSymbolGraph.of(JavaOverrideGraphExtractor.extract(withReferencedDeclarations(additional, check),
            symbols, check), check);
    }

    private void addOwner(
        SymbolId target,
        Set<String> owners,
        Map<DocumentId, SemanticIndexShard> shards,
        Deque<SemanticIndexShard> pending,
        Runnable check
    ) {
        if (!sourceIndex.graph().symbols(target).isEmpty())
            return;
        String owner = JavaSymbolIds.owningTypeName(target);
        if (owner == null || !owners.add(owner))
            return;
        var declaration = symbols.declarationShard(owner, check);
        if (declaration != null && shards.putIfAbsent(declaration.documentId(), declaration) == null) {
            pending.addLast(declaration);
        }
    }

    /**
     * Finds the navigable declarations of the symbol at an offset, resolving only the requesting document's body
     * against the captured declarations, so the whole-workspace graph is not needed. A symbol declared only by a
     * library or the JDK navigates into a read-only view of its class from {@code librarySources}: the attached
     * source when available, otherwise a declaration-only view. That view is not part of the workspace capture
     * unless it is already open.
     *
     * @param workspace workspace capture this analysis was built from
     * @param source captured revision containing the offset
     * @param offset zero-based UTF-16 offset in the source revision
     * @param librarySources provider of readable views of compiled classes
     * @param activityCheck callback invoked to check cancellation or interruption; may throw to abort
     * @return ranked targets, or an empty list when the offset is unresolved, ambiguous, or not navigable
     */
    public List<DefinitionTarget> definitionsAt(
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot source,
        int offset,
        JavaLibrarySources librarySources,
        Runnable activityCheck
    ) {
        Objects.requireNonNull(librarySources, "librarySources");
        Objects.requireNonNull(activityCheck).run();
        Map<DocumentId, SemanticIndexShard> shards = new LinkedHashMap<>();
        JavaProjectSemanticIndex.SourceFileIndex requested = null;
        for (var file : sourceIndex.files().values()) {
            activityCheck.run();
            var shard = file.semanticShard();
            if (shard == null)
                continue;
            shards.put(shard.documentId(), shard);
            if (shard.documentId().equals(source.id())) {
                requested = file;
            }
        }
        if (requested == null)
            return List.of();

        var resolved = resolveShard(new JavaProjectSemanticExtractor(), requested, activityCheck);
        shards.put(resolved.documentId(), resolved);
        var graph = WorkspaceSymbolGraph.of(shards.values(), activityCheck);
        SymbolId symbol = SemanticNavigationResolver.symbolAt(graph, source, offset).orElse(null);
        if (symbol == null)
            return List.of();
        if (!graph.symbols(symbol).isEmpty())
            return SemanticNavigationResolver.definitionsOf(graph, workspace, symbol);

        return libraryDefinition(workspace, symbol, librarySources, activityCheck).map(List::of).orElseGet(List::of);
    }

    private static @Nullable SymbolDescriptor declarationIn(SemanticIndexShard shard, SymbolId symbol) {
        return shard.declarations().stream().filter(candidate -> candidate.id().equals(symbol)).findFirst()
            .orElse(null);
    }

    /**
     * Searches captured source files for references to one symbol. Resolves the caret file first and publishes
     * results as each candidate file is checked against the captured source and binary declarations.
     *
     * @param workspace workspace capture this analysis was built from
     * @param document requesting revision for an offset query
     * @param query position or stable identity of the target symbol
     * @param sink receiver for usage batches and candidate file counts
     * @param activityCheck checks cancellation and the request deadline
     * @return complete ordered results, or null when the symbol cannot be resolved
     */
    public @Nullable UsageSearch usages(
        WorkspaceSnapshot workspace,
        DocumentSnapshot document,
        UsageQuery query,
        UsageSink sink,
        Runnable activityCheck
    ) {
        activityCheck.run();
        var extractor = new JavaProjectSemanticExtractor();
        var files = sourceIndex.files().values();
        SemanticIndexShard caretShard = null;
        SymbolId symbol;
        if (query instanceof UsageQuery.AtOffset(int offset)) {
            if (!(document instanceof TextDocumentSnapshot source))
                return null;
            var requested = files.stream().filter(file -> file.semanticShard().documentId().equals(source.id()))
                .findFirst().orElse(null);
            if (requested == null)
                return null;
            caretShard = resolveShard(extractor, requested, activityCheck);
            symbol = SemanticNavigationResolver.symbolAt(caretShard, source, offset).orElse(null);
            String text = source.text();
            if (symbol == null && offset > 0 && offset <= text.length()
                && Character.isJavaIdentifierPart(text.codePointBefore(offset))
                && (offset == text.length() || !Character.isJavaIdentifierPart(text.codePointAt(offset)))) {
                symbol = SemanticNavigationResolver.symbolAt(caretShard, source, offset - 1).orElse(null);
            }
            if (symbol == null)
                return null;
        } else {
            symbol = ((UsageQuery.OfSymbol) query).symbol();
        }

        var graph = resolvedGraph == null ? sourceIndex.graph() : resolvedGraph;
        SymbolDescriptor declaration = graph.symbols(symbol).stream().findFirst().orElse(null);
        if (declaration == null && caretShard != null) {
            declaration = declarationIn(caretShard, symbol);
        }
        if (declaration == null) {
            String owner = JavaSymbolIds.owningTypeName(symbol);
            var shard = owner == null ? null : symbols.declarationShard(owner, activityCheck);
            if (shard != null) {
                declaration = declarationIn(shard, symbol);
            }
        }
        String requiredName = declaration != null && (declaration.kind().id().equals(JavaSymbolKind.METHOD.id())
            || declaration.kind().id().equals(JavaSymbolKind.FIELD.id())) ? declaration.simpleName() : null;
        var candidates = files.stream().filter(file -> {
            activityCheck.run();
            return requiredName == null
                || JavaUnicodeEscapes.translate(sourceTexts.get(file.path())).contains(requiredName);
        }).sorted(Comparator.comparing(file -> file.semanticShard().uri().toString())).toList();

        var usages = new ArrayList<Location<?>>();
        int searched = 0;
        sink.accept(List.of(), searched, candidates.size());
        for (var file : candidates) {
            activityCheck.run();
            var shard = caretShard != null && caretShard.documentId().equals(file.semanticShard().documentId())
                ? caretShard
                : resolveShard(extractor, file, activityCheck);
            if (declaration == null) {
                declaration = declarationIn(shard, symbol);
            }
            var batch = List.<Location<?>>copyOf(SemanticNavigationResolver.usagesOf(shard, workspace, symbol));
            activityCheck.run();
            usages.addAll(batch);
            sink.accept(batch, ++searched, candidates.size());
        }
        activityCheck.run();
        if (query instanceof UsageQuery.OfSymbol && declaration == null && usages.isEmpty())
            return null;
        String name = declaration == null
            ? symbol.value()
            : Objects.requireNonNullElse(declaration.qualifiedName(), declaration.simpleName());
        return new UsageSearch(symbol, name, usages);
    }

    private Optional<DefinitionTarget> libraryDefinition(
        WorkspaceSnapshot workspace,
        SymbolId symbol,
        JavaLibrarySources librarySources,
        Runnable activityCheck
    ) {
        List<JavaSymbolIds.Component> components;
        try {
            components = JavaSymbolIds.components(symbol);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        int typeIndex = -1;
        for (int index = 0; index < components.size(); index++) {
            if (components.get(index).kind().equals("type")) {
                typeIndex = index;
            }
        }
        if (typeIndex < 0)
            return Optional.empty();

        ClassStub topLevel = topLevelClass(components.get(typeIndex).value());
        if (topLevel == null)
            return Optional.empty();

        DocumentUri binary = binaryLocation(topLevel.getFullName()).orElse(null);
        if (binary == null)
            return Optional.empty();
        activityCheck.run();
        TextDocumentSnapshot view = librarySources.open(binary, topLevel, symbols.classStubsByQualifiedName())
            .orElse(null);
        if (view == null)
            return Optional.empty();
        activityCheck.run();
        for (DocumentSnapshot document : workspace.documents().values()) {
            if (document instanceof TextDocumentSnapshot open && open.uri().equals(view.uri())
                && open.text().equals(view.text())) {
                view = open;
                break;
            }
        }

        Path path = view.uri().filePath().orElseThrow();
        var tree = JavaSyntaxParser.parse(view, activityCheck);
        var model = JavaSemanticAnalyzer.analyzeProjectDeclarations(tree, symbols, activityCheck);
        var declarations = new JavaProjectSemanticExtractor()
            .extractProjectDeclarations(path, model, symbols, activityCheck).semanticShard().declarations();
        activityCheck.run();

        Location.TextLocation location = null;
        for (int length = components.size(); length > typeIndex && location == null; length--) {
            location = declarationLocation(declarations, components.subList(0, length));
        }
        if (location == null) {
            location = Location.TextLocation.from(view, 0, 0);
        }
        return Optional.of(new DefinitionTarget(view, location));
    }

    /**
     * Looks up the declaration of a resolved callable in the source attached to its binary.
     *
     * @param librarySources provider of local source attachments
     * @param activityCheck cooperative cancellation check
     * @return request-local lookup that parses each declaring source file once
     */
    public Function<SymbolId, Optional<SymbolSignature>> attachedSignatures(
        JavaLibrarySources librarySources,
        Runnable activityCheck
    ) {
        Map<String, Map<SymbolId, SymbolSignature>> bySource = new HashMap<>();
        return callable -> {
            activityCheck.run();
            String owner = JavaSymbolIds.owningTypeName(callable);
            ClassStub topLevel = owner == null ? null : topLevelClass(owner);
            return topLevel == null
                ? Optional.empty()
                : Optional.ofNullable(bySource
                    .computeIfAbsent(topLevel.getFullName(),
                        key -> readAttachedSignatures(topLevel, librarySources, activityCheck))
                    .get(callable));
        };
    }

    private Map<SymbolId, SymbolSignature> readAttachedSignatures(
        ClassStub topLevel,
        JavaLibrarySources librarySources,
        Runnable activityCheck
    ) {
        var source = binaryLocation(topLevel.getFullName())
            .flatMap(binary -> librarySources.readAttachedSource(binary, topLevel)).orElse(null);
        if (source == null)
            return Map.of();
        var tree = JavaSyntaxParser.parse(source, activityCheck);
        var model = JavaSemanticAnalyzer.analyzeProjectDeclarations(tree, symbols, activityCheck);
        var shard = JavaSemanticGraphExtractor.extractProjectDeclarations(model, symbols, activityCheck);
        Map<SymbolId, SymbolSignature> signatures = new HashMap<>();
        for (var declaration : shard.declarations()) {
            if (declaration.signature() != null) {
                signatures.put(declaration.id(), declaration.signature());
            }
        }
        return signatures;
    }

    private @Nullable ClassStub topLevelClass(String owner) {
        ClassStub topLevel = symbols.lookupClassStub(owner);
        Set<String> visited = new HashSet<>();
        while (topLevel != null && topLevel.enclosingClassName() != null) {
            if (!visited.add(topLevel.getFullName()))
                return null;
            topLevel = symbols.lookupClassStub(topLevel.enclosingClassName());
        }
        return topLevel;
    }

    private Optional<DocumentUri> binaryLocation(String qualifiedName) {
        for (var descriptor : symbols.lookupQualifiedName(qualifiedName)) {
            if (descriptor.descriptor() == null)
                continue;
            for (var location : descriptor.descriptor().locations()) {
                if (!location.uri().isFile() || location.uri().toString().endsWith(".class"))
                    return Optional.of(location.uri());
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the declaration identified by, or nested under, a prefix of a Java identity, preferring the shallowest
     * declaration and then the earliest. A prefix ending at a member name thereby matches the first overload.
     */
    private static Location.TextLocation declarationLocation(
        List<SymbolDescriptor> declarations,
        List<JavaSymbolIds.Component> prefix
    ) {
        Location.TextLocation best = null;
        int bestDepth = Integer.MAX_VALUE;
        for (var declaration : declarations) {
            List<JavaSymbolIds.Component> components;
            try {
                components = JavaSymbolIds.components(declaration.id());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            if (components.size() < prefix.size() || !components.subList(0, prefix.size()).equals(prefix))
                continue;
            for (var source : declaration.locations()) {
                Location<?> candidate = source.selection() != null ? source.selection() : source.declaration();
                if (!(candidate instanceof Location.TextLocation text))
                    continue;
                if (components.size() < bestDepth
                    || components.size() == bestDepth && text.range().start < best.range().start) {
                    best = text;
                    bestDepth = components.size();
                }
            }
        }
        return best;
    }

    private SemanticIndexShard resolveShard(
        JavaProjectSemanticExtractor extractor,
        JavaProjectSemanticIndex.SourceFileIndex file,
        Runnable activityCheck
    ) {
        var shard = file.semanticShard();
        if (shard == null)
            throw new IllegalStateException("Captured source has no semantic shard: " + file.path());
        if (resolvedGraph != null) {
            activityCheck.run();
            return resolvedGraph.shard(shard.documentId()).orElseThrow();
        }
        var origin = shard.declarations().stream().flatMap(symbol -> symbol.locations().stream())
            .filter(location -> location.uri().equals(shard.uri())).map(location -> location.origin())
            .findFirst().orElseGet(() -> DocumentOrigin.unspecified(shard.uri()));
        String text = Objects.requireNonNull(sourceTexts.get(file.path()), "Captured source text");
        var snapshot = new TextDocumentSnapshot(shard.documentId(), shard.uri(), shard.documentVersion(),
            "java", origin, text, StandardCharsets.UTF_8);
        var tree = JavaSyntaxParser.parse(snapshot, activityCheck);
        var model = JavaSemanticAnalyzer.analyzeFacts(tree, this, activityCheck);
        var resolved = extractor.extract(file.path(), model, this, activityCheck).semanticShard();
        activityCheck.run();
        return resolved;
    }

    /**
     * Resolves captured source bodies with thread-interruption checks.
     *
     * @return cached immutable graph including resolved source references and override relations
     */
    public WorkspaceSymbolGraph resolvedGraph() {
        return resolvedGraph(ActivityCheckpoint::checkInterrupted);
    }

    @Override
    public Optional<String> sourceText(Path path) {
        return Optional.ofNullable(sourceTexts.get(path));
    }
    @Override
    public Set<String> declaredQualifiedNames() {
        return symbols.declaredQualifiedNames();
    }
    @Override
    public Set<String> typeNames() {
        return symbols.typeNames();
    }
    @Override
    public boolean containsPackage(String name) {
        return symbols.containsPackage(name);
    }
    @Override
    public Map<String, ClassStub> classStubsByQualifiedName() {
        return symbols.classStubsByQualifiedName();
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupSimpleName(String name) {
        return symbols.lookupSimpleName(name);
    }

    @Override
    public List<String> qualifiedTypeNames(String simpleName) {
        return symbols.qualifiedTypeNames(simpleName);
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String name) {
        return symbols.lookupQualifiedName(name);
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String name) {
        return symbols.lookupMembers(name);
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String owner, String name) {
        return symbols.lookupMember(owner, name);
    }
}
