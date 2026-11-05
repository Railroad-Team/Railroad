package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;
import java.util.stream.Stream;

/**
 * Stores class declaration metadata and its parsed fields, methods, and constructors.
 *
 * @param packageName the class package name
 * @param name the declared name
 * @param typeParameters the declared generic type parameters
 * @param superClass the superclass type
 * @param interfaces the implemented interface types
 * @param fields the declared fields
 * @param methods the declared methods
 * @param constructors the declared constructors
 * @param modifiers the JVM access and modifier bits
 * @param annotations the declaration annotations
 * @param enclosingClassName binary name of the enclosing class, or null for a top-level declaration
 */
public record ClassStub(
    String packageName, // e.g., "java.lang"
    String name, // e.g., "String"
    List<TypeParameter> typeParameters, // Generic type parameters
    Type superClass, // Superclass type
    List<Type> interfaces, // Implemented interfaces
    List<FieldStub> fields, // Fields in the class
    List<MethodStub> methods, // Methods in the class
    List<ConstructorStub> constructors, // Constructors in the class
    int modifiers, // Modifiers (e.g., public, abstract)
    List<AnnotationStub> annotations // Annotations on the class
    String enclosingClassName
) implements Stub {
    /**
     * Creates a stub without enclosing-class metadata, preserving the existing constructor contract.
     *
     * @param packageName the class package name
     * @param name the declared name
     * @param typeParameters the declared generic type parameters
     * @param superClass the superclass type
     * @param interfaces the implemented interface types
     * @param fields the declared fields
     * @param methods the declared methods
     * @param constructors the declared constructors
     * @param modifiers the JVM access and modifier bits
     * @param annotations the declaration annotations
     */
    public ClassStub(
        String packageName,
        String name,
        List<TypeParameter> typeParameters,
        Type superClass,
        List<Type> interfaces,
        List<FieldStub> fields,
        List<MethodStub> methods,
        List<ConstructorStub> constructors,
        int modifiers,
        List<AnnotationStub> annotations
    ) {
        this(packageName, name, typeParameters, superClass, interfaces, fields, methods, constructors,
            modifiers, annotations, null);
    }

    /**
     * Combines the class's fields, methods, and constructors in that order.
     *
     * @return the combined list of member stubs
     */
    public List<Stub> getMembers() {
        return Stream.of(fields, methods, constructors)
            .flatMap(List::stream)
            .map(Stub.class::cast)
            .toList();
    }

    /**
     * Joins the package and class names with a dot.
     *
     * @return the package name followed by a dot and the class name
     */
    public String getFullName() {
        return packageName + "." + name;
    }
}
