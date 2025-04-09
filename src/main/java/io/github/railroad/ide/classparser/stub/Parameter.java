package io.github.railroad.ide.classparser.stub;

import io.github.railroad.ide.classparser.Type;

import java.util.List;

public record Parameter(String name, Type type, List<AnnotationStub> annotations) {}
