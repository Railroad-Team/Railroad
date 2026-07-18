package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;

import java.util.*;

public final class CompositeJavaSymbolIndex implements JavaSymbolIndex {
    private final List<JavaSymbolIndex> delegates;
    private final Set<String> declaredQualifiedNames;
    private final Set<String> typeNames;
    private final Map<String, ClassStub> classStubsByQualifiedName;

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
    public Set<String> declaredQualifiedNames() {
        return declaredQualifiedNames;
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
