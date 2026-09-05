package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JavaJdkSymbolIndex extends JavaStubSymbolIndex {
    private JavaJdkSymbolIndex(
        Map<String, ClassStub> classStubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName
    ) {
        super(classStubsByQualifiedName, sourceByQualifiedName);
    }

    public static JavaJdkSymbolIndex build(Path jdkHome) {
        if (jdkHome == null || Files.notExists(jdkHome))
            return fromCurrentRuntime();

        List<Path> roots = new ArrayList<>();
        Path rtJar = jdkHome.resolve("lib").resolve("rt.jar");
        if (Files.isRegularFile(rtJar)) {
            roots.add(rtJar);
        } else {
            Path jmods = jdkHome.resolve("jmods");
            if (Files.isDirectory(jmods)) {
                try (var paths = Files.list(jmods)) {
                    roots.addAll(paths.filter(path -> path.getFileName().toString().endsWith(".jmod")).toList());
                } catch (IOException _) {
                    return fromCurrentRuntime();
                }
            }
        }

        if (roots.isEmpty())
            return fromCurrentRuntime();

        JavaLibrarySymbolIndex index = JavaLibrarySymbolIndex.build(roots);
        Map<String, ClassStub> classStubsByQualifiedName = new LinkedHashMap<>(index.classStubsByQualifiedName());
        Map<String, Path> sourceByQualifiedName = new LinkedHashMap<>();
        classStubsByQualifiedName.keySet().forEach(qualifiedName -> sourceByQualifiedName.put(qualifiedName, jdkHome));
        return new JavaJdkSymbolIndex(classStubsByQualifiedName, sourceByQualifiedName);
    }

    public static JavaJdkSymbolIndex fromCurrentRuntime() {
        Map<String, ClassStub> classStubsByQualifiedName = new LinkedHashMap<>(
            JavaSemanticAnalyzer.loadJdkClassStubsByQualifiedName());
        Map<String, Path> sourceByQualifiedName = new LinkedHashMap<>();
        Path runtimeHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
        classStubsByQualifiedName.keySet()
            .forEach(qualifiedName -> sourceByQualifiedName.put(qualifiedName, runtimeHome));
        return new JavaJdkSymbolIndex(classStubsByQualifiedName, sourceByQualifiedName);
    }
}
