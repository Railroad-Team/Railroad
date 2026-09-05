package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.classparser.ClassScanException;
import dev.railroadide.railroad.ide.classparser.ClassStubParser;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JavaLibrarySymbolIndex extends JavaStubSymbolIndex {
    private JavaLibrarySymbolIndex(
        Map<String, ClassStub> classStubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName
    ) {
        super(classStubsByQualifiedName, sourceByQualifiedName);
    }

    public static JavaLibrarySymbolIndex build(List<Path> roots) {
        Map<String, ClassStub> stubsByQualifiedName = new LinkedHashMap<>();
        Map<String, Path> sourceByQualifiedName = new LinkedHashMap<>();
        Set<Path> uniqueRoots = new LinkedHashSet<>(roots);
        for (Path root : uniqueRoots) {
            scanRoot(root, stubsByQualifiedName, sourceByQualifiedName);
        }

        return new JavaLibrarySymbolIndex(stubsByQualifiedName, sourceByQualifiedName);
    }

    private static void scanRoot(
        Path root,
        Map<String, ClassStub> stubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName
    ) {
        if (root == null || Files.notExists(root) || !Files.isReadable(root))
            return;

        if (Files.isDirectory(root)) {
            scanDirectory(root, stubsByQualifiedName, sourceByQualifiedName);
            return;
        }

        String fileName = root.getFileName() == null ? "" : root.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jar") || fileName.endsWith(".jmod")) {
            scanArchive(root, stubsByQualifiedName, sourceByQualifiedName);
        }
    }

    private static void scanDirectory(
        Path root,
        Map<String, ClassStub> stubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName
    ) {
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".class"))
                .filter(path -> !isDescriptorClass(path.getFileName().toString()))
                .forEach(path -> parseClassFile(path, root, stubsByQualifiedName, sourceByQualifiedName));
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error scanning library directory {}", root, exception);
        }
    }

    private static void parseClassFile(
        Path classFile,
        Path origin,
        Map<String, ClassStub> stubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName
    ) {
        try {
            indexStub(ClassStubParser.parse(classFile), origin, stubsByQualifiedName, sourceByQualifiedName);
        } catch (ClassScanException exception) {
            Railroad.LOGGER.warn("Ignoring unreadable class file {}", classFile, exception);
        }
    }

    private static void scanArchive(
        Path archive,
        Map<String, ClassStub> stubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName
    ) {
        try (JarFile jarFile = new JarFile(archive.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory())
                    continue;

                String entryName = entry.getName();
                if (!entryName.endsWith(".class") || isDescriptorClass(entryName))
                    continue;

                try (InputStream input = jarFile.getInputStream(entry)) {
                    indexStub(ClassStubParser.parse(new ClassReader(input)), archive, stubsByQualifiedName,
                        sourceByQualifiedName);
                } catch (Exception exception) {
                    Railroad.LOGGER.warn("Ignoring unreadable class entry {} in {}", entryName, archive, exception);
                }
            }
        } catch (IOException exception) {
            Railroad.LOGGER.error("Error scanning library archive {}", archive, exception);
        }
    }

    private static void indexStub(
        ClassStub stub,
        Path origin,
        Map<String, ClassStub> stubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName
    ) {
        String qualifiedName = stub.getFullName();
        if (qualifiedName == null || qualifiedName.isBlank())
            return;

        stubsByQualifiedName.putIfAbsent(qualifiedName, stub);
        sourceByQualifiedName.putIfAbsent(qualifiedName, origin);
    }

    private static boolean isDescriptorClass(String name) {
        return name.endsWith("module-info.class") || name.endsWith("package-info.class");
    }
}
