package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;

public record Parameter(String name, Type type, List<AnnotationStub> annotations) {
}
