package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;

public record MethodStub(String name, Type returnType, List<Parameter> parameters, int modifiers,
                         List<AnnotationStub> annotations, List<TypeParameter> typeParameters) implements Stub {}
