package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/** Prevents an older binary declaration from supplying members for a captured source type. */
public final class JavaSourcePrecedenceIndex implements JavaSymbolIndex {
    private final JavaProjectSemanticIndex sources;
    private final JavaSymbolIndex binaries;
    private final Map<String, String> sourceNames;
    private final Set<String> hiddenBinaries;
    private final Map<String, ClassStub> classStubs;
    private final Set<String> qualifiedNames;
    private final Set<String> typeNames;

    /**
     * Combines symbols while giving captured source declarations precedence over their binaries.
     *
     * @param sources captured source declarations
     * @param binaries pinned library and JDK declarations
     */
    public JavaSourcePrecedenceIndex(JavaProjectSemanticIndex sources, JavaSymbolIndex binaries) {
        this(sources, binaries, null);
    }

    private JavaSourcePrecedenceIndex(
        JavaProjectSemanticIndex sources,
        JavaSymbolIndex binaries,
        @Nullable JavaSourcePrecedenceIndex previous
    ) {
        this.sources = sources;
        this.binaries = binaries;
        Map<String, String> names = new HashMap<>();
        sources.declaredQualifiedNames().forEach(name -> names.put(name, name));
        for (JavaProjectSemanticIndex.SourceFileIndex file : sources.files().values()) {
            String prefix = file.packageName() == null || file.packageName().isEmpty() ? "" : file.packageName() + ".";
            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : file.declaredSymbols()) {
                String name = symbol.qualifiedName();
                if (name != null && sources.declaredQualifiedNames().contains(name) && name.startsWith(prefix)) {
                    // Only dots in the type hierarchy become '$'; identifier characters stay intact.
                    names.putIfAbsent(prefix + name.substring(prefix.length()).replace('.', '$'), name);
                }
            }
        }
        if (previous != null && names.equals(previous.sourceNames)
            && sources.typeNames().equals(previous.sources.typeNames())) {
            sourceNames = previous.sourceNames;
            hiddenBinaries = previous.hiddenBinaries;
            classStubs = previous.classStubs;
            qualifiedNames = previous.qualifiedNames;
            typeNames = previous.typeNames;
            return;
        }
        sourceNames = Map.copyOf(names);
        Set<String> hidden = new HashSet<>();
        for (String name : binaries.declaredQualifiedNames()) {
            if (ownedBySource(name)) {
                hidden.add(name);
            }
        }
        hiddenBinaries = Set.copyOf(hidden);
        Map<String, ClassStub> filtered = new HashMap<>(binaries.classStubsByQualifiedName());
        hidden.forEach(filtered::remove);
        classStubs = Map.copyOf(filtered);
        Set<String> qualified = new HashSet<>(sourceNames.keySet());
        Set<String> simple = new HashSet<>(sources.typeNames());
        for (String name : binaries.declaredQualifiedNames()) {
            if (!hidden.contains(name)) {
                qualified.add(name);
                int separator = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
                simple.add(name.substring(separator + 1));
            }
        }
        simple.addAll(qualified);
        qualifiedNames = Set.copyOf(qualified);
        typeNames = Set.copyOf(simple);
    }

    /**
     * Combines updated source declarations with the same binaries, sharing binary lookups when
     * the visible type names are unchanged.
     *
     * @param updated source declarations for the new revision
     * @return the combined index for that revision
     */
    public JavaSourcePrecedenceIndex withSources(JavaProjectSemanticIndex updated) {
        return new JavaSourcePrecedenceIndex(updated, binaries, this);
    }

    private boolean ownedBySource(String name) {
        Set<String> visited = new HashSet<>();
        while (name != null && visited.add(name)) {
            if (sourceNames.containsKey(name))
                return true;
            ClassStub stub = binaries.lookupClassStub(name);
            name = stub == null ? null : stub.enclosingClassName();
        }
        return false;
    }

    private boolean visible(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
        return (symbol.ownerQualifiedName() == null || !hiddenBinaries.contains(symbol.ownerQualifiedName()))
            && (symbol.qualifiedName() == null || !hiddenBinaries.contains(symbol.qualifiedName()));
    }

    @Override
    public @Nullable SemanticIndexShard declarationShard(String binaryName, Runnable activityCheck) {
        activityCheck.run();
        if (sourceNames.containsKey(binaryName))
            return sources.declarationShard(binaryName, activityCheck);
        if (hiddenBinaries.contains(binaryName))
            return null;
        var shard = binaries.declarationShard(binaryName, activityCheck);
        // One source-only attachment shard can also declare a type shadowed by workspace source.
        if (shard != null && binaries.lookupClassStub(binaryName) == null) {
            for (var symbol : shard.declarations()) {
                activityCheck.run();
                if (symbol.qualifiedName() != null && hiddenBinaries.contains(symbol.qualifiedName()))
                    return null;
            }
        }
        return shard;
    }

    @Override
    public Set<String> declaredQualifiedNames() {
        return qualifiedNames;
    }
    @Override
    public Set<String> typeNames() {
        return typeNames;
    }
    @Override
    public boolean containsPackage(String name) {
        return sources.containsPackage(name) || binaries.containsPackage(name);
    }
    @Override
    public Map<String, ClassStub> classStubsByQualifiedName() {
        return classStubs;
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupSimpleName(String name) {
        return Stream.concat(sources.lookupSimpleName(name).stream(),
            binaries.lookupSimpleName(name).stream().filter(this::visible)).toList();
    }

    @Override
    public List<String> qualifiedTypeNames(String simpleName) {
        return Stream.concat(sources.qualifiedTypeNames(simpleName).stream(),
            binaries.qualifiedTypeNames(simpleName).stream().filter(name -> !hiddenBinaries.contains(name)))
            .distinct().toList();
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String name) {
        if (name == null)
            return List.of();
        int separator = name.indexOf('#');
        String owner = separator < 0 ? name : name.substring(0, separator);
        String source = sourceNames.get(owner);
        if (source != null)
            return sources.lookupQualifiedName(separator < 0 ? source : source + name.substring(separator));
        if (hiddenBinaries.contains(owner))
            return List.of();
        return Stream.concat(sources.lookupQualifiedName(name).stream(),
            binaries.lookupQualifiedName(name).stream().filter(this::visible)).toList();
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String owner) {
        if (owner == null)
            return List.of();
        if (sourceNames.containsKey(owner))
            return sources.lookupMembers(sourceNames.get(owner));
        return hiddenBinaries.contains(owner) ? List.of() : binaries.lookupMembers(owner);
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String owner, String name) {
        if (owner == null)
            return List.of();
        if (sourceNames.containsKey(owner))
            return sources.lookupMember(sourceNames.get(owner), name);
        return hiddenBinaries.contains(owner) ? List.of() : binaries.lookupMember(owner, name);
    }
}
