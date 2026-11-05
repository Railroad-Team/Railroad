package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.project.*;
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

    private String sourceName(String name) {
        if (name == null)
            return null;
        int member = name.indexOf('#');
        if (member >= 0)
            return sourceNames.getOrDefault(name.substring(0, member), name.substring(0, member))
                + name.substring(member);
        return sourceNames.getOrDefault(name, name);
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
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String name) {
        return Stream.concat(sources.lookupQualifiedName(sourceName(name)).stream(),
            binaries.lookupQualifiedName(name).stream().filter(this::visible)).toList();
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String owner) {
        if (owner == null)
            return List.of();
        if (sourceNames.containsKey(owner))
            return sources.lookupMembers(sourceName(owner));
        return hiddenBinaries.contains(owner) ? List.of() : binaries.lookupMembers(owner);
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String owner, String name) {
        if (owner == null)
            return List.of();
        if (sourceNames.containsKey(owner))
            return sources.lookupMember(sourceName(owner), name);
        return hiddenBinaries.contains(owner) ? List.of() : binaries.lookupMember(owner, name);
    }
}
