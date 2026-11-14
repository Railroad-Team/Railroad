package dev.railroadide.railroad.ide.sst.semantic.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Immutable declaration signature. Types use the type-parameter list with no value parameters or
 * return type; callables additionally describe their parameters, result and thrown types.
 * Parameter types preserve source detail; providers compute overload
 * identity using their language's rules, independently of this display and query structure.
 *
 * @param typeParameters declared type parameters and bounds
 * @param parameters ordered value parameters
 * @param returnType declared result type, or null for constructors/unknown results
 * @param thrownTypes declared exception/effect types
 */
public record SymbolSignature(
    List<TypeParameter> typeParameters,
    List<Parameter> parameters,
    @Nullable TypeReference returnType,
    List<TypeReference> thrownTypes
) {
    public SymbolSignature {
        typeParameters = List.copyOf(typeParameters);
        parameters = List.copyOf(parameters);
        thrownTypes = List.copyOf(thrownTypes);
        for (int index = 0; index < parameters.size() - 1; index++) {
            if (parameters.get(index).varargs())
                throw new IllegalArgumentException("Only the final parameter may be variadic");
        }
    }

    /** A declared generic parameter with ordered bounds. */
    public record TypeParameter(String name, List<TypeReference> bounds) {
        public TypeParameter {
            Objects.requireNonNull(name, "name");
            if (name.isBlank())
                throw new IllegalArgumentException("Type parameter name cannot be blank");
            bounds = List.copyOf(bounds);
        }
    }

    /** A callable parameter; a variadic parameter stores its element type. */
    public record Parameter(String name, TypeReference type, boolean varargs) {
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }
}
