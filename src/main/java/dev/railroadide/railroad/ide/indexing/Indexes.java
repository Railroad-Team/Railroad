package dev.railroadide.railroad.ide.indexing;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.classparser.ClassStubParser;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Indexes {
    public static List<ClassStub> scanStandardLibrary() {
        List<ClassStub> stubs = new ArrayList<>();

        Path javaHome = Path.of(System.getProperty("java.home"));
        // check if its using java 9 modules
        if (Files.notExists(javaHome.resolve("lib").resolve("modules"))) {
            scanRTJar(javaHome, stubs);
        } else {
            scanJMods(javaHome, stubs);
        }

        return stubs;
    }

    private static void scanRTJar(Path javaHome, List<ClassStub> stubs) {
        // We are using java 8 or below so we need to scan the rt.jar
        Path rtJar = javaHome.resolve("lib").resolve("rt.jar");
        try (var jar = new JarFile(rtJar.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String className = entry.getName();
                if (className.startsWith("java/") && className.endsWith(".class")) {
                    className = className.substring("java/".length(), className.length() - ".class".length());
                    if (className.endsWith("module-info") || className.endsWith("package-info"))
                        continue;

                    ClassStub metadata = ClassStubParser.parse(new ClassReader(jar.getInputStream(entry)));
                    stubs.add(metadata);
                }
            }
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to scan standard library", exception);
        }
    }

    private static void scanJMods(Path javaHome, List<ClassStub> stubs) {
        Path jmods = javaHome.resolve("jmods");
        // Scan the `java.base` module
        Path javaBase = jmods.resolve("java.base.jmod"); // this should be effectively a jar file

        try (var jmod = new JarFile(javaBase.toFile())) {
            Enumeration<JarEntry> entries = jmod.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String className = entry.getName();
                if (className.startsWith("classes/java/") && className.endsWith(".class")) {
                    className = className.substring("classes/java/".length(), className.length() - ".class".length());
                    if (className.endsWith("module-info") || className.endsWith("package-info"))
                        continue;

                    ClassStub metadata = ClassStubParser.parse(new ClassReader(jmod.getInputStream(entry)));
                    stubs.add(metadata);
                }
            }
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to scan standard library", exception);
        }
    }
}
