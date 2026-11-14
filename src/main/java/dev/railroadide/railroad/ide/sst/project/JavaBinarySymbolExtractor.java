package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.graph.api.*;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Projects immutable class metadata into the same node-free declarations used by source shards. */
public final class JavaBinarySymbolExtractor {
    private JavaBinarySymbolExtractor() {
    }

    /** Returns the declaration ID before generic receiver or invocation substitutions are applied. */
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

    /** Returns a binary constructor ID using declaration bounds rather than inferred argument types. */
    public static SymbolId constructorId(ClassStub owner, ConstructorStub constructor) {
        return JavaSymbolIds.constructor(JavaSymbolIds.type(owner.getFullName()), erased(constructor.parameters(),
            bounds(constructor.typeParameters(), bounds(owner.typeParameters(), Map.of()))));
    }

    /**
     * Extracts one binary entry. Alternate source/decompiler locations must be supplied by their owner;
     * no source offsets or decompiled buffers are invented. Binary revisions are pinned by configuration.
     */
    public static SemanticIndexShard extract(
        ClassStub stub,
        DocumentUri uri,
        Map<SymbolId, List<SymbolLocation>> alternates,
        Runnable check
    ) {
        return withParameters(extractDeclarations(stub, uri, alternates, check), alternates, check);
    }

    /** Extracts authoritative type/member facts without allocating graph-only parameter declarations. */
    public static SemanticIndexShard extractDeclarations(
        ClassStub stub,
        DocumentUri uri,
        Map<SymbolId, List<SymbolLocation>> alternates,
        Runnable check
    ) {
        check.run();
        SymbolId owner = JavaSymbolIds.type(stub.getFullName());
        var location = new SymbolLocation(uri, DocumentOrigin.original(uri), null, null, true);
        List<SymbolDescriptor> declarations = new ArrayList<>();
        List<SymbolEdge> edges = new ArrayList<>();
        Map<String, Type> bounds = bounds(stub.typeParameters(), Map.of());
        JavaSymbolKind kind = (stub.modifiers() & Opcodes.ACC_ANNOTATION) != 0
            ? JavaSymbolKind.ANNOTATION
            : (stub.modifiers() & Opcodes.ACC_ENUM) != 0
                ? JavaSymbolKind.ENUM
                : (stub.modifiers() & Opcodes.ACC_RECORD) != 0
                    ? JavaSymbolKind.RECORD
                    : Modifier.isInterface(stub.modifiers()) ? JavaSymbolKind.INTERFACE : JavaSymbolKind.CLASS;
        declarations.add(descriptor(owner, kind, stub.name(), stub.getFullName(),
            stub.enclosingClassName() == null
                ? JavaSymbolIds.packageId("", stub.packageName())
                : JavaSymbolIds.type(stub.enclosingClassName()),
            new SymbolSignature(typeParameters(stub.typeParameters()), List.of(), null, List.of()),
            type(new Type.ClassType(stub.getFullName(), List.of())), stub.modifiers(), stub.annotations(), location,
            alternates));
        List<Type> supers = new ArrayList<>(stub.interfaces());
        if (stub.superClass() != null) {
            supers.add(stub.superClass());
        }
        for (Type base : supers) {
            if (base instanceof Type.ClassType named) {
                edges.add(new SymbolEdge(owner, JavaSymbolIds.type(named.name()), EdgeKindId.SUBTYPE,
                    type(base).arguments()));
            }
        }
        for (FieldStub field : stub.fields()) {
            check.run();
            declarations.add(descriptor(JavaSymbolIds.field(owner, field.name()), JavaSymbolKind.FIELD, field.name(),
                stub.getFullName() + "#" + field.name(), owner, null, type(field.type()), field.modifiers(),
                field.annotations(), location, alternates));
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
        for (MethodStub method : stub.methods()) {
            check.run();
            if ((method.modifiers() & Opcodes.ACC_BRIDGE) != 0)
                continue;
            SymbolSignature signature = signature(method.parameters(), method.typeParameters(), method.returnType(),
                method.thrownTypes(), method.modifiers());
            Map<String, Type> methodBounds = bounds(method.typeParameters(), bounds);
            List<String> parameterTypes = erased(method.parameters(), methodBounds);
            SymbolId id = JavaSymbolIds.method(owner, method.name(), parameterTypes);
            if (methodReturns.get(id).size() > 1) {
                id = JavaSymbolIds.binaryMethod(owner, method.name(), parameterTypes,
                    erased(method.returnType(), methodBounds, new HashSet<>()));
            }
            declarations
                .add(descriptor(id, JavaSymbolKind.METHOD, method.name(), stub.getFullName() + "#" + method.name(),
                    owner, signature, type(method.returnType()), method.modifiers(), method.annotations(), location,
                    alternates));
        }
        for (ConstructorStub constructor : stub.constructors()) {
            check.run();
            SymbolSignature signature = signature(constructor.parameters(), constructor.typeParameters(), null,
                constructor.thrownTypes(), constructor.modifiers());
            SymbolId id = constructorId(stub, constructor);
            declarations.add(descriptor(id, JavaSymbolKind.CONSTRUCTOR, stub.name(), stub.getFullName() + "#<init>",
                owner, signature, null, constructor.modifiers(), constructor.annotations(), location, alternates));
        }
        check.run();
        return new SemanticIndexShard(
            new DocumentId(UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8))),
            DocumentVersion.initial(), uri, declarations, List.of(), edges);
    }

    /** Enriches a binary declaration shard while retaining the exact published type/member descriptor objects. */
    public static SemanticIndexShard withParameters(
        SemanticIndexShard shard,
        Map<SymbolId, List<SymbolLocation>> alternates,
        Runnable check
    ) {
        List<SymbolDescriptor> declarations = new ArrayList<>(shard.declarations());
        for (var declaration : shard.declarations()) {
            check.run();
            if (declaration.signature() != null) {
                parameters(declarations, declaration.id(), declaration.signature(), declaration.locations().getFirst(),
                    alternates, check);
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
        SymbolLocation location,
        Map<SymbolId, List<SymbolLocation>> alternates,
        Runnable check
    ) {
        for (int i = 0; i < signature.parameters().size(); i++) {
            check.run();
            var parameter = signature.parameters().get(i);
            output.add(descriptor(JavaSymbolIds.parameter(owner, i), JavaSymbolKind.PARAMETER,
                parameter.name().isBlank() ? "arg" + i : parameter.name(), null, owner, null, parameter.type(),
                0, List.of(), location, alternates));
        }
        for (int i = 0; i < signature.typeParameters().size(); i++) {
            check.run();
            output.add(descriptor(JavaSymbolIds.typeParameter(owner, i), JavaSymbolKind.TYPE_PARAMETER,
                signature.typeParameters().get(i).name(), null, owner, null, null, 0, List.of(), location, alternates));
        }
    }

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
        SymbolLocation location,
        Map<SymbolId, List<SymbolLocation>> alternates
    ) {
        List<SymbolLocation> locations = new ArrayList<>();
        locations.add(location);
        locations.addAll(alternates.getOrDefault(id, List.of()));
        Set<String> modifiers = new LinkedHashSet<>();
        int modifierMask = switch (kind) {
            case METHOD -> Modifier.methodModifiers();
            case CONSTRUCTOR -> Modifier.constructorModifiers();
            case FIELD -> Modifier.fieldModifiers();
            default -> Modifier.classModifiers();
        };
        String standard = Modifier.toString(flags & modifierMask);
        if (!standard.isBlank()) {
            modifiers.addAll(List.of(standard.split(" ")));
        }
        if ((flags & Opcodes.ACC_SYNTHETIC) != 0) {
            modifiers.add("synthetic");
        }
        if ((flags & Opcodes.ACC_VARARGS) != 0 && signature != null) {
            modifiers.remove("transient");
            modifiers.add("varargs");
        }
        List<TypeReference> annotationTypes = annotations.stream().map(annotation -> type(
            new Type.ClassType(annotation.name(), List.of()))).toList();
        String visibility = Modifier.isPublic(flags)
            ? "public"
            : Modifier.isProtected(flags)
                ? "protected"
                : Modifier.isPrivate(flags) ? "private" : "package";
        var metadata = new SymbolMetadata(modifiers, visibility, (flags & Opcodes.ACC_DEPRECATED) != 0
            || annotations.stream().anyMatch(annotation -> annotation.name().equals("java.lang.Deprecated")),
            annotationTypes, Map.of("java:jvmAccess", Integer.toString(flags)));
        return new SymbolDescriptor(id, SymbolKindId.from(kind), name, qualified, owner, signature, type, metadata,
            locations);
    }

    private static SymbolSignature signature(
        List<Parameter> parameters,
        List<TypeParameter> typeParameters,
        Type result,
        List<Type> thrown,
        int flags
    ) {
        List<SymbolSignature.Parameter> values = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            boolean varargs = i == parameters.size() - 1 && (flags & Opcodes.ACC_VARARGS) != 0;
            Type parameterType = varargs && parameter.type() instanceof Type.ArrayType array
                ? array.componentType()
                : parameter.type();
            values.add(new SymbolSignature.Parameter(parameter.name() == null ? "arg" + i : parameter.name(),
                type(parameterType), varargs));
        }
        return new SymbolSignature(typeParameters(typeParameters), values, result == null ? null : type(result),
            thrown.stream().map(JavaBinarySymbolExtractor::type).toList());
    }

    private static List<SymbolSignature.TypeParameter> typeParameters(List<TypeParameter> parameters) {
        return parameters.stream().map(parameter -> new SymbolSignature.TypeParameter(parameter.name(),
            parameter.bounds().stream().map(JavaBinarySymbolExtractor::type).toList())).toList();
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

    private static TypeReference type(Type value) {
        return switch (value) {
            case Type.ClassType named -> new TypeReference(named.name(), named.typeArguments().stream()
                .map(JavaBinarySymbolExtractor::type).toList(), 0, JavaSymbolIds.type(named.name()));
            case Type.PrimitiveType primitive -> new TypeReference(primitive.name());
            case Type.TypeVariable variable -> new TypeReference(variable.name());
            case Type.ArrayType array -> {
                TypeReference component = type(array.componentType());
                yield new TypeReference(component.name(), component.arguments(), component.arrayDimensions() + 1,
                    component.target());
            }
            case Type.WildcardType wildcard -> wildcard.bound() == null
                ? new TypeReference("?")
                : new TypeReference(wildcard.isUpperBound() ? "? extends" : "? super", List.of(type(wildcard.bound())),
                    0, null);
        };
    }
}
