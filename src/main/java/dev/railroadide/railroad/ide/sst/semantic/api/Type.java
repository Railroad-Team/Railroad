package dev.railroadide.railroad.ide.sst.semantic.api;

import java.util.List;
import java.util.Objects;

/**
 * Public semantic type contract used by type resolution and inspections.
 * <p>
 * Types are lightweight immutable value objects. They are designed for querying rather
 * than modelling every compiler-internal detail, which makes them suitable for inspection
 * logic and diagnostics.
 */
public sealed interface Type
        permits Type.UnknownType, Type.VoidType, Type.PrimitiveType, Type.DeclaredType, Type.ArrayType, Type.TypeVariableType, Type.WildcardType {

    /**
     * Returns the coarse-grained type category.
     */
    Kind kind();

    /**
     * Returns a human-readable type name suitable for diagnostics and logging.
     */
    String displayName();

    /**
     * Broad categories supported by the public semantic type model.
     */
    enum Kind {
        UNKNOWN,
        VOID,
        PRIMITIVE,
        DECLARED,
        ARRAY,
        TYPE_VARIABLE,
        WILDCARD
    }

    /**
     * Type used when semantic analysis cannot determine a more precise type.
     */
    record UnknownType(String displayName) implements Type {
        public UnknownType {
            displayName = normalizeDisplayName(displayName, "<unknown>");
        }

        @Override
        public Kind kind() {
            return Kind.UNKNOWN;
        }
    }

    /**
     * The special {@code void} type.
     */
    record VoidType() implements Type {
        @Override
        public Kind kind() {
            return Kind.VOID;
        }

        @Override
        public String displayName() {
            return "void";
        }
    }

    /**
     * A Java primitive type such as {@code int} or {@code boolean}.
     */
    record PrimitiveType(String displayName) implements Type {
        public PrimitiveType {
            displayName = normalizeDisplayName(displayName, "primitive");
        }

        @Override
        public Kind kind() {
            return Kind.PRIMITIVE;
        }
    }

    /**
     * A declared reference type such as {@code String} or {@code List<String>}.
     */
    record DeclaredType(String displayName, List<Type> typeArguments) implements Type {
        public DeclaredType {
            displayName = normalizeDisplayName(displayName, "declared");
            typeArguments = List.copyOf(Objects.requireNonNull(typeArguments, "typeArguments"));
        }

        @Override
        public Kind kind() {
            return Kind.DECLARED;
        }
    }

    /**
     * An array type.
     */
    record ArrayType(Type componentType) implements Type {
        public ArrayType {
            componentType = Objects.requireNonNull(componentType, "componentType");
        }

        @Override
        public Kind kind() {
            return Kind.ARRAY;
        }

        @Override
        public String displayName() {
            return componentType.displayName() + "[]";
        }
    }

    /**
     * A type variable such as {@code T}.
     */
    record TypeVariableType(String displayName) implements Type {
        public TypeVariableType {
            displayName = normalizeDisplayName(displayName, "type variable");
        }

        @Override
        public Kind kind() {
            return Kind.TYPE_VARIABLE;
        }
    }

    /**
     * A wildcard type such as {@code ? extends Number} or {@code ? super String}.
     */
    record WildcardType(Type upperBound, Type lowerBound) implements Type {
        public WildcardType {
            if (upperBound == null && lowerBound == null)
                throw new IllegalArgumentException("wildcard bound cannot be fully unbounded");
        }

        @Override
        public Kind kind() {
            return Kind.WILDCARD;
        }

        @Override
        public String displayName() {
            if (upperBound != null)
                return "? extends " + upperBound.displayName();
            return "? super " + lowerBound.displayName();
        }
    }

    private static String normalizeDisplayName(String value, String name) {
        value = Objects.requireNonNull(value, "displayName");
        if (value.isBlank())
            throw new IllegalArgumentException(name + " type displayName cannot be blank");
        return value;
    }
}
