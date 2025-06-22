package io.github.railroad.ide.classparser.stub;

import io.github.railroad.ide.classparser.Type;

import java.util.List;

public record MethodStub(String name, Type returnType, List<Parameter> parameters, int modifiers,
                         List<AnnotationStub> annotations, List<TypeParameter> typeParameters) implements Stub {}
