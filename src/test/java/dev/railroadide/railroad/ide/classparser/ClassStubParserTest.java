package dev.railroadide.railroad.ide.classparser;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.project.BinaryStubPublication;
import com.google.gson.Gson;
import dev.railroadide.railroad.ide.classparser.stub.MethodStub;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

public class ClassStubParserTest {
    @Test
    public void skippingMethodBodiesKeepsEveryDeclaration() throws Exception {
        for (Class<?> type : List.of(ClassReader.class, Gson.class)) {
            Path archive = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            try (var jar = new JarFile(archive.toFile())) {
                for (JarEntry entry : Collections.list(jar.entries())) {
                    if (!entry.getName().endsWith(".class") || entry.getName().endsWith("module-info.class"))
                        continue;

                    byte[] bytes = jar.getInputStream(entry).readAllBytes();
                    var withBodies = new ClassStubVisitor();
                    new ClassReader(bytes).accept(withBodies, ClassReader.EXPAND_FRAMES);

                    assertEquals(BinaryStubPublication.copy(withBodies.createClassStub()),
                        BinaryStubPublication.copy(ClassStubParser.parse(new ClassReader(bytes))), entry.getName());
                }
            }
        }
    }

    @Test
    public void parsesStandaloneGenericMethodParameterTypes() throws Exception {
        ClassStub listStub = parseRuntimeClass("java/util/List.class");

        MethodStub addElement = listStub.methods().stream()
            .filter(method -> method.name().equals("add"))
            .filter(method -> method.parameters().size() == 1)
            .findFirst()
            .orElseThrow();

        assertEquals(1, addElement.parameters().size());
        assertInstanceOf(Type.TypeVariable.class, addElement.parameters().getFirst().type());
        assertEquals("E", ((Type.TypeVariable) addElement.parameters().getFirst().type()).name());
    }

    @Test
    public void parsesGenericInterfaceTypeArguments() throws Exception {
        ClassStub listStub = parseRuntimeClass("java/util/List.class");

        assertTrue(listStub.interfaces().stream()
            .anyMatch(type -> type instanceof Type.ClassType classType
                && classType.name().equals("java.util.SequencedCollection")
                && classType.typeArguments().size() == 1
                && classType.typeArguments().getFirst() instanceof Type.TypeVariable variable
                && variable.name().equals("E")));
    }

    @Test
    public void preservesUnboundedWildcardTypeArguments() throws Exception {
        ClassStub collectorsStub = parseRuntimeClass("java/util/stream/Collectors.class");
        MethodStub toList = collectorsStub.methods().stream()
            .filter(method -> method.name().equals("toList"))
            .filter(method -> method.parameters().isEmpty())
            .findFirst()
            .orElseThrow();

        Type.ClassType collectorType = assertInstanceOf(Type.ClassType.class, toList.returnType());
        assertEquals(3, collectorType.typeArguments().size());
        Type.WildcardType accumulationType = assertInstanceOf(
            Type.WildcardType.class,
            collectorType.typeArguments().get(1));
        assertEquals(null, accumulationType.bound());
    }

    @Test
    public void preservesGenericThrownTypeVariables() throws Exception {
        ClassStub optionalStub = parseRuntimeClass("java/util/Optional.class");
        MethodStub orElseThrow = optionalStub.methods().stream()
            .filter(method -> method.name().equals("orElseThrow"))
            .filter(method -> method.parameters().size() == 1)
            .findFirst()
            .orElseThrow();

        assertEquals(1, orElseThrow.thrownTypes().size());
        Type.TypeVariable thrown = assertInstanceOf(
            Type.TypeVariable.class, orElseThrow.thrownTypes().getFirst());
        assertEquals("X", thrown.name());
    }

    private static ClassStub parseRuntimeClass(String classFile) throws Exception {
        Path javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
        Path jmod = javaHome.resolve("jmods").resolve("java.base.jmod");
        if (Files.isRegularFile(jmod)) {
            try (var jar = new JarFile(jmod.toFile())) {
                ZipEntry entry = jar.getEntry("classes/" + classFile);
                return ClassStubParser.parse(new ClassReader(jar.getInputStream(entry)));
            }
        }

        FileSystem fileSystem;
        try {
            fileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
        } catch (Exception _) {
            fileSystem = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap());
        }

        try (var input = Files.newInputStream(fileSystem.getPath("/modules/java.base/" + classFile))) {
            return ClassStubParser.parse(new ClassReader(input));
        }
    }
}
