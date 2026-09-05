package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;

/**
 * Stores a method signature and its modifiers, exceptions, annotations, and generic parameters.
 *
 * @param name the declared name
 * @param returnType the method return type
 * @param parameters the callable parameters in declaration order
 * @param modifiers the JVM access and modifier bits
 * @param thrownTypes the declared exception types
 * @param annotations the declaration annotations
 * @param typeParameters the declared generic type parameters
 */
public record MethodStub(
    String name,
    Type returnType,
    List<Parameter> parameters,
    int modifiers,
    List<Type> thrownTypes,
    List<AnnotationStub> annotations,
    List<TypeParameter> typeParameters
) implements Stub {
}
