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
    permits Type.UnknownType, Type.VoidType, Type.PrimitiveType, Type.DeclaredType, Type.ArrayType,
    Type.TypeVariableType, Type.WildcardType {

    /**
     * Returns the coarse-grained type category.
     *
     * @return the semantic type category
     */
    Kind kind();

    /**
     * Returns a human-readable type name suitable for diagnostics and logging.
     *
     * @return the human-readable type name
     */
    String displayName();

    /**
     * Broad categories supported by the public semantic type model.
     */
    enum Kind {
        /**
         * A type not determined by semantic analysis.
         */
        UNKNOWN,
        /**
         * The absence of a return value.
         */
        VOID,
        /**
         * A primitive Java value type.
         */
        PRIMITIVE,
        /**
         * A declared reference type, optionally with generic arguments.
         */
        DECLARED,
        /**
         * An array with a component type.
         */
        ARRAY,
        /**
         * A generic type variable.
         */
        TYPE_VARIABLE,
        /**
         * A wildcard constrained by an upper or lower bound.
         */
        WILDCARD
    }

    /**
     * Type used when semantic analysis cannot determine a more precise type.
     *
     * @param displayName the nonblank human-readable type name
     */
    record UnknownType(String displayName) implements Type {
        /**
         * Creates a semantic type with a nonblank display name.
         *
         * @param displayName the nonblank human-readable type name
         */
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
     *
     * @param displayName the nonblank human-readable type name
     */
    record PrimitiveType(String displayName) implements Type {
        /**
         * Creates a semantic type with a nonblank display name.
         *
         * @param displayName the nonblank human-readable type name
         */
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
     *
     * @param displayName the nonblank human-readable type name
     * @param typeArguments the ordered generic type arguments
     */
    record DeclaredType(String displayName, List<Type> typeArguments) implements Type {
        /**
         * Creates a declared type with a nonblank display name and an immutable copy of its type arguments.
         *
         * @param displayName the nonblank human-readable type name
         * @param typeArguments the ordered generic type arguments
         */
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
     *
     * @param componentType the nonnull array component type
     */
    record ArrayType(Type componentType) implements Type {
        /**
         * Creates an array type with a nonnull component type.
         *
         * @param componentType the nonnull array component type
         */
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
     *
     * @param displayName the nonblank human-readable type name
     */
    record TypeVariableType(String displayName) implements Type {
        /**
         * Creates a semantic type with a nonblank display name.
         *
         * @param displayName the nonblank human-readable type name
         */
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
     *
     * @param upperBound the upper bound, or {@code null} when only a lower bound is supplied
     * @param lowerBound the lower bound, or {@code null} when only an upper bound is supplied
     */
    record WildcardType(Type upperBound, Type lowerBound) implements Type {
        /**
         * Creates a wildcard type, requiring at least one nonnull bound.
         *
         * @param upperBound the upper bound, or {@code null} when only a lower bound is supplied
         * @param lowerBound the lower bound, or {@code null} when only an upper bound is supplied
         */
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
