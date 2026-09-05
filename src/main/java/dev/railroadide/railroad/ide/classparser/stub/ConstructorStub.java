package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;

/**
 * Stores constructor parameters, modifiers, exceptions, annotations, and generic parameters.
 *
 * @param parameters the callable parameters in declaration order
 * @param modifiers the JVM access and modifier bits
 * @param thrownTypes the declared exception types
 * @param annotations the declaration annotations
 * @param typeParameters the declared generic type parameters
 */
public record ConstructorStub(
    List<Parameter> parameters,
    int modifiers,
    List<Type> thrownTypes,
    List<AnnotationStub> annotations,
    List<TypeParameter> typeParameters
) implements Stub {
    @Override
    public String name() {
        return "<init>";
    }
}
