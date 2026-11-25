package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.language.index.LanguageFileIndex;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndex;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.graph.api.WorkspaceSymbolGraph;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolSignature;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolLocation;

/**
 * Immutable source index with defensively copied file and descriptor collections.
 * Published instances support concurrent reads and remain valid when a builder is reused.
 * This index does not yet carry document revision or workspace generation tokens.
 */
public final class JavaProjectSemanticIndex
    implements ProjectLanguageIndex<JavaProjectSemanticIndex.SourceFileIndex>, JavaSymbolIndex {
    private final Map<Path, SourceFileIndex> filesByPath;
    private final Map<String, List<SourceFileIndex>> filesByPackage;
    private final Map<String, List<SymbolDescriptor>> symbolsBySimpleName;
    private final Map<String, List<SymbolDescriptor>> symbolsByQualifiedName;
    private final Map<String, List<SymbolDescriptor>> membersByOwnerQualifiedName;
    private final Set<String> declaredQualifiedNames;
    private final Set<String> typeNames;
    private final WorkspaceSymbolGraph graph;

    private JavaProjectSemanticIndex(Map<Path, SourceFileIndex> filesByPath) {
        this(filesByPath, null);
    }

    private JavaProjectSemanticIndex(Map<Path, SourceFileIndex> filesByPath, WorkspaceSymbolGraph graph) {
        this.filesByPath = copyFileMap(filesByPath);
        this.filesByPackage = buildFilesByPackage(this.filesByPath);
        this.symbolsBySimpleName = buildSymbolsBySimpleName(this.filesByPath.values());
        this.symbolsByQualifiedName = buildSymbolsByQualifiedName(this.filesByPath.values());
        this.membersByOwnerQualifiedName = buildMembersByOwnerQualifiedName(this.filesByPath.values());
        Set<String> qualifiedNames = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (SourceFileIndex file : this.filesByPath.values()) {
            for (SymbolDescriptor symbol : file.declaredSymbols()) {
                if (!isTypeSymbol(symbol.kind()) || symbol.qualifiedName() == null)
                    continue;

                qualifiedNames.add(symbol.qualifiedName());
                names.add(symbol.qualifiedName());
                names.add(symbol.simpleName());
            }
        }

        this.declaredQualifiedNames = Set.copyOf(qualifiedNames);
        this.typeNames = Set.copyOf(names);
        this.graph = graph == null ? WorkspaceSymbolGraph.of(semanticShards()) : graph;
    }

    /**
     * Returns immutable graph facts belonging to this source index capture.
     *
     * @return immutable declaration graph for this capture
     */
    public WorkspaceSymbolGraph graph() {
        return graph;
    }

    /**
     * Returns the semantic facts of every indexed source file.
     *
     * @return the declaration and reference shards of the indexed source files
     */
    public List<SemanticIndexShard> semanticShards() {
        return filesByPath.values().stream().map(SourceFileIndex::semanticShard).filter(Objects::nonNull).toList();
    }

    @Override
    public @Nullable SemanticIndexShard declarationShard(String binaryName, Runnable activityCheck) {
        activityCheck.run();
        for (dev.railroadide.railroad.ide.sst.semantic.api.SymbolDescriptor symbol : graph
            .symbols(JavaSymbolIds.type(binaryName))) {
            for (SymbolLocation location : symbol.locations()) {
                if (location.declaration() != null)
                    return graph.shard(location.declaration().documentId()).orElse(null);
            }
        }

        return null;
    }

    /**
     * Replaces a file and its graph postings together; unrelated immutable shards remain shared.
     *
     * @param file replacement source entry and its graph facts
     * @return new index containing the replacement file and updated graph postings
     */
    public JavaProjectSemanticIndex withFile(SourceFileIndex file) {
        Map<Path, SourceFileIndex> updated = new HashMap<>(filesByPath);
        if (file.semanticShard() != null) {
            updated.entrySet().removeIf(entry -> !entry.getKey().equals(file.path())
                && entry.getValue().semanticShard() != null
                && entry.getValue().semanticShard().documentId().equals(file.semanticShard().documentId()));
        }

        SourceFileIndex previous = updated.put(file.path(), file);
        WorkspaceSymbolGraph updatedGraph = graph;
        if (previous != null && previous.semanticShard() != null
            && (file.semanticShard() == null
                || !previous.semanticShard().documentId().equals(file.semanticShard().documentId()))) {
            updatedGraph = updatedGraph.removeShard(previous.semanticShard().documentId());
        }

        if (file.semanticShard() != null) {
            updatedGraph = updatedGraph.replaceShard(file.semanticShard());
        }

        return new JavaProjectSemanticIndex(updated, updatedGraph);
    }

    /**
     * Removes a file and every graph posting established by it in one immutable transaction.
     *
     * @param path source file path, normalized by the index
     * @return new index without the file or its graph postings
     */
    public JavaProjectSemanticIndex withoutFile(Path path) {
        Map<Path, SourceFileIndex> updated = new HashMap<>(filesByPath);
        SourceFileIndex previous = updated.remove(FileUtils.normalizePath(path));
        WorkspaceSymbolGraph updatedGraph = previous == null || previous.semanticShard() == null
            ? graph
            : graph.removeShard(previous.semanticShard().documentId());
        return new JavaProjectSemanticIndex(updated, updatedGraph);
    }

    /**
     * Creates a project index containing no source files.
     *
     * @return an empty immutable project index
     */
    public static JavaProjectSemanticIndex empty() {
        return new JavaProjectSemanticIndex(Map.of());
    }

    /**
     * Creates a builder for collecting source-file index entries.
     *
     * @return a new empty index builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns all indexed source files keyed by normalized path.
     *
     * @return the immutable file index map
     */
    public Map<Path, SourceFileIndex> files() {
        return filesByPath;
    }

    /**
     * Tests whether the supplied path is present as a key in the file index.
     *
     * @param path the normalized source file path to test
     * @return whether the exact path key is indexed
     */
    public boolean containsFile(Path path) {
        return filesByPath.containsKey(path);
    }

    /**
     * Looks up a source file after normalizing its path.
     *
     * @param path the source file path to find
     * @return the matching entry, or an empty optional if the file is absent
     */
    public Optional<SourceFileIndex> getFile(Path path) {
        return Optional.ofNullable(filesByPath.get(FileUtils.normalizePath(path)));
    }

    /**
     * Finds files declaring the specified named package after trimming the package name.
     *
     * @param packageName the package name to look up
     * @return the matching files, or an empty list for an absent, null, or blank package name
     */
    public List<SourceFileIndex> getFilesByPackage(String packageName) {
        packageName = normalizeOptionalName(packageName);
        if (packageName == null)
            return List.of();

        return filesByPackage.getOrDefault(packageName, List.of());
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
        return !getFilesByPackage(packageName).isEmpty();
    }

    @Override
    public Map<String, ClassStub> classStubsByQualifiedName() {
        return Map.of();
    }

    @Override
    public List<SymbolDescriptor> lookupSimpleName(String simpleName) {
        simpleName = normalizeOptionalName(simpleName);
        if (simpleName == null)
            return List.of();

        return symbolsBySimpleName.getOrDefault(simpleName, List.of());
    }

    @Override
    public List<SymbolDescriptor> lookupQualifiedName(String qualifiedName) {
        qualifiedName = normalizeOptionalName(qualifiedName);
        if (qualifiedName == null)
            return List.of();

        return symbolsByQualifiedName.getOrDefault(qualifiedName, List.of());
    }

    @Override
    public List<SymbolDescriptor> lookupMembers(String qualifiedName) {
        qualifiedName = normalizeOptionalName(qualifiedName);
        if (qualifiedName == null)
            return List.of();

        return membersByOwnerQualifiedName.getOrDefault(qualifiedName, List.of());
    }

    @Override
    public List<SymbolDescriptor> lookupMember(String ownerQualifiedName, String simpleName) {
        ownerQualifiedName = normalizeOptionalName(ownerQualifiedName);
        if (ownerQualifiedName == null)
            return List.of();

        simpleName = normalizeOptionalName(simpleName);
        if (simpleName == null)
            return List.of();

        final String memberSimpleName = simpleName;
        return lookupMembers(ownerQualifiedName).stream()
            .filter(symbol -> symbol.simpleName().equals(memberSimpleName))
            .toList();
    }

    private static Map<Path, SourceFileIndex> copyFileMap(@NotNull Map<Path, SourceFileIndex> original) {
        original = Objects.requireNonNull(original, "original");
        Map<Path, SourceFileIndex> copy = new LinkedHashMap<>(original.size());
        for (Map.Entry<Path, SourceFileIndex> entry : original.entrySet()) {
            SourceFileIndex value = Objects.requireNonNull(entry.getValue(),
                "original contains null value for key: " + entry.getKey());
            copy.put(FileUtils.normalizePath(entry.getKey()), value);
        }

        return Map.copyOf(copy);
    }

    private static <T> Map<String, List<T>> copyListMap(@NotNull Map<String, List<T>> original) {
        original = Objects.requireNonNull(original, "original");
        Map<String, List<T>> copy = new LinkedHashMap<>(original.size());
        for (Map.Entry<String, List<T>> entry : original.entrySet()) {
            List<T> value = Objects.requireNonNull(entry.getValue(),
                "original contains null value for key: " + entry.getKey());
            copy.put(entry.getKey(), List.copyOf(value));
        }

        return Map.copyOf(copy);
    }

    private static Map<String, List<SourceFileIndex>> buildFilesByPackage(Map<Path, SourceFileIndex> filesByPath) {
        Map<String, List<SourceFileIndex>> index = new LinkedHashMap<>(filesByPath.size());
        for (SourceFileIndex file : filesByPath.values()) {
            if (file.packageName() == null)
                continue;

            index.computeIfAbsent(file.packageName(), _ -> new ArrayList<>()).add(file);
        }

        return copyListMap(index);
    }

    private static Map<String, List<SymbolDescriptor>> buildSymbolsBySimpleName(Iterable<SourceFileIndex> files) {
        Map<String, List<SymbolDescriptor>> index = new LinkedHashMap<>();
        for (SourceFileIndex file : files) {
            for (SymbolDescriptor symbol : file.declaredSymbols()) {
                index.computeIfAbsent(symbol.simpleName(), _ -> new ArrayList<>()).add(symbol);
            }
        }

        return copyListMap(index);
    }

    private static Map<String, List<SymbolDescriptor>> buildSymbolsByQualifiedName(Iterable<SourceFileIndex> files) {
        Map<String, List<SymbolDescriptor>> index = new LinkedHashMap<>();
        for (SourceFileIndex file : files) {
            for (SymbolDescriptor symbol : file.declaredSymbols()) {
                index.computeIfAbsent(symbol.qualifiedName(), _ -> new ArrayList<>()).add(symbol);
            }
        }

        return copyListMap(index);
    }

    private static Map<String, List<SymbolDescriptor>> buildMembersByOwnerQualifiedName(
        Iterable<SourceFileIndex> files
    ) {
        Map<String, List<SymbolDescriptor>> index = new LinkedHashMap<>();
        for (SourceFileIndex file : files) {
            for (SymbolDescriptor symbol : file.declaredSymbols()) {
                if (symbol.ownerQualifiedName() == null)
                    continue;

                index.computeIfAbsent(symbol.ownerQualifiedName(), _ -> new ArrayList<>()).add(symbol);
            }
        }

        return copyListMap(index);
    }

    private static @Nullable String normalizeOptionalName(@Nullable String name) {
        if (name == null)
            return null;

        name = name.trim();
        return name.isEmpty() ? null : name;
    }

    private static boolean isTypeSymbol(SymbolKind kind) {
        return switch (kind) {
            case JavaSymbolKind.CLASS, JavaSymbolKind.INTERFACE, JavaSymbolKind.ENUM, JavaSymbolKind.ANNOTATION,
                JavaSymbolKind.RECORD -> true;
            default -> false;
        };
    }

    private static String requireName(String value, String name) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty())
            throw new IllegalArgumentException(name + " cannot be blank");

        return value;
    }

    @Override
    public String languageId() {
        return "java";
    }

    @Override
    public SourceFileIndex getFileIndex(Path path) {
        return filesByPath.get(FileUtils.normalizePath(path));
    }

    /**
     * Thread-confined construction state; each build returns an independent index.
     */
    public static final class Builder {
        private final Map<Path, SourceFileIndex> filesByPath = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds or replaces the index entry for the source file's path.
         *
         * @param fileIndex the source-file entry to store
         * @return this builder
         */
        public Builder putFile(@NotNull SourceFileIndex fileIndex) {
            fileIndex = Objects.requireNonNull(fileIndex, "fileIndex");
            filesByPath.put(fileIndex.path(), fileIndex);
            return this;
        }

        /**
         * Removes the source-file entry at the normalized path, if present.
         *
         * @param path the source file path to remove
         * @return this builder
         */
        public Builder removeFile(Path path) {
            filesByPath.remove(FileUtils.normalizePath(path));
            return this;
        }

        /**
         * Copies the collected files and builds immutable symbol lookup tables.
         *
         * @return the completed project index
         */
        public JavaProjectSemanticIndex build() {
            return new JavaProjectSemanticIndex(filesByPath);
        }
    }

    /**
     * Immutable declarations and imports extracted from one Java source file.
     *
     * @param path the source file path, normalized on construction
     * @param packageName the declared package name, or {@code null} for the unnamed package
     * @param imports the source file's import descriptors
     * @param declaredSymbols the source file's indexed type and member descriptors
     * @param sourceHash SHA-256 of the parsed UTF-8 input, or null when provenance is unavailable
     *
     * @param semanticShard document graph facts, or {@code null} when unavailable
     */
    public record SourceFileIndex(
        Path path,
        @Nullable String packageName,
        List<ImportDescriptor> imports,
        List<SymbolDescriptor> declaredSymbols,
        @Nullable String sourceHash,
        @Nullable SemanticIndexShard semanticShard
    ) implements LanguageFileIndex {
        /**
         * Normalizes the source path and optional package name and copies the import and declaration lists.
         *
         * @param path the source file path, normalized on construction
         * @param packageName the declared package name, or {@code null} for the unnamed package
         * @param imports the source file's import descriptors
         * @param declaredSymbols the source file's indexed type and member descriptors
         * @param sourceHash SHA-256 of the parsed UTF-8 input, or null when provenance is unavailable
         *
         * @param semanticShard document graph facts, or {@code null} when unavailable
         */
        public SourceFileIndex {
            path = FileUtils.normalizePath(path);
            packageName = normalizeOptionalName(packageName);
            imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
            declaredSymbols = List.copyOf(Objects.requireNonNull(declaredSymbols, "declaredSymbols"));
        }

        /**
         * Creates a source entry without document graph facts.
         *
         * @param path source file path, normalized by the index
         * @param packageName declared package, or {@code null} for the unnamed package
         * @param imports import descriptors to copy
         * @param declaredSymbols indexed type and member declarations to copy
         * @param sourceHash SHA-256 of the parsed UTF-8 source, or {@code null} when unavailable
         */
        public SourceFileIndex(
            Path path,
            @Nullable String packageName,
            List<ImportDescriptor> imports,
            List<SymbolDescriptor> declaredSymbols,
            @Nullable String sourceHash
        ) {
            this(path, packageName, imports, declaredSymbols, sourceHash, null);
        }

        /**
         * Creates a declaration entry without persistence provenance.
         *
         * @param path the source file path
         * @param packageName the declared package name
         * @param imports the file's import descriptors
         * @param declaredSymbols the indexed declaration descriptors
         */
        public SourceFileIndex(
            Path path,
            @Nullable String packageName,
            List<ImportDescriptor> imports,
            List<SymbolDescriptor> declaredSymbols
        ) {
            this(path, packageName, imports, declaredSymbols, null);
        }

        /**
         * Collects the nonnull qualified names of every symbol declared by this file.
         *
         * @return an immutable set of declared symbol names
         */
        public Set<String> declaredQualifiedNames() {
            Set<String> names = new LinkedHashSet<>();
            for (SymbolDescriptor symbol : declaredSymbols) {
                if (symbol.qualifiedName() != null) {
                    names.add(symbol.qualifiedName());
                }
            }

            return Set.copyOf(names);
        }
    }

    /**
     * Describes the target and modifiers of a Java import declaration.
     *
     * @param qualifiedName the nonblank qualified import target
     * @param isStatic whether the declaration has the {@code static} modifier
     * @param isWildcard whether the import selects all names from its target
     */
    public record ImportDescriptor(
        String qualifiedName,
        boolean isStatic,
        boolean isWildcard
    ) {
        /**
         * Creates an import descriptor, requiring a nonblank target name.
         *
         * @param qualifiedName the nonblank qualified import target
         * @param isStatic whether the declaration has the {@code static} modifier
         * @param isWildcard whether the import selects all names from its target
         */
        public ImportDescriptor {
            qualifiedName = requireName(qualifiedName, "qualifiedName");
        }
    }

    /**
     * Describes an indexed Java type or member and its source location.
     *
     * @param kind the symbol category
     * @param simpleName the nonblank unqualified symbol name
     * @param qualifiedName the qualified symbol name, or {@code null} when unavailable
     * @param ownerQualifiedName the owning type's qualified name, or {@code null} for a nonmember
     * @param signature the callable signature, or {@code null} when unavailable
     * @param sourceFile the symbol's originating source path
     * @param isStatic whether the declaration has the {@code static} modifier
     * @param isTopLevel whether the symbol is a top-level type declaration
     *
     * @param descriptor authoritative structured declaration facts, or {@code null} when unavailable
     */
    public record SymbolDescriptor(
        @NotNull SymbolKind kind,
        String simpleName,
        @Nullable String qualifiedName,
        @Nullable String ownerQualifiedName,
        @Nullable String signature,
        Path sourceFile,
        boolean isStatic,
        boolean isTopLevel,
        @Nullable dev.railroadide.railroad.ide.sst.semantic.api.SymbolDescriptor descriptor
    ) {
        /**
         * Creates a symbol descriptor, normalizing optional names and the source path.
         *
         * @param kind the symbol category
         * @param simpleName the nonblank unqualified symbol name
         * @param qualifiedName the qualified symbol name, or {@code null} when unavailable
         * @param ownerQualifiedName the owning type's qualified name, or {@code null} for a nonmember
         * @param signature the callable signature, or {@code null} when unavailable
         * @param sourceFile the symbol's originating source path
         * @param isStatic whether the declaration has the {@code static} modifier
         * @param isTopLevel whether the symbol is a top-level type declaration
         *
         * @param descriptor authoritative structured declaration facts, or {@code null} when unavailable
         */
        public SymbolDescriptor {
            if (descriptor != null) {
                simpleName = descriptor.simpleName();
                qualifiedName = descriptor.qualifiedName();
                signature = descriptor.signature() == null || kind != JavaSymbolKind.METHOD
                    && kind != JavaSymbolKind.CONSTRUCTOR
                        ? null
                        : "(" + String.join(",",
                            descriptor.signature().parameters().stream().map(parameter -> parameter.type().displayName()
                                + (parameter.varargs() ? "[]" : "")).toList())
                            + ")";
                isStatic = descriptor.metadata().modifiers().contains("static");
            }

            kind = Objects.requireNonNull(kind, "kind");
            simpleName = requireName(simpleName, "simpleName");
            qualifiedName = normalizeOptionalName(qualifiedName);
            ownerQualifiedName = normalizeOptionalName(ownerQualifiedName);
            signature = normalizeOptionalName(signature);
            sourceFile = FileUtils.normalizePath(Objects.requireNonNull(sourceFile, "sourceFile"));
        }

        /**
         * Creates a source symbol entry without authoritative structured declaration facts.
         *
         * @param kind semantic category of the declaration or relation
         * @param simpleName nonblank unqualified declaration name
         * @param qualifiedName qualified declaration name, or {@code null} when unavailable
         * @param ownerQualifiedName qualified owning type name, or {@code null} for a nonmember
         * @param signature callable signature, or {@code null} when unavailable
         * @param sourceFile originating source path
         * @param isStatic whether the declaration has the {@code static} modifier
         * @param isTopLevel whether this is a top-level type declaration
         */
        public SymbolDescriptor(
            SymbolKind kind,
            String simpleName,
            @Nullable String qualifiedName,
            @Nullable String ownerQualifiedName,
            @Nullable String signature,
            Path sourceFile,
            boolean isStatic,
            boolean isTopLevel
        ) {
            this(kind, simpleName, qualifiedName, ownerQualifiedName, signature, sourceFile, isStatic, isTopLevel,
                null);
        }

        /**
         * The authoritative structured signature when semantic extraction has completed.
         *
         * @return structured signature, or {@code null} when no descriptor or signature is available
         */
        public SymbolSignature structuredSignature() {
            return descriptor == null ? null : descriptor.signature();
        }

        /**
         * Tests whether this symbol has a qualified name.
         *
         * @return whether a qualified name is available
         */
        public boolean hasQualifiedName() {
            return qualifiedName != null;
        }

        /**
         * Tests whether this symbol has an owning type.
         *
         * @return whether an owner qualified name is available
         */
        public boolean isMember() {
            return ownerQualifiedName != null;
        }

        /**
         * Returns the symbol's qualified name when present.
         *
         * @return the qualified name, or an empty optional
         */
        public Optional<String> qualifiedNameOptional() {
            return Optional.ofNullable(qualifiedName);
        }

        /**
         * Returns the owning type's qualified name when present.
         *
         * @return the owner qualified name, or an empty optional
         */
        public Optional<String> ownerQualifiedNameOptional() {
            return Optional.ofNullable(ownerQualifiedName);
        }
    }
}
