package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.language.index.LanguageFileIndex;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolKind;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

public final class JavaProjectSemanticIndex implements ProjectLanguageIndex<JavaProjectSemanticIndex.SourceFileIndex> {
    private final Map<Path, SourceFileIndex> filesByPath;
    private final Map<String, List<SourceFileIndex>> filesByPackage;
    private final Map<String, List<SymbolDescriptor>> symbolsBySimpleName;
    private final Map<String, List<SymbolDescriptor>> symbolsByQualifiedName;
    private final Map<String, List<SymbolDescriptor>> membersByOwnerQualifiedName;

    private JavaProjectSemanticIndex(Map<Path, SourceFileIndex> filesByPath) {
        this.filesByPath = copyFileMap(filesByPath);
        this.filesByPackage = buildFilesByPackage(this.filesByPath);
        this.symbolsBySimpleName = buildSymbolsBySimpleName(this.filesByPath.values());
        this.symbolsByQualifiedName = buildSymbolsByQualifiedName(this.filesByPath.values());
        this.membersByOwnerQualifiedName = buildMembersByOwnerQualifiedName(this.filesByPath.values());
    }

    public static JavaProjectSemanticIndex empty() {
        return new JavaProjectSemanticIndex(Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<Path, SourceFileIndex> files() {
        return filesByPath;
    }

    public boolean containsFile(Path path) {
        return filesByPath.containsKey(path);
    }

    public Optional<SourceFileIndex> getFile(Path path) {
        return Optional.ofNullable(filesByPath.get(FileUtils.normalizePath(path)));
    }

    public List<SourceFileIndex> getFilesByPackage(String packageName) {
        packageName = normalizeOptionalName(packageName);
        if (packageName == null)
            return List.of();

        return filesByPackage.getOrDefault(packageName, List.of());
    }

    public List<SymbolDescriptor> lookupSimpleName(String simpleName) {
        simpleName = normalizeOptionalName(simpleName);
        if (simpleName == null)
            return List.of();

        return symbolsBySimpleName.getOrDefault(simpleName, List.of());
    }

    public List<SymbolDescriptor> lookupQualifiedName(String qualifiedName) {
        qualifiedName = normalizeOptionalName(qualifiedName);
        if (qualifiedName == null)
            return List.of();

        return symbolsByQualifiedName.getOrDefault(qualifiedName, List.of());
    }

    public List<SymbolDescriptor> lookupMembers(String qualifiedName) {
        qualifiedName = normalizeOptionalName(qualifiedName);
        if (qualifiedName == null)
            return List.of();

        return membersByOwnerQualifiedName.getOrDefault(qualifiedName, List.of());
    }

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
            SourceFileIndex value = Objects.requireNonNull(entry.getValue(), "original contains null value for key: " + entry.getKey());
            copy.put(FileUtils.normalizePath(entry.getKey()), value);
        }

        return Map.copyOf(copy);
    }

    private static <T> Map<String, List<T>> copyListMap(@NotNull Map<String, List<T>> original) {
        original = Objects.requireNonNull(original, "original");
        Map<String, List<T>> copy = new LinkedHashMap<>(original.size());
        for (Map.Entry<String, List<T>> entry : original.entrySet()) {
            List<T> value = Objects.requireNonNull(entry.getValue(), "original contains null value for key: " + entry.getKey());
            copy.put(entry.getKey(), List.copyOf(value));
        }

        return Map.copyOf(copy);
    }

    private static Map<String, List<SourceFileIndex>> buildFilesByPackage(Map<Path, SourceFileIndex> filesByPath) {
        Map<String, List<SourceFileIndex>> index = new LinkedHashMap<>(filesByPath.size());
        for (SourceFileIndex file : filesByPath.values()) {
            if (file.packageName() == null)
                continue;

            index.computeIfAbsent(file.packageName(), $ -> new ArrayList<>()).add(file);
        }

        return copyListMap(index);
    }

    private static Map<String, List<SymbolDescriptor>> buildSymbolsBySimpleName(Iterable<SourceFileIndex> files) {
        Map<String, List<SymbolDescriptor>> index = new LinkedHashMap<>();
        for (SourceFileIndex file : files) {
            for (SymbolDescriptor symbol : file.declaredSymbols()) {
                index.computeIfAbsent(symbol.simpleName(), $ -> new ArrayList<>()).add(symbol);
            }
        }

        return copyListMap(index);
    }

    private static Map<String, List<SymbolDescriptor>> buildSymbolsByQualifiedName(Iterable<SourceFileIndex> files) {
        Map<String, List<SymbolDescriptor>> index = new LinkedHashMap<>();
        for (SourceFileIndex file : files) {
            for (SymbolDescriptor symbol : file.declaredSymbols()) {
                index.computeIfAbsent(symbol.qualifiedName(), $ -> new ArrayList<>()).add(symbol);
            }
        }

        return copyListMap(index);
    }

    private static Map<String, List<SymbolDescriptor>> buildMembersByOwnerQualifiedName(Iterable<SourceFileIndex> files) {
        Map<String, List<SymbolDescriptor>> index = new LinkedHashMap<>();
        for (SourceFileIndex file : files) {
            for (SymbolDescriptor symbol : file.declaredSymbols()) {
                if (symbol.ownerQualifiedName() == null)
                    continue;

                index.computeIfAbsent(symbol.ownerQualifiedName(), $ -> new ArrayList<>()).add(symbol);
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

    public static final class Builder {
        private final Map<Path, SourceFileIndex> filesByPath = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder putFile(@NotNull SourceFileIndex fileIndex) {
            fileIndex = Objects.requireNonNull(fileIndex, "fileIndex");
            filesByPath.put(fileIndex.path(), fileIndex);
            return this;
        }

        public Builder removeFile(Path path) {
            filesByPath.remove(FileUtils.normalizePath(path));
            return this;
        }

        public JavaProjectSemanticIndex build() {
            return new JavaProjectSemanticIndex(filesByPath);
        }
    }

    public record SourceFileIndex(
        Path path,
        @Nullable String packageName,
        List<ImportDescriptor> imports,
        List<SymbolDescriptor> declaredSymbols
    ) implements LanguageFileIndex {
        public SourceFileIndex {
            path = FileUtils.normalizePath(path);
            packageName = normalizeOptionalName(packageName);
            imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
            declaredSymbols = List.copyOf(Objects.requireNonNull(declaredSymbols, "declaredSymbols"));
        }

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

    public record ImportDescriptor(
        String qualifiedName,
        boolean isStatic,
        boolean isWildcard
    ) {
        public ImportDescriptor {
            qualifiedName = requireName(qualifiedName, "qualifiedName");
        }
    }

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
        public SymbolDescriptor {
            kind = Objects.requireNonNull(kind, "kind");
            simpleName = requireName(simpleName, "simpleName");
            qualifiedName = normalizeOptionalName(qualifiedName);
            ownerQualifiedName = normalizeOptionalName(ownerQualifiedName);
            signature = normalizeOptionalName(signature);
            sourceFile = FileUtils.normalizePath(Objects.requireNonNull(sourceFile, "sourceFile"));
        }

        public boolean hasQualifiedName() {
            return qualifiedName != null;
        }

        public boolean isMember() {
            return ownerQualifiedName != null;
        }

        public Optional<String> qualifiedNameOptional() {
            return Optional.ofNullable(qualifiedName);
        }

        public Optional<String> ownerQualifiedNameOptional() {
            return Optional.ofNullable(ownerQualifiedName);
        }
    }
}
