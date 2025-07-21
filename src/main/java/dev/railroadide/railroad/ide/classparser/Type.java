package dev.railroadide.railroad.ide.classparser;

import java.util.List;

public sealed interface Type
        permits Type.ClassType, Type.PrimitiveType, Type.ArrayType, Type.TypeVariable, Type.WildcardType {
    // Class or interface type, with optional type arguments for generics
    record ClassType(String name, List<Type> typeArguments) implements Type {}

    // Primitive type (e.g., int, boolean)
    record PrimitiveType(String name) implements Type {}

    // Array type
    record ArrayType(Type componentType) implements Type {}

    // Type variable for generics (e.g., T in List<T>)
    record TypeVariable(String name) implements Type {}

    // Wildcard type for generics (e.g., ? extends Number)
    record WildcardType(Type bound, boolean isUpperBound) implements Type {}

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
}