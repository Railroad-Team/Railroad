package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;

/**
 * Stores a field declaration's name, type, modifiers, and annotations.
 *
 * @param name the declared name
 * @param type the declared type
 * @param modifiers the JVM access and modifier bits
 * @param annotations the declaration annotations
 */
public record FieldStub(String name, Type type, int modifiers, List<AnnotationStub> annotations) implements Stub {
}
