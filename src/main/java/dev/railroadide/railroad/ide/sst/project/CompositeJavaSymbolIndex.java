package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

/**
 * Combines symbol indexes, deduplicating lookup results and preferring earlier delegates for binary class stubs.
 */
public final class CompositeJavaSymbolIndex implements JavaSymbolIndex {
    private final List<JavaSymbolIndex> delegates;
    private final JavaSymbolIndex unscoped;
    private final Map<Path, String> attachedSourceTexts;
    private final Set<String> declaredQualifiedNames;
    private final Set<String> typeNames;
    private final Map<String, ClassStub> classStubsByQualifiedName;

    /**
     * Copies the delegates and merges their type-name and class-stub indexes in precedence order.
     *
     * @param delegates the indexes to combine, ordered from highest to lowest binary-stub precedence
     */
    public CompositeJavaSymbolIndex(List<? extends JavaSymbolIndex> delegates) {
        this(delegates, null, Map.of());
    }

    /**
     * Combines an attachment's declarations and captured texts, using the base index when switching archives.
     */
    public CompositeJavaSymbolIndex(
        List<? extends JavaSymbolIndex> delegates,
        JavaSymbolIndex unscoped,
        Map<Path, String> sourceTexts
    ) {
        this.unscoped = unscoped;
        Map<Path, String> texts = new HashMap<>(sourceTexts);
        this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates"));
        Set<String> qualifiedNames = new LinkedHashSet<>();
        Set<String> typeNames = new LinkedHashSet<>();
        Map<String, ClassStub> classStubs = new LinkedHashMap<>();
        for (JavaSymbolIndex delegate : this.delegates) {
            delegate.attachedSourceTexts().forEach(texts::putIfAbsent);
            qualifiedNames.addAll(delegate.declaredQualifiedNames());
            typeNames.addAll(delegate.typeNames());
            delegate.classStubsByQualifiedName().forEach(classStubs::putIfAbsent);
        }

        attachedSourceTexts = Map.copyOf(texts);
        this.declaredQualifiedNames = Set.copyOf(qualifiedNames);
        this.typeNames = Set.copyOf(typeNames);
        this.classStubsByQualifiedName = Map.copyOf(classStubs);
    }

    @Override
    public @Nullable SemanticIndexShard declarationShard(String binaryName, Runnable activityCheck) {
        activityCheck.run();
        for (JavaSymbolIndex delegate : delegates) {
            SemanticIndexShard shard = delegate.declarationShard(binaryName, activityCheck);
            if (shard != null)
                return shard;
        }

        return null;
    }

    @Override
    public Set<String> declaredQualifiedNames() {
        return declaredQualifiedNames;
    }

    @Override
    public JavaSymbolIndex forAttachedSource(Path archive, Runnable check) {
        if (unscoped != null)
            return unscoped.forAttachedSource(archive, check);

        List<JavaSymbolIndex> scoped = delegates.stream().map(delegate -> delegate.forAttachedSource(archive, check))
            .toList();
        return scoped.equals(delegates) ? this : new CompositeJavaSymbolIndex(scoped);
    }

    @Override
    public Map<Path, String> attachedSourceTexts() {
        return attachedSourceTexts;
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
    public List<String> qualifiedTypeNames(String simpleName) {
        return delegates.stream().flatMap(delegate -> delegate.qualifiedTypeNames(simpleName).stream())
            .distinct().toList();
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
