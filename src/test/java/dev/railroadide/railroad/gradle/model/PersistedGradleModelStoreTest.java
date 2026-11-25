package dev.railroadide.railroad.gradle.model;

import dev.railroadide.railroadplugin.dto.FabricDataModel;
import dev.railroadide.railroadplugin.dto.RailroadConfiguration;
import dev.railroadide.railroadplugin.dto.RailroadDependency;
import dev.railroadide.railroadplugin.dto.RailroadJavaLanguageSettings;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import dev.railroadide.railroadplugin.dto.RailroadProject;
import dev.railroadide.railroadplugin.dto.impl.*;
import org.gradle.api.JavaVersion;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PersistedGradleModelStoreTest {
    private static final Instant IMPORT_STARTED = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir
    public Path temp;

    private Path root;
    private Path module;
    private Path archive;
    private PersistedGradleModelStore store;

    @BeforeEach
    public void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("build"));
        module = Files.createDirectories(root.resolve("app"));
        archive = temp.resolve("library.jar");
        writeOld(root.resolve("settings.gradle"), "include('app')");
        writeOld(module.resolve("build.gradle"), "plugins { id 'java' }");
        writeOld(archive, "jar");
        store = new PersistedGradleModelStore(temp.resolve("models"), "importer-1");
    }

    @Test
    public void savedModelIsRestoredAsCurrent() throws IOException {
        assertTrue(store.save(root, model(), IMPORT_STARTED));

        PersistedGradleModelStore.Restored restored = store.load(root).orElseThrow();

        assertTrue(restored.current());
        GradleBuildModel model = restored.model();
        assertEquals("9.7.0", model.gradleVersion());
        assertEquals(root, model.rootDir());
        assertEquals("1.21", model.fabricData().minecraftVersion());
        assertTrue(model.fabricData().loomVersion().isArchitecturyLoom());
        RailroadModule restoredModule = model.project().getModules().getAt(0);
        assertEquals(List.of(archive.toFile()), restoredModule.getClasspathRoots());
        assertEquals(JavaVersion.VERSION_21, restoredModule.getJavaLanguageSettings().getSourceCompatibility());
        RailroadDependency dependency = restoredModule.getConfigurations().getAt(0).getDependencies().getAt(0);
        assertEquals("transitive", dependency.getChildren().getAt(0).getName());
    }

    @Test
    public void missingSnapshotRestoresNothing() throws IOException {
        assertEquals(Optional.empty(), store.load(root));
    }

    @Test
    public void changedModuleBuildFileMakesSnapshotStale() throws IOException {
        store.save(root, model(), IMPORT_STARTED);
        Files.writeString(module.resolve("build.gradle"), "plugins { id 'java-library' }");

        PersistedGradleModelStore.Restored restored = store.load(root).orElseThrow();

        assertFalse(restored.current());
        assertEquals(1, restored.model().project().getModules().size());
    }

    @Test
    public void addedBuildFileMakesSnapshotStale() throws IOException {
        store.save(root, model(), IMPORT_STARTED);
        Files.writeString(root.resolve("gradle.properties"), "org.gradle.parallel=true");

        assertFalse(store.load(root).orElseThrow().current());
    }

    @Test
    public void deletedDependencyArchiveMakesSnapshotStale() throws IOException {
        store.save(root, model(), IMPORT_STARTED);
        Files.delete(archive);

        assertFalse(store.load(root).orElseThrow().current());
    }

    @Test
    public void snapshotFromAnotherImporterIsDiscarded() throws IOException {
        store.save(root, model(), IMPORT_STARTED);

        var upgraded = new PersistedGradleModelStore(temp.resolve("models"), "importer-2");

        assertEquals(Optional.empty(), upgraded.load(root));
    }

    @Test
    public void buildFileChangedDuringImportIsNotSaved() throws IOException {
        Files.setLastModifiedTime(module.resolve("build.gradle"), FileTime.from(IMPORT_STARTED.plusSeconds(1)));

        assertFalse(store.save(root, model(), IMPORT_STARTED));
        assertEquals(Optional.empty(), store.load(root));
    }

    @Test
    public void newerImportReplacesSnapshot() throws IOException {
        store.save(root, model(), IMPORT_STARTED);
        Files.writeString(module.resolve("build.gradle"), "plugins { id 'java-library' }");
        Files.setLastModifiedTime(module.resolve("build.gradle"), FileTime.from(IMPORT_STARTED));

        store.save(root, model(), IMPORT_STARTED.plusSeconds(10));

        assertTrue(store.load(root).orElseThrow().current());
    }

    @Test
    public void corruptSnapshotFailsToLoad() throws IOException {
        store.save(root, model(), IMPORT_STARTED);
        try (var files = Files.list(temp.resolve("models"))) {
            for (Path snapshot : files.toList()) {
                byte[] bytes = Files.readAllBytes(snapshot);
                Files.write(snapshot, Arrays.copyOf(bytes, bytes.length / 2));
            }
        }

        assertThrows(IOException.class, () -> store.load(root));
    }

    @Test
    public void toolingApiModelProxiesAreRestored() throws IOException {
        RailroadJavaLanguageSettings settings = new ProtocolToModelAdapter().adapt(RailroadJavaLanguageSettings.class,
            new BasicRailroadJavaLanguageSettings(JavaVersion.VERSION_17, JavaVersion.VERSION_17, null));
        var project = new BasicRailroadProject("build", null, null, List.of(), settings);
        store.save(root, new GradleBuildModel("9.7.0", root, null, project), IMPORT_STARTED);

        GradleBuildModel restored = store.load(root).orElseThrow().model();

        assertEquals(JavaVersion.VERSION_17, restored.project().javaLanguageSettings().getSourceCompatibility());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void proxiesOfOtherInterfacesAreRejected() throws IOException {
        List failures = new ArrayList();
        failures.add(new ProtocolToModelAdapter().adapt(Runnable.class, new SerializableTask()));
        var foreignModule = new BasicRailroadModule("app", null, ":app", module.toFile(), null, null, null,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, failures);
        var project = new BasicRailroadProject("build", null, null, List.of(foreignModule), null);
        store.save(root, new GradleBuildModel("9.7.0", root, null, project), IMPORT_STARTED);

        IOException failure = assertThrows(IOException.class, () -> store.load(root));
        assertInstanceOf(InvalidClassException.class, failure.getCause());
    }

    private GradleBuildModel model() {
        var languageSettings = new BasicRailroadJavaLanguageSettings(JavaVersion.VERSION_21, JavaVersion.VERSION_21,
            null);
        var parent = new BasicRailroadProject("build", null, null, List.of(), languageSettings);
        List<RailroadConfiguration> configurations = new ArrayList<>();
        var appModule = new BasicRailroadModule("app", null, ":app", module.toFile(), parent, languageSettings,
            new BasicRailroadCompilerOutput(false, null, null), List.of(), List.of(archive.toFile()),
            List.of(archive.toFile()), List.of(), configurations, List.of(), null, null, List.of());
        // Like the plugin, children refer to a shallow copy of their parent rather than forming a cycle.
        var moduleReference = new BasicRailroadModule("app", null, ":app", module.toFile(), parent, languageSettings,
            null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, List.of());
        var configuration = new BasicRailroadConfiguration("compileClasspath", null, true, false, true, true,
            List.of("implementation"), Map.of("org.gradle.usage", "java-api"), moduleReference, new ArrayList<>(),
            List.of());
        var transitive = new BasicRailroadDependency(configuration, "org.example", "transitive", "1.0",
            new File("transitive.jar"), "org.example:transitive:1.0", List.of());
        configuration.dependencies().add(new BasicRailroadDependency(configuration, "org.example", "library", "1.0",
            archive.toFile(), "org.example:library:1.0", List.of(transitive)));
        configurations.add(configuration);
        RailroadProject project = new BasicRailroadProject("build", null, null, List.of(appModule),
            languageSettings);
        return new GradleBuildModel("9.7.0", root, new ProxyLikeFabricData(), project);
    }

    private static void writeOld(Path file, String content) throws IOException {
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(IMPORT_STARTED.minusSeconds(60)));
    }

    /**
     * Has the shape of {@link Runnable} without implementing it, so the adapter wraps it in a proxy.
     */
    public static final class SerializableTask implements Serializable {
        public void run() {
        }
    }

    /**
     * Stands in for the Tooling API proxy, which is not serializable.
     */
    private static final class ProxyLikeFabricData implements FabricDataModel {
        @Override
        public String minecraftVersion() {
            return "1.21";
        }

        @Override
        public String mappingsVersion() {
            return "1.21+build.1";
        }

        @Override
        public String loaderVersion() {
            return "0.16.0";
        }

        @Override
        public String fabricApiVersion() {
            return "0.100.0";
        }

        @Override
        public LoomVersion loomVersion() {
            return new LoomVersion() {
                @Override
                public String version() {
                    return "1.7";
                }

                @Override
                public boolean isArchitecturyLoom() {
                    return true;
                }
            };
        }
    }
}
