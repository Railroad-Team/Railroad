package dev.railroadide.railroad.ide.indexing;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.classparser.ClassStubParser;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.java.JDKManager;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
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

        Path javaHome = resolveJavaHome();
        if (javaHome == null) {
            Railroad.LOGGER.error("Failed to locate a JDK home for standard library scanning");
            return stubs;
        }

        // check if its using java 9 modules
        if (Files.notExists(javaHome.resolve("lib").resolve("modules"))) {
            scanRTJar(javaHome, stubs);
        } else {
            scanModuleImage(stubs);
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

    private static void scanModuleImage(List<ClassStub> stubs) {
        try {
            FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
            Path javaBase = jrt.getPath("/modules/java.base/java");
            try (var entries = Files.walk(javaBase)) {
                entries.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> !path.endsWith("module-info.class") && !path.endsWith("package-info.class"))
                    .forEach(path -> {
                        try (var input = Files.newInputStream(path)) {
                            stubs.add(ClassStubParser.parse(new ClassReader(input)));
                        } catch (IOException exception) {
                            Railroad.LOGGER.error("Failed to scan standard library class {}", path, exception);
                        }
                    });
            }
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to scan standard library", exception);
        }
    }

    private static Path resolveJavaHome() {
        Path configured = normalizeHome(System.getProperty("java.home"));
        if (configured != null && hasScannableStandardLibrary(configured))
            return configured;

        Path javaHomeEnv = normalizeHome(System.getenv("JAVA_HOME"));
        if (javaHomeEnv != null && hasScannableStandardLibrary(javaHomeEnv))
            return javaHomeEnv;

        Path jdkHomeEnv = normalizeHome(System.getenv("JDK_HOME"));
        if (jdkHomeEnv != null && hasScannableStandardLibrary(jdkHomeEnv))
            return jdkHomeEnv;

        JDKManager.refreshJDKs();
        for (JDK jdk : JDKManager.getAvailableJDKs()) {
            if (hasScannableStandardLibrary(jdk.path()))
                return jdk.path();
        }

        return configured;
    }

    private static Path normalizeHome(String home) {
        if (home == null || home.isBlank())
            return null;

        try {
            return Path.of(home).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean hasScannableStandardLibrary(Path javaHome) {
        if (javaHome == null || Files.notExists(javaHome))
            return false;

        if (Files.isRegularFile(javaHome.resolve("lib").resolve("rt.jar")))
            return true;

        return Files.isRegularFile(javaHome.resolve("lib").resolve("modules"))
            || Files.isRegularFile(javaHome.resolve("jmods").resolve("java.base.jmod"));
    }
}
