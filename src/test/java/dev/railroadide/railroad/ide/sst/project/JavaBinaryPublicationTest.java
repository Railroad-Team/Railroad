package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;

import static org.junit.jupiter.api.Assertions.*;

public class JavaBinaryPublicationTest {
    @Test
    public void processRuntimeCacheDoesNotExposeMutableStubs() {
        var stub = JavaSemanticAnalyzer
            .loadJdkClassStubsByQualifiedName().get("java.lang.String");
        assertNotNull(stub);
        assertThrows(UnsupportedOperationException.class, () -> stub.methods().clear());
        assertThrows(UnsupportedOperationException.class, () -> stub.interfaces().clear());
    }
    @Test
    public void publicationDetachesNestedInputsAndExposesImmutableValues() {
        var arguments = new ArrayList<Type>(List.of(new Type.PrimitiveType("int")));
        var bounds = new ArrayList<Type>(List.of(new Type.ClassType("List", arguments)));
        int[] array = {1, 2};
        var values = new HashMap<String, Object>();
        values.put("numbers", array);
        var annotations = new ArrayList<>(List.of(new AnnotationStub("Example", values)));
        var fields = new ArrayList<>(List.of(new FieldStub("value", bounds.getFirst(), 0, annotations)));
        var parameters = new ArrayList<>(List.of(new Parameter("value", bounds.getFirst(), annotations)));
        var typeParameters = new ArrayList<>(List.of(new TypeParameter("T", bounds)));
        var methods = new ArrayList<>(List.of(new MethodStub("method", bounds.getFirst(), parameters, 0,
            bounds, annotations, typeParameters)));
        var constructors = new ArrayList<>(
            List.of(new ConstructorStub(parameters, 0, bounds, annotations, typeParameters)));
        var stub = new ClassStub("example", "Example", List.of(new TypeParameter("T", bounds)),
            null, bounds, fields, methods, constructors, 0, annotations);
        var index = new JavaStubSymbolIndex(Map.of("example.Example", stub),
            Map.of("example.Example", Path.of("example.jar")));
        ClassStub published = index.classStubsByQualifiedName().get("example.Example");
        arguments.clear();
        bounds.clear();
        fields.clear();
        annotations.clear();
        array[0] = 99;
        values.clear();
        parameters.clear();
        typeParameters.clear();
        methods.clear();
        constructors.clear();
        assertEquals(1, published.fields().size());
        assertEquals(1, ((Type.ClassType) published.fields().getFirst().type()).typeArguments().size());
        assertEquals(List.of(1, 2), published.annotations().getFirst().values().get("numbers"));
        assertThrows(UnsupportedOperationException.class, () -> published.fields().clear());
        assertThrows(UnsupportedOperationException.class, () -> published.typeParameters().getFirst().bounds().clear());
        assertThrows(UnsupportedOperationException.class, () -> published.annotations().getFirst().values().clear());
        assertEquals(1, published.methods().getFirst().parameters().size());
        assertEquals(1, published.constructors().getFirst().typeParameters().size());
        assertThrows(UnsupportedOperationException.class, () -> published.methods().getFirst().parameters().clear());
        assertThrows(UnsupportedOperationException.class,
            () -> published.constructors().getFirst().thrownTypes().clear());
    }
}
