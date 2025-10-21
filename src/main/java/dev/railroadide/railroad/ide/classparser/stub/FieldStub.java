package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;

public record FieldStub(String name, Type type, int modifiers, List<AnnotationStub> annotations) implements Stub {
}