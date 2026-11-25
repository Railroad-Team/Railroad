package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

/**
 * Lookup contract for Java types, packages, members, and available binary class stubs.
 */
public interface JavaSymbolIndex {
    /**
     * Returns the symbols visible when inspecting source attached to the given classpath archive.
     *
     * @param archive binary owning the source attachment
     * @param check cancellation check
     * @return immutable symbol index for this attachment
     */
    default JavaSymbolIndex forAttachedSource(Path archive, Runnable check) {
        return this;
    }

    /**
     * Returns captured library sources that have declarations but no matching class file in the attached view.
     */
    default Map<Path, String> attachedSourceTexts() {
        return Map.of();
    }

    /**
     * Looks up one type's declaring document with thread-interruption checks.
     *
     * @param binaryName physical name of the type, with dollars for nested types
     * @return the visible declaration shard, or null when unavailable
     */
    default @Nullable SemanticIndexShard declarationShard(String binaryName) {
        return declarationShard(binaryName, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Returns the declaring document for one JVM binary type name, including member and parameter declarations.
     *
     * @param binaryName physical name of the type, with dollars for nested types
     * @param activityCheck cancellation check for this lookup
     * @return the visible declaration shard, or null when unavailable
     */
    default @Nullable SemanticIndexShard declarationShard(String binaryName, Runnable activityCheck) {
        activityCheck.run();
        return null;
    }

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
        Set<String> names = new LinkedHashSet<>();
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
     * Resolves a source spelling to the physical binary name, preserving names without a binary declaration.
     *
     * @param name qualified type name in source
     * @return the name recorded in the class file, or the supplied name
     */
    default String binaryTypeName(String name) {
        ClassStub stub = lookupClassStub(name);
        return stub == null ? name : stub.getFullName();
    }

    /**
     * Finds indexed symbols sharing an unqualified name.
     *
     * @param simpleName the unqualified symbol name
     * @return the matching symbol descriptors, or an empty list
     */
    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupSimpleName(String simpleName);

    /**
     * Looks up type names without requiring member declarations from classes with a same-named member.
     *
     * @param simpleName unqualified type name
     * @return distinct qualified names of matching type declarations
     */
    default List<String> qualifiedTypeNames(String simpleName) {
        return lookupSimpleName(simpleName).stream().filter(symbol -> switch (symbol.kind()) {
            case JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ENUM, JavaSymbolKind.RECORD,
                JavaSymbolKind.ANNOTATION -> true;
            default -> false;
        }).map(JavaProjectSemanticIndex.SymbolDescriptor::qualifiedName).filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Finds indexed symbols sharing a qualified name.
     *
     * @param qualifiedName the qualified symbol name
     * @return the matching symbol descriptors, or an empty list
     */
    List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String qualifiedName);

    /**
     * Resolves a binary or source type alias to the declaration's qualified name.
     *
     * @param qualifiedName name used by a source reference or binary signature
     * @return declaration name, or the original name when no type is indexed
     */
    default String canonicalTypeName(String qualifiedName) {
        return lookupQualifiedName(qualifiedName).stream()
            .filter(symbol -> switch (symbol.kind()) {
                case JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ENUM, JavaSymbolKind.RECORD,
                    JavaSymbolKind.ANNOTATION -> true;
                default -> false;
            })
            .map(JavaProjectSemanticIndex.SymbolDescriptor::qualifiedName)
            .filter(Objects::nonNull)
            .findFirst().orElse(qualifiedName);
    }

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
