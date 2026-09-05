package dev.railroadide.railroad.ide.classparser.stub;

import java.util.Map;

/**
 * Stores the name and element values of a parsed annotation.
 *
 * @param name the annotation type name
 * @param values the annotation element values keyed by element name
 */
public record AnnotationStub(String name, Map<String, Object> values) implements Stub {
}
