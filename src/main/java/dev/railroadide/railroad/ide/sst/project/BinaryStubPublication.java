package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.*;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Copies the small ASM stub model at the binary index publication boundary.
 */
public final class BinaryStubPublication {
    private BinaryStubPublication() {
    }

    /**
     * Returns an alias-free immutable stub for binary cache publication.
     *
     * @param stub binary class stub to copy
     * @return copied stub with immutable collections and recursively copied type and annotation values
     * @throws IllegalArgumentException if an annotation contains an unsupported value type
     */
    public static ClassStub copy(ClassStub stub) {
        return new ClassStub(name(stub.packageName()), name(stub.name()), parameters(stub.typeParameters()),
            type(stub.superClass()), types(stub.interfaces()),
            stub.fields().stream().map(f -> new FieldStub(name(f.name()), type(f.type()), f.modifiers(),
                annotations(f.annotations()))).toList(),
            stub.methods().stream().map(m -> new MethodStub(name(m.name()), type(m.returnType()),
                arguments(m.parameters()), m.modifiers(), types(m.thrownTypes()), annotations(m.annotations()),
                parameters(m.typeParameters()))).toList(),
            stub.constructors().stream().map(c -> new ConstructorStub(arguments(c.parameters()), c.modifiers(),
                types(c.thrownTypes()), annotations(c.annotations()), parameters(c.typeParameters()))).toList(),
            stub.modifiers(), annotations(stub.annotations()), name(stub.enclosingClassName()));
    }

    /**
     * Interns names, which recur heavily across the classes of a classpath.
     */
    private static String name(String value) {
        return value == null ? null : value.intern();
    }

    private static List<Parameter> arguments(List<Parameter> values) {
        return values.stream().map(p -> new Parameter(name(p.name()), type(p.type()), annotations(p.annotations())))
            .toList();
    }

    private static List<TypeParameter> parameters(List<TypeParameter> values) {
        return values.stream().map(p -> new TypeParameter(name(p.name()), types(p.bounds()))).toList();
    }

    private static List<Type> types(List<Type> values) {
        return values.stream().map(BinaryStubPublication::type).toList();
    }

    private static Type type(Type value) {
        return switch (value) {
            case null -> null;
            case Type.ClassType c -> new Type.ClassType(name(c.name()), types(c.typeArguments()));
            case Type.ArrayType a -> new Type.ArrayType(type(a.componentType()));
            case Type.WildcardType w -> new Type.WildcardType(type(w.bound()), w.isUpperBound());
            case Type.PrimitiveType p -> Type.PrimitiveType.of(p.name());
            case Type.TypeVariable v -> new Type.TypeVariable(name(v.name()));
        };
    }

    private static List<AnnotationStub> annotations(List<AnnotationStub> values) {
        return values.stream().map(BinaryStubPublication::annotation).toList();
    }

    private static AnnotationStub annotation(AnnotationStub value) {
        Map<String, Object> values = new HashMap<>();
        value.values().forEach((key, element) -> values.put(key, annotationValue(element)));
        return new AnnotationStub(name(value.name()), Map.copyOf(values));
    }

    private static Object annotationValue(Object value) {
        if (value instanceof AnnotationStub annotation)
            return annotation(annotation);

        if (value instanceof List<?> list)
            return list.stream().map(BinaryStubPublication::annotationValue).toList();

        if (value != null && value.getClass().isArray()) {
            List<Object> elements = new ArrayList<>(Array.getLength(value));
            for (int i = 0; i < Array.getLength(value); i++) {
                elements.add(annotationValue(Array.get(value, i)));
            }

            return List.copyOf(elements);
        }

        if (value instanceof String || value instanceof Boolean || value instanceof Character
            || value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long || value instanceof Float || value instanceof Double
            || value instanceof org.objectweb.asm.Type)
            return value;

        throw new IllegalArgumentException("Unsupported binary annotation value: " + value);
    }
}
