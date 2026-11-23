package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.classparser.stub.FieldStub;
import dev.railroadide.railroad.ide.classparser.stub.MethodStub;
import dev.railroadide.railroad.ide.classparser.stub.Parameter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class JavaStubSymbolIndexTest {
    private static final Type VOID = new Type.PrimitiveType("void");
    private static final Type INT = new Type.PrimitiveType("int");

    @Test
    public void sharedSimpleNameReturnsEveryOwnerOnceInInputOrder() {
        ClassStub first = stub("First",
            List.of(new FieldStub("run", INT, Opcodes.ACC_PUBLIC, List.of())),
            List.of(method("run"), method("run", INT), method("other")));
        ClassStub second = stub("Second", List.of(), List.of(method("run")));

        assertEquals(List.of("sample.First", "sample.First", "sample.First", "sample.Second"),
            owners(index(first, second).lookupSimpleName("run")));
        assertEquals(List.of("sample.Second", "sample.First", "sample.First", "sample.First"),
            owners(index(second, first).lookupSimpleName("run")));
    }

    @Test
    public void singleOwnerNamesAndTypesAreFound() {
        var index = index(stub("Only", List.of(), List.of(method("unique"))));

        assertEquals(List.of("sample.Only"), owners(index.lookupSimpleName("unique")));
        assertEquals(1, index.lookupSimpleName("Only").size());
        assertEquals(List.of(), index.lookupSimpleName("missing"));
    }

    @Test
    public void typeNameLookupSkipsClassesWhoseMembersHaveTheSameName() {
        var index = index(stub("Box", List.of(), List.of()), stub("Other", List.of(), List.of(method("Box"))));
        assertEquals(List.of("sample.Box"), index.qualifiedTypeNames("Box"));
        assertEquals(2, index.lookupSimpleName("Box").size());
    }

    @Test
    public void bridgeMethodsAreNotIndexedBySimpleName() {
        var bridge = new MethodStub("bridged", VOID, List.of(), Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE, List.of(),
            List.of(), List.of());

        assertEquals(List.of(), index(stub("Bridges", List.of(), List.of(bridge))).lookupSimpleName("bridged"));
    }

    @Test
    public void lookupTablesCoverEveryClass() {
        var index = index(stub("First", List.of(), List.of()), stub("Second", List.of(), List.of()));

        assertEquals(Set.of("sample.First", "sample.Second"), index.declaredQualifiedNames());
        assertTrue(index.typeNames().containsAll(Set.of("sample.First", "First", "sample.Second", "Second")));
        assertTrue(index.containsPackage("sample"));
        assertThrows(UnsupportedOperationException.class, () -> index.declaredQualifiedNames().clear());
    }

    @Test
    public void classWithoutSourceIsRejected() {
        ClassStub stub = stub("Orphan", List.of(), List.of());

        assertThrows(NullPointerException.class,
            () -> new JavaStubSymbolIndex(Map.of("sample.Orphan", stub), Map.of()));
    }

    private static JavaStubSymbolIndex index(ClassStub... stubs) {
        Map<String, ClassStub> byName = new LinkedHashMap<>();
        Map<String, Path> sources = new LinkedHashMap<>();
        for (ClassStub stub : stubs) {
            byName.put(stub.getFullName(), stub);
            sources.put(stub.getFullName(), Path.of("library.jar"));
        }

        return new JavaStubSymbolIndex(byName, sources);
    }

    private static List<String> owners(List<JavaProjectSemanticIndex.SymbolDescriptor> symbols) {
        return symbols.stream().map(JavaProjectSemanticIndex.SymbolDescriptor::ownerQualifiedName).toList();
    }

    private static ClassStub stub(String name, List<FieldStub> fields, List<MethodStub> methods) {
        return new ClassStub("sample", name, List.of(), new Type.ClassType("java.lang.Object", List.of()), List.of(),
            fields, methods, List.of(), Opcodes.ACC_PUBLIC, List.of());
    }

    private static MethodStub method(String name, Type... parameters) {
        List<Parameter> parameterList = Arrays.stream(parameters)
            .map(type -> new Parameter("value", type, List.of()))
            .toList();
        return new MethodStub(name, VOID, parameterList, Opcodes.ACC_PUBLIC, List.of(), List.of(), List.of());
    }
}
