package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;

public record ConstructorStub(List<Parameter> parameters, int modifiers,
                              List<Type> thrownTypes, List<AnnotationStub> annotations,
                              List<TypeParameter> typeParameters) implements Stub {
    @Override
    public String name() {
        return "<init>";
    }
}
