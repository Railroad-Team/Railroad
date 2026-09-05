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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans the Java runtime library into class-file stubs.
 */
public class Indexes {
    /**
     * Scans runtime classes from rt.jar, JMOD archives, or the runtime image.
     *
     * @return collected class stubs; logged scan failures may leave a partial or empty result
     */
    public static List<ClassStub> scanStandardLibrary() {
        List<ClassStub> stubs = new ArrayList<>();

        Path javaHome = resolveJavaHome();
        if (javaHome == null) {
            Railroad.LOGGER.error("Failed to locate a JDK home for standard library scanning");
            return stubs;
        }

        // check if its using java 9 modules
        if (Files.isRegularFile(javaHome.resolve("lib").resolve("rt.jar"))) {
            scanRTJar(javaHome, stubs);
        } else if (Files.isRegularFile(javaHome.resolve("jmods").resolve("java.base.jmod"))) {
            scanJMods(javaHome, stubs);
        } else {
            scanJrtRuntime(stubs);
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
        try (var paths = Files.list(jmods)) {
            for (Path jmodPath : paths
                .filter(path -> path.getFileName().toString().endsWith(".jmod"))
                .sorted()
                .toList()) {
                scanJMod(jmodPath, stubs);
            }
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to scan standard library modules", exception);
        }
    }

    private static void scanJMod(Path jmodPath, List<ClassStub> stubs) {
        try (var jmod = new JarFile(jmodPath.toFile())) {
            Enumeration<JarEntry> entries = jmod.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String className = entry.getName();
                if (className.startsWith("classes/") && className.endsWith(".class")) {
                    className = className.substring("classes/".length(), className.length() - ".class".length());
                    if (className.endsWith("module-info") || className.endsWith("package-info"))
                        continue;

                    ClassStub metadata = ClassStubParser.parse(new ClassReader(jmod.getInputStream(entry)));
                    stubs.add(metadata);
                }
            }
        } catch (IOException exception) {
            Railroad.LOGGER.warn("Ignoring unreadable standard library module {}", jmodPath, exception);
        }
    }

    private static void scanJrtRuntime(List<ClassStub> stubs) {
        try {
            FileSystem fileSystem;
            try {
                fileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
            } catch (Exception _) {
                fileSystem = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap());
            }

            Path modules = fileSystem.getPath("/modules");
            try (var paths = Files.walk(modules)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return !name.equals("module-info.class") && !name.equals("package-info.class");
                    })
                    .forEach(path -> parseRuntimeClass(path, stubs));
            }
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to scan current runtime standard library", exception);
        }
    }

    private static void parseRuntimeClass(Path classFile, List<ClassStub> stubs) {
        try (var input = Files.newInputStream(classFile)) {
            stubs.add(ClassStubParser.parse(new ClassReader(input)));
        } catch (Exception exception) {
            Railroad.LOGGER.warn("Ignoring unreadable runtime class {}", classFile, exception);
        }
    }

    private static Path resolveJavaHome() {
        Path configured = normalizeHome(System.getProperty("java.home"));
        if (configured != null && (hasScannableStandardLibrary(configured)
            || Files.isRegularFile(configured.resolve("lib").resolve("modules"))))
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
        } catch (Exception _) {
            return null;
        }
    }

    private static boolean hasScannableStandardLibrary(Path javaHome) {
        if (javaHome == null || Files.notExists(javaHome))
            return false;

        if (Files.isRegularFile(javaHome.resolve("lib").resolve("rt.jar")))
            return true;

        return Files.isRegularFile(javaHome.resolve("jmods").resolve("java.base.jmod"));
    }
}
