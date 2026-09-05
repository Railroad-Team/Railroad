package dev.railroadide.railroad.ide.classparser;

import java.util.List;

/**
 * Represents a type appearing in parsed class metadata or generic signatures.
 */
public sealed interface Type
    permits Type.ClassType, Type.PrimitiveType, Type.ArrayType, Type.TypeVariable, Type.WildcardType {
    /**
     * Converts an ASM primitive, object, or array type into the stub type model.
     *
     * @param asmType the ASM type to convert
     * @return the corresponding stub type
     * @throws IllegalArgumentException if the ASM type sort is unsupported
     */
    static Type fromAsmType(org.objectweb.asm.Type asmType) {
        return switch (asmType.getSort()) {
            case org.objectweb.asm.Type.VOID -> new PrimitiveType("void");
            case org.objectweb.asm.Type.BOOLEAN -> new PrimitiveType("boolean");
            case org.objectweb.asm.Type.CHAR -> new PrimitiveType("char");
            case org.objectweb.asm.Type.BYTE -> new PrimitiveType("byte");
            case org.objectweb.asm.Type.SHORT -> new PrimitiveType("short");
            case org.objectweb.asm.Type.INT -> new PrimitiveType("int");
            case org.objectweb.asm.Type.FLOAT -> new PrimitiveType("float");
            case org.objectweb.asm.Type.LONG -> new PrimitiveType("long");
            case org.objectweb.asm.Type.DOUBLE -> new PrimitiveType("double");
            case org.objectweb.asm.Type.ARRAY -> {
                Type componentType = fromAsmType(asmType.getElementType());
                yield new ArrayType(componentType);
            }
            case org.objectweb.asm.Type.OBJECT -> {
                String className = asmType.getClassName();
                yield new ClassType(className, List.of());
            }
            default -> throw new IllegalArgumentException("Unsupported type: " + asmType);
        };
    }

    // Class or interface type, with optional type arguments for generics
    /**
     * Describes a class or interface reference with optional generic arguments.
     *
     * @param name the qualified class or interface name
     * @param typeArguments the generic arguments in declaration order
     */
    record ClassType(String name, List<Type> typeArguments) implements Type {
    }

    // Primitive type (e.g., int, boolean)
    /**
     * Describes a primitive type or the void return type.
     *
     * @param name the primitive keyword or void
     */
    record PrimitiveType(String name) implements Type {
    }

    // Array type
    /**
     * Describes an array by its component type.
     *
     * @param componentType the array component type
     */
    record ArrayType(Type componentType) implements Type {
    }

    // Type variable for generics (e.g., T in List<T>)
    /**
     * References a named generic type parameter.
     *
     * @param name the declared name
     */
    record TypeVariable(String name) implements Type {
    }

    // Wildcard type for generics (e.g., ? extends Number)
    /**
     * Describes a generic wildcard with an upper or lower bound.
     *
     * @param bound the wildcard bound
     * @param isUpperBound whether the bound is an extends bound rather than a super bound
     */
    record WildcardType(Type bound, boolean isUpperBound) implements Type {
    }
}
