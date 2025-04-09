package io.github.railroad.ide.classparser.stub;

import java.util.Map;

public record AnnotationStub(String name, Map<String, Object> values) implements Stub {}