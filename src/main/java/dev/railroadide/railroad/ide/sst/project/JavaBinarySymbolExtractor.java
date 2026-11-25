package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.*;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentOrigin;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.graph.api.EdgeKindId;
import dev.railroadide.railroad.ide.sst.graph.api.SemanticIndexShard;
import dev.railroadide.railroad.ide.sst.graph.api.SymbolEdge;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Projects immutable class metadata into the same node-free declarations used by source shards.
 */
public final class JavaBinarySymbolExtractor {
    /**
     * Metadata without annotations depends only on the access flags and declaration kind, and a classpath only uses a
     * few hundred distinct combinations, so every such declaration shares one instance.
     */
    private static final Map<Long, SymbolMetadata> PLAIN_METADATA = new ConcurrentHashMap<>();

    private JavaBinarySymbolExtractor() {
    }

    /**
     * Returns the declaration ID before generic receiver or invocation substitutions are applied.
     *
     * @param owner declaring class metadata
     * @param method method metadata whose declaration identity is required
     * @return canonical binary method identity before invocation substitutions
     */
    public static SymbolId methodId(ClassStub owner, MethodStub method) {
        Map<String, Type> inherited = bounds(owner.typeParameters(), Map.of());
        Map<String, Type> methodBounds = bounds(method.typeParameters(), inherited);
        List<String> parameters = erased(method.parameters(), methodBounds);
        String result = erased(method.returnType(), methodBounds, new HashSet<>());
        for (MethodStub other : owner.methods()) {
            if (other == method || (other.modifiers() & Opcodes.ACC_BRIDGE) != 0 || !other.name().equals(method.name()))
                continue;

            Map<String, Type> otherBounds = bounds(other.typeParameters(), inherited);
            if (parameters.equals(erased(other.parameters(), otherBounds))
                && !result.equals(erased(other.returnType(), otherBounds, new HashSet<>())))
                return JavaSymbolIds.binaryMethod(JavaSymbolIds.type(owner.getFullName()), method.name(), parameters,
                    result);
        }

        return JavaSymbolIds.method(JavaSymbolIds.type(owner.getFullName()), method.name(), parameters);
    }

    /**
     * Returns a binary constructor ID using declaration bounds rather than inferred argument types.
     *
     * @param owner declaring class metadata
     * @param constructor constructor metadata whose declaration identity is required
     * @return canonical constructor identity based on erased declaration bounds
     */
    public static SymbolId constructorId(ClassStub owner, ConstructorStub constructor) {
        return JavaSymbolIds.constructor(JavaSymbolIds.type(owner.getFullName()), erased(constructor.parameters(),
            bounds(constructor.typeParameters(), bounds(owner.typeParameters(), Map.of()))));
    }

    /**
     * Extracts one binary entry. Alternate source/decompiler locations must be supplied by their owner;
     * no source offsets or decompiled buffers are invented. Binary revisions are pinned by configuration.
     *
     * @param stub immutable class metadata to project
     * @param uri exact document address, including any archive entry
     * @param alternates additional source or decompiled locations keyed by declaration identity
     * @param check callback invoked to check cancellation or interruption; may throw to abort
     * @return binary shard including type, member and parameter declarations
     */
    public static SemanticIndexShard extract(
        ClassStub stub,
        DocumentUri uri,
        Map<SymbolId, List<SymbolLocation>> alternates,
        Runnable check
    ) {
        return withParameters(extractDeclarations(stub, uri, alternates, new TypeReferences(), check), alternates,
            check);
    }

    /**
     * Extracts authoritative type/member facts without allocating graph-only parameter declarations.
     *
     * @param stub immutable class metadata to project
     * @param uri exact document address, including any archive entry
     * @param alternates additional source or decompiled locations keyed by declaration identity
     * @param types type references shared with the other classes of the same index
     * @param check callback invoked to check cancellation or interruption; may throw to abort
     * @return binary shard containing type and member declarations without parameter declarations
     */
    public static SemanticIndexShard extractDeclarations(
        ClassStub stub,
        DocumentUri uri,
        Map<SymbolId, List<SymbolLocation>> alternates,
        TypeReferences types,
        Runnable check
    ) {
        check.run();
        String fullName = stub.getFullName();
        SymbolId owner = JavaSymbolIds.type(fullName);
        List<SymbolLocation> location = List
            .of(new SymbolLocation(uri, DocumentOrigin.original(uri), null, null, true));
        List<SymbolDescriptor> declarations = new ArrayList<>();
        Map<String, Type> bounds = bounds(stub.typeParameters(), Map.of());
        JavaSymbolKind kind = (stub.modifiers() & Opcodes.ACC_ANNOTATION) != 0
            ? JavaSymbolKind.ANNOTATION
            : (stub.modifiers() & Opcodes.ACC_ENUM) != 0
                ? JavaSymbolKind.ENUM
                : (stub.modifiers() & Opcodes.ACC_RECORD) != 0
                    ? JavaSymbolKind.RECORD
                    : Modifier.isInterface(stub.modifiers()) ? JavaSymbolKind.INTERFACE : JavaSymbolKind.CLASS;
        declarations.add(descriptor(owner, kind, stub.name(), fullName,
            stub.enclosingClassName() == null
                ? JavaSymbolIds.packageId("", stub.packageName())
                : JavaSymbolIds.type(stub.enclosingClassName()),
            new SymbolSignature(typeParameters(stub.typeParameters(), types), List.of(), null, List.of()),
            types.resolved(fullName), stub.modifiers(), stub.annotations(), location, alternates, types));
        for (FieldStub field : stub.fields()) {
            check.run();
            declarations.add(descriptor(JavaSymbolIds.field(owner, field.name()), JavaSymbolKind.FIELD, field.name(),
                fullName + "#" + field.name(), owner, null, types.type(field.type()), field.modifiers(),
                field.annotations(), location, alternates, types));
        }

        Map<SymbolId, Set<String>> methodReturns = new HashMap<>();
        for (MethodStub method : stub.methods()) {
            check.run();
            if ((method.modifiers() & Opcodes.ACC_BRIDGE) != 0)
                continue;

            Map<String, Type> methodBounds = bounds(method.typeParameters(), bounds);
            SymbolId id = JavaSymbolIds.method(owner, method.name(), erased(method.parameters(), methodBounds));
            methodReturns.computeIfAbsent(id, _ -> new HashSet<>())
                .add(erased(method.returnType(), methodBounds, new HashSet<>()));
        }

        Map<String, String> methodNames = new HashMap<>();
        for (MethodStub method : stub.methods()) {
            check.run();
            if ((method.modifiers() & Opcodes.ACC_BRIDGE) != 0)
                continue;

            SymbolSignature signature = signature(method.parameters(), method.typeParameters(), method.returnType(),
                method.thrownTypes(), method.modifiers(), types);
            Map<String, Type> methodBounds = bounds(method.typeParameters(), bounds);
            List<String> parameterTypes = erased(method.parameters(), methodBounds);
            SymbolId id = JavaSymbolIds.method(owner, method.name(), parameterTypes);
            if (methodReturns.get(id).size() > 1) {
                id = JavaSymbolIds.binaryMethod(owner, method.name(), parameterTypes,
                    erased(method.returnType(), methodBounds, new HashSet<>()));
            }

            String qualified = methodNames.computeIfAbsent(method.name(), name -> fullName + "#" + name);
            declarations.add(descriptor(id, JavaSymbolKind.METHOD, method.name(), qualified, owner, signature,
                types.type(method.returnType()), method.modifiers(), method.annotations(), location, alternates,
                types));
        }

        String constructorName = fullName + "#<init>";
        for (ConstructorStub constructor : stub.constructors()) {
            check.run();
            SymbolSignature signature = signature(constructor.parameters(), constructor.typeParameters(), null,
                constructor.thrownTypes(), constructor.modifiers(), types);
            SymbolId id = constructorId(stub, constructor);
            declarations.add(descriptor(id, JavaSymbolKind.CONSTRUCTOR, stub.name(), constructorName, owner,
                signature, null, constructor.modifiers(), constructor.annotations(), location, alternates, types));
        }

        check.run();
        // Some stub providers identify only the containing archive, so several classes can share the same URI.
        var documentId = new DocumentId(
            UUID.nameUUIDFromBytes((uri + "\0" + fullName).getBytes(StandardCharsets.UTF_8)));
        return new SemanticIndexShard(
            documentId, DocumentVersion.initial(), uri, declarations, List.of(), supertypeEdges(stub, owner, types));
    }

    /**
     * Reads only the direct superclass and interfaces, preserving their generic arguments.
     *
     * @param stub immutable class metadata
     * @param owner identity of the class
     * @param types shared type references
     * @return direct subtype edges from the class
     */
    public static List<SymbolEdge> supertypeEdges(ClassStub stub, SymbolId owner, TypeReferences types) {
        List<Type> supers = new ArrayList<>(stub.interfaces());
        if (stub.superClass() != null) {
            supers.add(stub.superClass());
        }

        List<SymbolEdge> edges = new ArrayList<>();
        for (Type base : supers) {
            if (base instanceof Type.ClassType named) {
                TypeReference reference = types.type(named);
                edges.add(new SymbolEdge(owner, reference.target(), EdgeKindId.SUBTYPE, reference.arguments()));
            }
        }

        return edges;
    }

    /**
     * Enriches a binary declaration shard while retaining the exact published type/member descriptor objects.
     *
     * @param shard document facts to publish
     * @param alternates additional source or decompiled locations keyed by declaration identity
     * @param check callback invoked to check cancellation or interruption; may throw to abort
     * @return shard enriched with parameter declarations while retaining existing type and member descriptors
     */
    public static SemanticIndexShard withParameters(
        SemanticIndexShard shard,
        Map<SymbolId, List<SymbolLocation>> alternates,
        Runnable check
    ) {
        List<SymbolDescriptor> declarations = new ArrayList<>(shard.declarations());
        for (SymbolDescriptor declaration : shard.declarations()) {
            check.run();
            if (declaration.signature() != null) {
                List<SymbolLocation> locations = declaration.locations();
                parameters(declarations, declaration.id(), declaration.signature(),
                    locations.size() == 1 ? locations : List.of(locations.getFirst()), alternates, check);
            }
        }

        check.run();
        return declarations.size() == shard.declarations().size()
            ? shard
            : new SemanticIndexShard(shard.documentId(), shard.documentVersion(), shard.uri(), declarations,
                shard.references(), shard.edges());
    }

    private static void parameters(
        List<SymbolDescriptor> output,
        SymbolId owner,
        SymbolSignature signature,
        List<SymbolLocation> location,
        Map<SymbolId, List<SymbolLocation>> alternates,
        Runnable check
    ) {
        for (int i = 0; i < signature.parameters().size(); i++) {
            check.run();
            SymbolSignature.Parameter parameter = signature.parameters().get(i);
            output.add(descriptor(JavaSymbolIds.parameter(owner, i), JavaSymbolKind.PARAMETER,
                parameter.name().isBlank() ? "arg" + i : parameter.name(), null, owner, null, parameter.type(),
                0, List.of(), location, alternates, null));
        }

        for (int i = 0; i < signature.typeParameters().size(); i++) {
            check.run();
            output.add(descriptor(JavaSymbolIds.typeParameter(owner, i), JavaSymbolKind.TYPE_PARAMETER,
                signature.typeParameters().get(i).name(), null, owner, null, null, 0, List.of(), location, alternates,
                null));
        }
    }

    /**
     * @param location the declaring class location, shared by every declaration without alternate locations
     * @param types shared type references; may be {@code null} when {@code annotations} is empty
     */
    private static SymbolDescriptor descriptor(
        SymbolId id,
        JavaSymbolKind kind,
        String name,
        String qualified,
        SymbolId owner,
        SymbolSignature signature,
        TypeReference type,
        int flags,
        List<AnnotationStub> annotations,
        List<SymbolLocation> location,
        Map<SymbolId, List<SymbolLocation>> alternates,
        TypeReferences types
    ) {
        List<SymbolLocation> locations = location;
        List<SymbolLocation> extra = alternates.get(id);
        if (extra != null && !extra.isEmpty()) {
            locations = new ArrayList<>(location);
            locations.addAll(extra);
        }

        boolean hasSignature = signature != null;
        SymbolMetadata metadata = annotations.isEmpty()
            ? PLAIN_METADATA.computeIfAbsent(metadataKey(kind, flags, hasSignature),
                _ -> metadata(kind, flags, hasSignature, List.of()))
            : metadata(kind, flags, hasSignature, annotations.stream()
                .map(annotation -> types.resolved(annotation.name()))
                .toList());
        return new SymbolDescriptor(id, SymbolKindId.from(kind), name, qualified, owner, signature, type, metadata,
            locations);
    }

    private static long metadataKey(JavaSymbolKind kind, int flags, boolean hasSignature) {
        return (long) modifierMask(kind) << 33 | (hasSignature ? 1L << 32 : 0) | Integer.toUnsignedLong(flags);
    }

    private static int modifierMask(JavaSymbolKind kind) {
        return switch (kind) {
            case METHOD -> Modifier.methodModifiers();
            case CONSTRUCTOR -> Modifier.constructorModifiers();
            case FIELD -> Modifier.fieldModifiers();
            default -> Modifier.classModifiers();
        };
    }

    private static SymbolMetadata metadata(
        JavaSymbolKind kind,
        int flags,
        boolean hasSignature,
        List<TypeReference> annotations
    ) {
        Set<String> modifiers = new LinkedHashSet<>();
        String standard = Modifier.toString(flags & modifierMask(kind));
        if (!standard.isBlank()) {
            modifiers.addAll(List.of(standard.split(" ")));
        }

        if ((flags & Opcodes.ACC_SYNTHETIC) != 0) {
            modifiers.add("synthetic");
        }

        if ((flags & Opcodes.ACC_VARARGS) != 0 && hasSignature) {
            modifiers.remove("transient");
            modifiers.add("varargs");
        }

        String visibility = Modifier.isPublic(flags)
            ? "public"
            : Modifier.isProtected(flags)
                ? "protected"
                : Modifier.isPrivate(flags) ? "private" : "package";
        boolean deprecated = (flags & Opcodes.ACC_DEPRECATED) != 0
            || annotations.stream().anyMatch(annotation -> annotation.name().equals("java.lang.Deprecated"));
        return new SymbolMetadata(modifiers, visibility, deprecated, annotations,
            Map.of("java:jvmAccess", Integer.toString(flags)));
    }

    private static SymbolSignature signature(
        List<Parameter> parameters,
        List<TypeParameter> typeParameters,
        Type result,
        List<Type> thrown,
        int flags,
        TypeReferences types
    ) {
        List<SymbolSignature.Parameter> values = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            boolean varargs = i == parameters.size() - 1 && (flags & Opcodes.ACC_VARARGS) != 0;
            Type parameterType = varargs && parameter.type() instanceof Type.ArrayType array
                ? array.componentType()
                : parameter.type();
            values.add(new SymbolSignature.Parameter(parameter.name() == null ? "arg" + i : parameter.name(),
                types.type(parameterType), varargs));
        }

        return new SymbolSignature(typeParameters(typeParameters, types), values, types.type(result),
            thrown.stream().map(types::type).toList());
    }

    private static List<SymbolSignature.TypeParameter> typeParameters(
        List<TypeParameter> parameters,
        TypeReferences types
    ) {
        return parameters.stream().map(parameter -> new SymbolSignature.TypeParameter(parameter.name(),
            parameter.bounds().stream().map(types::type).toList())).toList();
    }

    private static Map<String, Type> bounds(List<TypeParameter> parameters, Map<String, Type> inherited) {
        Map<String, Type> result = new HashMap<>(inherited);
        for (TypeParameter parameter : parameters) {
            result.put(parameter.name(),
                parameter.bounds().isEmpty()
                    ? new Type.ClassType("java.lang.Object", List.of())
                    : parameter.bounds().getFirst());
        }

        return result;
    }

    private static List<String> erased(List<Parameter> parameters, Map<String, Type> bounds) {
        return parameters.stream().map(parameter -> erased(parameter.type(), bounds, new HashSet<>())).toList();
    }

    private static String erased(Type value, Map<String, Type> bounds, Set<String> visiting) {
        return switch (value) {
            case Type.TypeVariable variable -> visiting.add(variable.name())
                ? erased(bounds.getOrDefault(variable.name(),
                    new Type.ClassType("java.lang.Object", List.of())), bounds, visiting)
                : "java.lang.Object";
            case Type.ArrayType array -> erased(array.componentType(), bounds, visiting) + "[]";
            case Type.ClassType named -> named.name();
            case Type.PrimitiveType primitive -> primitive.name();
            case Type.WildcardType wildcard -> wildcard.isUpperBound() && wildcard.bound() != null
                ? erased(wildcard.bound(), bounds, visiting)
                : "java.lang.Object";
        };
    }

    /**
     * Shares the type references that recur across the classes of one binary index. Raw class types, primitives and
     * type variables make up most references and are shared; parameterized and array types are built per use.
     * Safe for concurrent use.
     */
    public static final class TypeReferences {
        private final Map<String, TypeReference> resolved = new ConcurrentHashMap<>();
        private final Map<String, TypeReference> unresolved = new ConcurrentHashMap<>();

        private TypeReference resolved(String name) {
            return resolved.computeIfAbsent(name, _ -> new TypeReference(name, List.of(), 0, JavaSymbolIds.type(name)));
        }

        private TypeReference unresolved(String name) {
            return unresolved.computeIfAbsent(name, TypeReference::new);
        }

        private TypeReference type(Type value) {
            return switch (value) {
                case null -> null;
                case Type.ClassType named -> named.typeArguments().isEmpty()
                    ? resolved(named.name())
                    : new TypeReference(named.name(), named.typeArguments().stream().map(this::type).toList(), 0,
                        resolved(named.name()).target());
                case Type.PrimitiveType primitive -> unresolved(primitive.name());
                case Type.TypeVariable variable -> unresolved(variable.name());
                case Type.ArrayType array -> {
                    TypeReference component = type(array.componentType());
                    yield new TypeReference(component.name(), component.arguments(), component.arrayDimensions() + 1,
                        component.target());
                }

                case Type.WildcardType wildcard -> wildcard.bound() == null
                    ? unresolved("?")
                    : new TypeReference(wildcard.isUpperBound() ? "? extends" : "? super",
                        List.of(type(wildcard.bound())), 0, null);
            };
        }
    }
}
