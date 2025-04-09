package io.github.railroad.ide.classparser.stub;

import io.github.railroad.ide.classparser.Type;

import java.util.List;
import java.util.stream.Stream;

public record ClassStub(
        String packageName,                 // e.g., "java.lang"
        String name,                        // e.g., "String"
        List<TypeParameter> typeParameters, // Generic type parameters
        Type superClass,                    // Superclass type
        List<Type> interfaces,              // Implemented interfaces
        List<FieldStub> fields,             // Fields in the class
        List<MethodStub> methods,           // Methods in the class
        List<ConstructorStub> constructors, // Constructors in the class
        int modifiers,                      // Modifiers (e.g., public, abstract)
        List<AnnotationStub> annotations    // Annotations on the class
) implements Stub {
    public List<Stub> getMembers() {
        return Stream.of(fields, methods, constructors)
                .flatMap(List::stream)
                .map(Stub.class::cast)
                .toList();
    }

    public String getFullName() {
        return packageName + "." + name;
    }
}
