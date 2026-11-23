package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;

import java.io.IOException;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary symbol index for a selected JDK or the currently running Java runtime.
 */
public final class JavaJdkSymbolIndex extends JavaStubSymbolIndex {
    private JavaJdkSymbolIndex(JavaLibrarySymbolIndex index) {
        super(index);
    }
    private JavaJdkSymbolIndex(
        Map<String, ClassStub> classStubsByQualifiedName,
        Map<String, Path> sourceByQualifiedName,
        Map<String, DocumentUri> locations,
        Runnable activityCheck
    ) {
        super(classStubsByQualifiedName, sourceByQualifiedName, locations, Map.of(), activityCheck, false);
    }

    /**
     * Indexes a JDK's runtime archive or modules, falling back to the current runtime when roots are unavailable.
     *
     * @param jdkHome the JDK installation directory, or {@code null} to use the current runtime
     * @return the selected JDK index, or a current-runtime index on fallback
     */
    public static JavaJdkSymbolIndex build(Path jdkHome) {
        return build(jdkHome, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Indexes a selected JDK with cooperative controls during scanning and lookup-table construction.
     *
     * @param jdkHome JDK home, or null for the running runtime
     * @param activityCheck request activity check
     * @return complete immutable binary index
     */
    public static JavaJdkSymbolIndex build(Path jdkHome, Runnable activityCheck) {
        activityCheck.run();
        if (jdkHome == null || Files.notExists(jdkHome))
            return fromCurrentRuntime(activityCheck);

        List<Path> roots = new ArrayList<>();
        Path rtJar = jdkHome.resolve("jre/lib/rt.jar");
        if (!Files.isRegularFile(rtJar)) {
            rtJar = jdkHome.resolve("lib/rt.jar");
        }
        if (Files.isRegularFile(rtJar)) {
            roots.add(rtJar);
        } else {
            Path jmods = jdkHome.resolve("jmods");
            if (Files.isDirectory(jmods)) {
                try (var paths = Files.list(jmods)) {
                    roots.addAll(
                        paths.filter(path -> path.getFileName().toString().endsWith(".jmod")).sorted().toList());
                } catch (IOException _) {
                    return fromCurrentRuntime(activityCheck);
                }
            }
        }

        if (roots.isEmpty())
            return fromCurrentRuntime(activityCheck);

        JavaLibrarySymbolIndex index = JavaLibrarySymbolIndex.build(roots, activityCheck,
            JavaLibraryStubCache.shared().orElse(null),
            Files.isRegularFile(rtJar) ? "railroad.activity.jdk_runtime" : "railroad.activity.jdk_modules");
        activityCheck.run();
        return new JavaJdkSymbolIndex(index);
    }

    /**
     * Builds an index from the cached standard-library class stubs of the current runtime.
     *
     * @return the current-runtime symbol index
     */
    public static JavaJdkSymbolIndex fromCurrentRuntime() {
        return fromCurrentRuntime(ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Reuses or scans runtime stubs while checking the current request.
     *
     * @param activityCheck request activity check
     * @return complete immutable runtime index
     */
    public static JavaJdkSymbolIndex fromCurrentRuntime(Runnable activityCheck) {
        activityCheck.run();
        ActivityProgress.current().indeterminate("railroad.activity.jdk_runtime");
        Map<String, ClassStub> classStubsByQualifiedName = JavaSemanticAnalyzer
            .loadJdkClassStubsByQualifiedName(activityCheck);
        ActivityProgress.current().indeterminate("railroad.activity.finalizing");
        Map<String, Path> sourceByQualifiedName = new LinkedHashMap<>();
        Map<String, DocumentUri> locations = new LinkedHashMap<>();
        Path runtimeHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
        classStubsByQualifiedName.keySet()
            .forEach(qualifiedName -> {
                ActivityCheckpoint.poll(activityCheck);
                sourceByQualifiedName.put(qualifiedName, runtimeHome);
                String entry = qualifiedName.replace('.', '/') + ".class";
                var resource = ClassLoader.getSystemResource(entry);
                if (resource != null) {
                    locations.put(qualifiedName, DocumentUri.parse(resource.toExternalForm()));
                }
            });
        return new JavaJdkSymbolIndex(classStubsByQualifiedName, sourceByQualifiedName, locations, activityCheck);
    }
}
