package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface JavaSymbolIndex {
    Set<String> declaredQualifiedNames();

    /**
     * All declared type names in both qualified and simple form. Implementations
     * should cache this set because inspection contexts query it for every file.
     */
    default Set<String> typeNames() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (String qualifiedName : declaredQualifiedNames()) {
            names.add(qualifiedName);
            int separator = Math.max(qualifiedName.lastIndexOf('.'), qualifiedName.lastIndexOf('$'));
            names.add(separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1));
        }
        return Set.copyOf(names);
    }

    boolean containsPackage(String packageName);

    Map<String, ClassStub> classStubsByQualifiedName();

    default @Nullable ClassStub lookupClassStub(String qualifiedName) {
        return classStubsByQualifiedName().get(qualifiedName);
    }

    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupSimpleName(String simpleName);

    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String qualifiedName);

    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String qualifiedName);

    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String ownerQualifiedName, String simpleName);
}
