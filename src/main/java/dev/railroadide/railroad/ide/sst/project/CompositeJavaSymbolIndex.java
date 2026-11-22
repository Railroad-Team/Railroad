package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;

import java.util.*;
import java.nio.file.Path;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;

/**
 * Combines symbol indexes, deduplicating lookup results and preferring earlier delegates for binary class stubs.
 */
public final class CompositeJavaSymbolIndex implements JavaSymbolIndex {
    private final List<JavaSymbolIndex> delegates;
    private final Set<String> declaredQualifiedNames;
    private final Set<String> typeNames;
    private final Map<String, ClassStub> classStubsByQualifiedName;

    /**
     * Copies the delegates and merges their type-name and class-stub indexes in precedence order.
     *
     * @param delegates the indexes to combine, ordered from highest to lowest binary-stub precedence
     */
    public CompositeJavaSymbolIndex(List<? extends JavaSymbolIndex> delegates) {
        this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates"));
        Set<String> qualifiedNames = new LinkedHashSet<>();
        Set<String> typeNames = new LinkedHashSet<>();
        Map<String, ClassStub> classStubs = new LinkedHashMap<>();
        for (JavaSymbolIndex delegate : this.delegates) {
            qualifiedNames.addAll(delegate.declaredQualifiedNames());
            typeNames.addAll(delegate.typeNames());
            delegate.classStubsByQualifiedName().forEach(classStubs::putIfAbsent);
        }

        this.declaredQualifiedNames = Set.copyOf(qualifiedNames);
        this.typeNames = Set.copyOf(typeNames);
        this.classStubsByQualifiedName = Map.copyOf(classStubs);
    }

    @Override
    public List<SemanticIndexShard> semanticShards() {
        return semanticShards(ActivityCheckpoint::checkInterrupted);
    }

    @Override
    public List<SemanticIndexShard> semanticShards(Runnable activityCheck) {
        Objects.requireNonNull(activityCheck, "activityCheck").run();
        Set<SymbolId> seen = new HashSet<>();
        List<SemanticIndexShard> result = new ArrayList<>();
        for (JavaSymbolIndex delegate : delegates) {
            for (var shard : delegate.semanticShards(activityCheck)) {
                activityCheck.run();
                var declarations = shard.declarations().stream().filter(symbol -> {
                    activityCheck.run();
                    return seen.add(symbol.id());
                }).toList();
                if (!declarations.isEmpty()) {
                    result.add(new SemanticIndexShard(shard.documentId(),
                        shard.documentVersion(), shard.uri(), declarations, shard.references(), shard.edges()));
                }
            }
        }
        activityCheck.run();
        return List.copyOf(result);
    }

    @Override
    public Set<String> declaredQualifiedNames() {
        return declaredQualifiedNames;
    }

    @Override
    public JavaSymbolIndex forAttachedSource(Path archive, Runnable check) {
        var scoped = delegates.stream().map(delegate -> delegate.forAttachedSource(archive, check)).toList();
        return scoped.equals(delegates) ? this : new CompositeJavaSymbolIndex(scoped);
    }

    @Override
    public Set<String> typeNames() {
        return typeNames;
    }

    @Override
    public boolean containsPackage(String packageName) {
        return delegates.stream().anyMatch(delegate -> delegate.containsPackage(packageName));
    }

    @Override
    public Map<String, ClassStub> classStubsByQualifiedName() {
        return classStubsByQualifiedName;
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupSimpleName(String simpleName) {
        return delegates.stream()
            .flatMap(delegate -> delegate.lookupSimpleName(simpleName).stream())
            .distinct()
            .toList();
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String qualifiedName) {
        return delegates.stream()
            .flatMap(delegate -> delegate.lookupQualifiedName(qualifiedName).stream())
            .distinct()
            .toList();
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String qualifiedName) {
        return delegates.stream()
            .flatMap(delegate -> delegate.lookupMembers(qualifiedName).stream())
            .distinct()
            .toList();
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String ownerQualifiedName, String simpleName) {
        return delegates.stream()
            .flatMap(delegate -> delegate.lookupMember(ownerQualifiedName, simpleName).stream())
            .distinct()
            .toList();
    }
}
