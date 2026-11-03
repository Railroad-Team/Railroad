package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.project.*;
import java.util.*;
import java.util.stream.Stream;

/** Prevents an older binary declaration from supplying members for a captured source type. */
public final class JavaSourcePrecedenceIndex implements JavaSymbolIndex {
    private final JavaProjectSemanticIndex sources;
    private final JavaSymbolIndex binaries;
    private final Map<String, ClassStub> classStubs;
    private final JavaSymbolIndex combined;

    /**
     * Combines symbols while giving captured source declarations precedence over their binaries.
     *
     * @param sources captured source declarations
     * @param binaries pinned library and JDK declarations
     */
    public JavaSourcePrecedenceIndex(JavaProjectSemanticIndex sources, JavaSymbolIndex binaries) {
        this.sources = sources;
        this.binaries = binaries;
        Map<String, ClassStub> filtered = new HashMap<>();
        binaries.classStubsByQualifiedName().forEach((name, stub) -> {
            if (!sourceType(name)) {
                filtered.put(name, stub);
            }
        });
        classStubs = Map.copyOf(filtered);
        combined = new CompositeJavaSymbolIndex(List.of(sources, binaries));
    }

    private boolean sourceType(String name) {
        return name != null && (sources.declaredQualifiedNames().contains(name)
            || sources.declaredQualifiedNames().contains(name.replace('$', '.')));
    }

    private boolean visible(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
        return !sourceType(symbol.ownerQualifiedName()) && !sourceType(symbol.qualifiedName());
    }

    @Override
    public Set<String> declaredQualifiedNames() {
        return combined.declaredQualifiedNames();
    }
    @Override
    public Set<String> typeNames() {
        return combined.typeNames();
    }
    @Override
    public boolean containsPackage(String name) {
        return combined.containsPackage(name);
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
        return Stream.concat(sources.lookupQualifiedName(name).stream(),
            binaries.lookupQualifiedName(name).stream().filter(this::visible)).toList();
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String owner) {
        return sourceType(owner) ? sources.lookupMembers(owner) : binaries.lookupMembers(owner);
    }
    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String owner, String name) {
        return sourceType(owner) ? sources.lookupMember(owner, name) : binaries.lookupMember(owner, name);
    }
}
