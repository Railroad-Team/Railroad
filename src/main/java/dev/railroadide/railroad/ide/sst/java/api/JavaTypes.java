package dev.railroadide.railroad.ide.sst.java.api;

import dev.railroadide.railroad.ide.sst.semantic.api.Type;

import java.util.List;
import java.util.Objects;
import java.util.Locale;

/**
 * Java semantic type values used by Java resolution and inspections.
 * <p>
 * Types are lightweight immutable value objects. They are designed for querying rather
 * than modelling every compiler-internal detail, which makes them suitable for inspection
 * logic and diagnostics.
 */
public final class JavaTypes {
    private JavaTypes() {
    }

    /**
     * Broad categories supported by the public semantic type model.
     */
    public enum Kind implements Type.Kind {
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
        WILDCARD;

        @Override
        public String id() {
            return "java:" + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Type used when semantic analysis cannot determine a more precise type.
     *
     * @param displayName the nonblank human-readable type name
     */
    public record UnknownType(String displayName) implements Type {
        /**
         * Creates a semantic type with a nonblank display name.
         *
         * @param displayName the nonblank human-readable type name
         */
        public UnknownType {
            displayName = normalizeDisplayName(displayName, "<unknown>");
        }

        @Override
        public JavaTypes.Kind kind() {
            return JavaTypes.Kind.UNKNOWN;
        }
    }

    /**
     * The special {@code void} type.
     */
    public record VoidType() implements Type {
        @Override
        public JavaTypes.Kind kind() {
            return JavaTypes.Kind.VOID;
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
    public record PrimitiveType(String displayName) implements Type {
        /**
         * Creates a semantic type with a nonblank display name.
         *
         * @param displayName the nonblank human-readable type name
         */
        public PrimitiveType {
            displayName = normalizeDisplayName(displayName, "primitive");
        }

        @Override
        public JavaTypes.Kind kind() {
            return JavaTypes.Kind.PRIMITIVE;
        }
    }

    /**
     * A declared reference type such as {@code String} or {@code List<String>}.
     *
     * @param displayName the nonblank human-readable type name
     * @param typeArguments the ordered generic type arguments
     */
    public record DeclaredType(String displayName, List<Type> typeArguments) implements Type {
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
        public JavaTypes.Kind kind() {
            return JavaTypes.Kind.DECLARED;
        }
    }

    /**
     * An array type.
     *
     * @param componentType the nonnull array component type
     */
    public record ArrayType(Type componentType) implements Type {
        /**
         * Creates an array type with a nonnull component type.
         *
         * @param componentType the nonnull array component type
         */
        public ArrayType {
            componentType = Objects.requireNonNull(componentType, "componentType");
        }

        @Override
        public JavaTypes.Kind kind() {
            return JavaTypes.Kind.ARRAY;
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
    public record TypeVariableType(String displayName) implements Type {
        /**
         * Creates a semantic type with a nonblank display name.
         *
         * @param displayName the nonblank human-readable type name
         */
        public TypeVariableType {
            displayName = normalizeDisplayName(displayName, "type variable");
        }

        @Override
        public JavaTypes.Kind kind() {
            return JavaTypes.Kind.TYPE_VARIABLE;
        }
    }

    /**
     * A wildcard type such as {@code ? extends Number} or {@code ? super String}.
     *
     * @param upperBound the upper bound, or {@code null} when only a lower bound is supplied
     * @param lowerBound the lower bound, or {@code null} when only an upper bound is supplied
     */
    public record WildcardType(Type upperBound, Type lowerBound) implements Type {
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
        public JavaTypes.Kind kind() {
            return JavaTypes.Kind.WILDCARD;
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
