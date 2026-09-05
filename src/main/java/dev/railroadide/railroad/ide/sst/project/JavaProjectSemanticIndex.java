package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.language.index.LanguageFileIndex;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

/**
 * Immutable project source index supporting file, package, type, and member lookups.
 */
public final class JavaProjectSemanticIndex
    implements
        ProjectLanguageIndex<JavaProjectSemanticIndex.SourceFileIndex>,
        JavaSymbolIndex {
    private final Map<Path, SourceFileIndex> filesByPath;
    private final Map<String, List<SourceFileIndex>> filesByPackage;
    private final Map<String, List<SymbolDescriptor>> symbolsBySimpleName;
    private final Map<String, List<SymbolDescriptor>> symbolsByQualifiedName;
    private final Map<String, List<SymbolDescriptor>> membersByOwnerQualifiedName;
    private final Set<String> declaredQualifiedNames;
    private final Set<String> typeNames;

    private JavaProjectSemanticIndex(Map<Path, SourceFileIndex> filesByPath) {
        this.filesByPath = copyFileMap(filesByPath);
        this.filesByPackage = buildFilesByPackage(this.filesByPath);
        this.symbolsBySimpleName = buildSymbolsBySimpleName(this.filesByPath.values());
        this.symbolsByQualifiedName = buildSymbolsByQualifiedName(this.filesByPath.values());
        this.membersByOwnerQualifiedName = buildMembersByOwnerQualifiedName(this.filesByPath.values());
        LinkedHashSet<String> qualifiedNames = new LinkedHashSet<>();
        LinkedHashSet<String> names = new LinkedHashSet<>();
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
            case CLASS, INTERFACE, ENUM, ANNOTATION, RECORD -> true;
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
     * Accumulates source-file entries used to create an immutable project semantic index.
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
     */
    public record SourceFileIndex(
        Path path,
        @Nullable String packageName,
        List<ImportDescriptor> imports,
        List<SymbolDescriptor> declaredSymbols
    ) implements LanguageFileIndex {
        /**
         * Normalizes the source path and optional package name and copies the import and declaration lists.
         *
         * @param path the source file path, normalized on construction
         * @param packageName the declared package name, or {@code null} for the unnamed package
         * @param imports the source file's import descriptors
         * @param declaredSymbols the source file's indexed type and member descriptors
         */
        public SourceFileIndex {
            path = FileUtils.normalizePath(path);
            packageName = normalizeOptionalName(packageName);
            imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
            declaredSymbols = List.copyOf(Objects.requireNonNull(declaredSymbols, "declaredSymbols"));
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
     */
    public record SymbolDescriptor(
        @NotNull SymbolKind kind,
        String simpleName,
        @Nullable String qualifiedName,
        @Nullable String ownerQualifiedName,
        @Nullable String signature,
        Path sourceFile,
        boolean isStatic,
        boolean isTopLevel
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
         */
        public SymbolDescriptor {
            kind = Objects.requireNonNull(kind, "kind");
            simpleName = requireName(simpleName, "simpleName");
            qualifiedName = normalizeOptionalName(qualifiedName);
            ownerQualifiedName = normalizeOptionalName(ownerQualifiedName);
            signature = normalizeOptionalName(signature);
            sourceFile = FileUtils.normalizePath(Objects.requireNonNull(sourceFile, "sourceFile"));
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
