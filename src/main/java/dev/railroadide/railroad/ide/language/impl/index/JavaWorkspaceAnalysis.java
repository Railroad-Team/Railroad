package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.project.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Immutable Java analysis inputs derived from one workspace capture. Source lookup never
 * falls back to disk; library and JDK symbols are pinned for the captured configuration.
 *
 * @param sourceIndex declarations extracted from captured Java documents
 * @param symbols combined captured source and immutable binary symbols
 * @param sourceTexts captured source text indexed by declaration paths
 * @param classpath ordered binary dependency roots from the captured configuration
 * @param jdkHome configured JDK home, or the running JDK when unspecified
 */
public record JavaWorkspaceAnalysis(
    JavaProjectSemanticIndex sourceIndex,
    JavaSymbolIndex symbols,
    Map<Path, String> sourceTexts,
    List<Path> classpath,
    Path jdkHome
) implements JavaSourceSnapshotIndex {
    /**
     * Copies source text and dependency paths so later owners cannot change request inputs.
     *
     * @param sourceIndex captured source declarations
     * @param symbols immutable combined symbols
     * @param sourceTexts captured source text
     * @param classpath ordered dependency roots
     * @param jdkHome selected JDK home
     */
    public JavaWorkspaceAnalysis {
        Objects.requireNonNull(sourceIndex);
        Objects.requireNonNull(symbols);
        sourceTexts = Map.copyOf(sourceTexts);
        classpath = List.copyOf(classpath);
        Objects.requireNonNull(jdkHome);
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
