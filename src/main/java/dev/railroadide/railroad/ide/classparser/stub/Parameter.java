package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;

/**
 * Stores the name, type, and annotations of a callable parameter.
 *
 * @param name the declared name
 * @param type the declared type
 * @param annotations the declaration annotations
 */
public record Parameter(String name, Type type, List<AnnotationStub> annotations) {
}
