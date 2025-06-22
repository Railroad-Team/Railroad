package io.github.railroad.ide.classparser.stub;

import io.github.railroad.ide.classparser.Type;

import java.util.List;

public record FieldStub(String name, Type type, int modifiers, List<AnnotationStub> annotations) implements Stub {}