package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface JavaSymbolIndex {
    Set<String> declaredQualifiedNames();

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
