package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Lookup contract for Java types, packages, members, and available binary class stubs.
 */
public interface JavaSymbolIndex {
    /**
     * Returns qualified names of types declared by this index.
     *
     * @return the indexed qualified type names
     */
    Set<String> declaredQualifiedNames();

    /**
     * All declared type names in both qualified and simple form. Implementations
     * should cache this set because inspection contexts query it for every file.
     *
     * @return the indexed type names in qualified and simple forms
     */
    default Set<String> typeNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String qualifiedName : declaredQualifiedNames()) {
            names.add(qualifiedName);
            int separator = Math.max(qualifiedName.lastIndexOf('.'), qualifiedName.lastIndexOf('$'));
            names.add(separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1));
        }
        return Set.copyOf(names);
    }

    /**
     * Tests whether this index contains the requested package.
     *
     * @param packageName the package name to find
     * @return whether the package is represented in the index
     */
    boolean containsPackage(String packageName);

    /**
     * Returns available binary class stubs keyed by qualified type name.
     *
     * @return the binary class-stub map; source-only indexes may return an empty map
     */
    Map<String, ClassStub> classStubsByQualifiedName();

    /**
     * Looks up a binary class stub by qualified type name.
     *
     * @param qualifiedName the qualified type name to find
     * @return the matching stub, or {@code null} if unavailable
     */
    default @Nullable ClassStub lookupClassStub(String qualifiedName) {
        return classStubsByQualifiedName().get(qualifiedName);
    }

    /**
     * Finds indexed symbols sharing an unqualified name.
     *
     * @param simpleName the unqualified symbol name
     * @return the matching symbol descriptors, or an empty list
     */
    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupSimpleName(String simpleName);

    /**
     * Finds indexed symbols sharing a qualified name.
     *
     * @param qualifiedName the qualified symbol name
     * @return the matching symbol descriptors, or an empty list
     */
    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String qualifiedName);

    /**
     * Finds all indexed members declared by a type.
     *
     * @param qualifiedName the qualified name of the owning type
     * @return the type's indexed member descriptors, or an empty list
     */
    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String qualifiedName);

    /**
     * Finds indexed members of a type with a particular unqualified name.
     *
     * @param ownerQualifiedName the qualified name of the owning type
     * @param simpleName the unqualified member name
     * @return the matching member descriptors, including overloads, or an empty list
     */
    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String ownerQualifiedName, String simpleName);
}
