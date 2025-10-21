package dev.railroadide.railroad.ide.classparser.stub;

import java.util.List;

public record ConstructorStub(List<Parameter> parameters, int modifiers,
                              List<AnnotationStub> annotations, List<TypeParameter> typeParameters) implements Stub {
    @Override
    public String name() {
        return "<init>";
    }
}