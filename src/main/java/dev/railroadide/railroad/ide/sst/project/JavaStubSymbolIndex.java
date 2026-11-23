package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.classparser.stub.FieldStub;
import dev.railroadide.railroad.ide.classparser.stub.MethodStub;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolLocation;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds Java type and member lookup tables from binary class stubs and their origins.
 */
public class JavaStubSymbolIndex implements JavaSymbolIndex {
    private final Map<SymbolId, List<SymbolLocation>> alternateLocations;
    private final ConcurrentHashMap<String, SemanticIndexShard> shardsByOwner;
    private final Map<String, DocumentUri> binaryLocations;
    private final Set<String> declaredQualifiedNames;
    private final Set<String> typeNames;
    private final Set<String> packages;
    private final Map<String, ClassStub> classStubsByQualifiedName;
    private final Map<String, Path> sourceByQualifiedName;
    private final Map<String, String> aliases;
    private final Map<String, ClassStub> visibleStubs;
    private final Map<String, Object> ownersBySimpleName;
    private final ConcurrentHashMap<String, ClassDeclarations> declarationsByOwner;
    private final JavaBinarySymbolExtractor.TypeReferences typeReferences;

    /** Reuses fully immutable lookup storage when wrapping an already published binary index. */
    protected JavaStubSymbolIndex(JavaStubSymbolIndex source) {
        this(source, source.aliases);
    }

    /** Shares physical declarations and their navigation identities while exposing attachment-local source names. */
    protected JavaStubSymbolIndex(JavaStubSymbolIndex source, Map<String, String> aliases) {
        this.aliases = Map.copyOf(aliases);
        alternateLocations = source.alternateLocations;
        shardsByOwner = source.shardsByOwner;
        binaryLocations = source.binaryLocations;
        if (aliases.equals(source.aliases)) {
            visibleStubs = source.visibleStubs;
            declaredQualifiedNames = source.declaredQualifiedNames;
            typeNames = source.typeNames;
            packages = source.packages;
        } else {
            var visible = new HashMap<>(source.classStubsByQualifiedName);
            var names = new HashSet<>(source.typeNames);
            var packageNames = new HashSet<>(source.packages);
            aliases.forEach((alias, binary) -> {
                visible.put(alias, source.classStubsByQualifiedName.get(binary));
                names.add(alias);
                packageNames.add(alias.substring(0, alias.lastIndexOf('.')));
            });
            visibleStubs = Map.copyOf(visible);
            declaredQualifiedNames = visibleStubs.keySet();
            typeNames = Set.copyOf(names);
            packages = Set.copyOf(packageNames);
        }
        classStubsByQualifiedName = source.classStubsByQualifiedName;
        sourceByQualifiedName = source.sourceByQualifiedName;
        ownersBySimpleName = source.ownersBySimpleName;
        declarationsByOwner = source.declarationsByOwner;
        typeReferences = source.typeReferences;
    }

    /**
     * Copies binary class stubs and builds type, package, and member lookup tables.
     *
     * @param classStubsByQualifiedName the binary class stubs keyed by qualified type name
     * @param sourceByQualifiedName the origin path for every qualified class name in the stub map
     */
    public JavaStubSymbolIndex(
        Map<String, ClassStub> classStubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName
    ) {
        this(classStubsByQualifiedName, sourceByQualifiedName, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Builds immutable lookup tables with cooperative checks during publication.
     *
     * @param classStubsByQualifiedName stubs keyed by qualified type name
     * @param sourceByQualifiedName class origins
     * @param activityCheck request activity check
     */
    public JavaStubSymbolIndex(
        Map<String, ClassStub> classStubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName,
        Runnable activityCheck
    ) {
        this(classStubsByQualifiedName, sourceByQualifiedName, Map.of(), Map.of(), activityCheck);
    }

    /**
     * Publishes binary declarations with exact entry addresses and owner-supplied alternate locations.
     *
     * @param classStubsByQualifiedName class metadata keyed by qualified type name
     * @param sourceByQualifiedName source paths keyed by qualified type name
     * @param binaryLocations exact binary entry addresses keyed by qualified type name
     * @param alternateLocations alternate locations keyed by symbol identity
     * @param activityCheck callback invoked to check cancellation or interruption; may throw to abort
     */
    public JavaStubSymbolIndex(
        Map<String, ClassStub> classStubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName,
        Map<String, DocumentUri> binaryLocations,
        Map<SymbolId, List<SymbolLocation>> alternateLocations,
        Runnable activityCheck
    ) {
        this(classStubsByQualifiedName, sourceByQualifiedName, binaryLocations, alternateLocations, activityCheck,
            true);
    }

    /**
     * Constructs lookup storage, optionally reusing stubs from an already deeply immutable publication.
     * The trusted path is restricted to internal subclasses; external mutable inputs must be copied.
     */
    protected JavaStubSymbolIndex(
        Map<String, ClassStub> classStubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName,
        Map<String, DocumentUri> binaryLocations,
        Map<SymbolId, List<SymbolLocation>> alternateLocations,
        Runnable activityCheck,
        boolean copyStubs
    ) {
        Objects.requireNonNull(activityCheck, "activityCheck").run();
        Map<SymbolId, List<SymbolLocation>> alternatives = new LinkedHashMap<>();
        alternateLocations.forEach((id, entries) -> alternatives.put(id, List.copyOf(entries)));
        this.alternateLocations = Map.copyOf(alternatives);
        Map<String, DocumentUri> locations = new LinkedHashMap<>();
        Map<Path, DocumentUri> originLocations = new HashMap<>();
        classStubsByQualifiedName.forEach((name, stub) -> {
            ActivityCheckpoint.poll(activityCheck);
            DocumentUri location = binaryLocations.get(name);
            if (location == null) {
                location = originLocations.computeIfAbsent(sourceByQualifiedName.get(name), DocumentUri::fromPath);
            }
            locations.put(name, location);
        });
        this.binaryLocations = Map.copyOf(locations);
        Objects.requireNonNull(classStubsByQualifiedName, "classStubsByQualifiedName");
        Map<String, ClassStub> published = classStubsByQualifiedName;
        if (copyStubs) {
            published = new LinkedHashMap<>();
            for (Map.Entry<String, ClassStub> entry : classStubsByQualifiedName.entrySet()) {
                ActivityCheckpoint.poll(activityCheck);
                published.put(entry.getKey(), BinaryStubPublication.copy(entry.getValue()));
            }
        }
        this.classStubsByQualifiedName = Map.copyOf(published);
        this.aliases = Map.of();
        this.visibleStubs = this.classStubsByQualifiedName;
        this.sourceByQualifiedName = Map.copyOf(sourceByQualifiedName);
        this.declaredQualifiedNames = this.classStubsByQualifiedName.keySet();
        declarationsByOwner = new ConcurrentHashMap<>();
        shardsByOwner = new ConcurrentHashMap<>();
        typeReferences = new JavaBinarySymbolExtractor.TypeReferences();

        Set<String> typeNames = new HashSet<>();
        Set<String> packages = new HashSet<>();
        Map<String, Object> ownersBySimpleName = new HashMap<>();

        for (Map.Entry<String, ClassStub> entry : published.entrySet()) {
            ActivityCheckpoint.poll(activityCheck);
            String qualifiedName = entry.getKey();
            ClassStub stub = entry.getValue();
            if (this.sourceByQualifiedName.get(qualifiedName) == null)
                throw new NullPointerException("Missing source for " + qualifiedName);

            typeNames.add(qualifiedName);
            int typeNameSeparator = Math.max(qualifiedName.lastIndexOf('.'), qualifiedName.lastIndexOf('$'));
            typeNames.add(typeNameSeparator < 0 ? qualifiedName : qualifiedName.substring(typeNameSeparator + 1));
            if (stub.packageName() != null && !stub.packageName().isBlank()) {
                packages.add(stub.packageName());
            }

            indexOwner(ownersBySimpleName, stub.name(), qualifiedName);
            for (FieldStub field : stub.fields()) {
                ActivityCheckpoint.poll(activityCheck);
                indexOwner(ownersBySimpleName, field.name(), qualifiedName);
            }
            for (MethodStub method : stub.methods()) {
                ActivityCheckpoint.poll(activityCheck);
                if ((method.modifiers() & Opcodes.ACC_BRIDGE) == 0) {
                    indexOwner(ownersBySimpleName, method.name(), qualifiedName);
                }
            }
        }

        this.typeNames = Set.copyOf(typeNames);
        this.packages = Set.copyOf(packages);
        ownersBySimpleName.replaceAll((_, owners) -> owners instanceof List<?> list ? List.copyOf(list) : owners);
        this.ownersBySimpleName = Map.copyOf(ownersBySimpleName);
        activityCheck.run();
    }

    @Override
    public @Nullable SemanticIndexShard declarationShard(String binaryName, Runnable activityCheck) {
        activityCheck.run();
        if (!classStubsByQualifiedName.containsKey(binaryName))
            return null;
        var shard = shardsByOwner.computeIfAbsent(binaryName, name -> JavaBinarySymbolExtractor.withParameters(
            declarations(name, activityCheck).shard(), alternateLocations, activityCheck));
        activityCheck.run();
        return shard;
    }

    /**
     * Returns exact class-entry addresses retained by the scanner.
     *
     * @return immutable mapping of qualified class names to exact binary entry addresses
     */
    public Map<String, DocumentUri> binaryLocations() {
        return binaryLocations;
    }

    /** Returns the original class paths for preserving navigation in derived library views. */
    protected Map<String, Path> binaryOrigins() {
        return sourceByQualifiedName;
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
        return packageName != null && packages.contains(packageName.trim());
    }

    @Override
    public Map<String, ClassStub> classStubsByQualifiedName() {
        return visibleStubs;
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupSimpleName(String simpleName) {
        if (simpleName == null || simpleName.isBlank())
            return List.of();
        String name = simpleName.trim();
        List<JavaProjectSemanticIndex.SymbolDescriptor> matches = new ArrayList<>();
        for (String owner : owners(name)) {
            ActivityCheckpoint.checkInterrupted();
            for (var descriptor : declarations(owner).symbols()) {
                if (descriptor.simpleName().equals(name)) {
                    matches.add(descriptor);
                }
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public List<String> qualifiedTypeNames(String simpleName) {
        if (simpleName == null || simpleName.isBlank())
            return List.of();
        String name = simpleName.trim();
        return owners(name).stream().filter(owner -> classStubsByQualifiedName.get(owner).name().equals(name))
            .toList();
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupQualifiedName(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank())
            return List.of();
        String name = qualifiedName.trim();
        int separator = name.indexOf('#');
        String owner = separator < 0 ? name : name.substring(0, separator);
        String binary = aliases.getOrDefault(owner, owner);
        if (!classStubsByQualifiedName.containsKey(binary))
            return List.of();
        String target = separator < 0 ? binary : binary + name.substring(separator);
        return declarations(binary).symbols().stream().filter(symbol -> target.equals(symbol.qualifiedName())).toList();
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMembers(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank())
            return List.of();
        String name = qualifiedName.trim();
        String owner = aliases.getOrDefault(name, name);
        return classStubsByQualifiedName.containsKey(owner) ? declarations(owner).members() : List.of();
    }

    @Override
    public List<JavaProjectSemanticIndex.SymbolDescriptor> lookupMember(String ownerQualifiedName, String simpleName) {
        return lookupMembers(ownerQualifiedName).stream()
            .filter(symbol -> symbol.simpleName().equals(simpleName))
            .toList();
    }

    /**
     * Records {@code owner} as declaring {@code key}. Most names have a single owner, which is stored as the string
     * itself; a name shared by several owners is stored as a list. A class adds all of its names before the next
     * class adds any, so a repeated owner is always the last one recorded.
     */
    @SuppressWarnings("unchecked")
    private static void indexOwner(Map<String, Object> index, String key, String owner) {
        if (key == null || key.isBlank())
            return;

        Object existing = index.putIfAbsent(key, owner);
        if (existing == null || existing.equals(owner))
            return;

        if (existing instanceof String single) {
            List<String> owners = new ArrayList<>(2);
            owners.add(single);
            owners.add(owner);
            index.put(key, owners);
        } else {
            List<String> owners = (List<String>) existing;
            if (!owners.getLast().equals(owner)) {
                owners.add(owner);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> owners(String simpleName) {
        return switch (ownersBySimpleName.get(simpleName)) {
            case null -> List.of();
            case String owner -> List.of(owner);
            case Object owners -> (List<String>) owners;
        };
    }

    private ClassDeclarations declarations(String owner) {
        return declarations(owner, ActivityCheckpoint::checkInterrupted);
    }

    private ClassDeclarations declarations(String owner, Runnable activityCheck) {
        activityCheck.run();
        ClassDeclarations result = declarationsByOwner.computeIfAbsent(owner, name -> {
            var shard = JavaBinarySymbolExtractor.extractDeclarations(classStubsByQualifiedName.get(name),
                binaryLocations.get(name), alternateLocations, typeReferences, activityCheck);
            List<JavaProjectSemanticIndex.SymbolDescriptor> symbols = new ArrayList<>();
            List<JavaProjectSemanticIndex.SymbolDescriptor> members = new ArrayList<>();
            SymbolId typeId = JavaSymbolIds.type(name);
            for (var declaration : shard.declarations()) {
                activityCheck.run();
                JavaSymbolKind kind = JavaSymbolKind.fromId(declaration.kind().id());
                boolean type = declaration.id().equals(typeId);
                var descriptor = new JavaProjectSemanticIndex.SymbolDescriptor(kind, declaration.simpleName(),
                    declaration.qualifiedName(), type ? null : name, null, sourceByQualifiedName.get(name),
                    declaration.metadata().modifiers().contains("static"), type, declaration);
                symbols.add(descriptor);
                if (!type) {
                    members.add(descriptor);
                }
            }
            activityCheck.run();
            return new ClassDeclarations(shard, List.copyOf(symbols), List.copyOf(members));
        });
        activityCheck.run();
        return result;
    }

    private record ClassDeclarations(
        SemanticIndexShard shard,
        List<JavaProjectSemanticIndex.SymbolDescriptor> symbols,
        List<JavaProjectSemanticIndex.SymbolDescriptor> members
    ) {
    }
}
