package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.GradleModelListener;
import dev.railroadide.railroad.gradle.project.GradleManager;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.plugin.spi.events.ProjectConfigurationChangedEvent;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.FacetType;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import javafx.scene.image.WritableImage;
import org.gradle.tooling.model.DomainObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectWorkspaceConfigurationTest {
    @TempDir
    public Path directory;

    @BeforeAll
    public static void initialize() {
        WorkspaceModes.initialize();
    }

    @Test
    public void settingsAndBuildChangesPublishConfigurationWithoutChangingDocumentVersions() throws Exception {
        var project = new RailroadProject(directory, "Configuration", new WritableImage(1, 1));
        var store = new DefaultDocumentStore(project.getId(),
            ProjectWorkspaceConfiguration.initial(project));
        DocumentId id = DocumentId.create();
        var document = new TextDocumentSnapshot(id, DocumentUri.inMemory(id),
            new DocumentVersion(7), "java", "original", StandardCharsets.UTF_8);
        store.publishBacking(document);
        try (ProjectWorkspaceConfiguration tracker = new ProjectWorkspaceConfiguration(project, store)) {
            WorkspaceSnapshot original = store.capture();
            project.getDataStore().writeString("gradle/settings.json", "{\"offline\":true}");
            WorkspaceSnapshot changed = store.capture();
            assertTrue(changed.configuration().revision() > original.configuration().revision());
            assertEquals("{\"offline\":true}",
                changed.configuration().settings().get("project.settings.gradle/settings.json"));
            assertSame(document, changed.document(id).orElseThrow());
            assertFalse(original.configuration().settings().containsKey("project.settings.gradle/settings.json"));
            assertThrows(UnsupportedOperationException.class, () -> changed.configuration().settings().clear());

            Files.writeString(directory.resolve("build.gradle"), "plugins { id 'java' }");
            Railroad.EVENT_BUS.publish(new ProjectConfigurationChangedEvent(project));
            WorkspaceSnapshot imported = store.capture();
            assertTrue(imported.configuration().revision() > changed.configuration().revision());
            assertEquals("plugins { id 'java' }",
                imported.configuration().settings().get("project.build.build.gradle"));
            assertSame(document, imported.document(id).orElseThrow());
            Railroad.EVENT_BUS.publish(new ProjectConfigurationChangedEvent(project));
            assertEquals(imported, store.capture());
        }
        WorkspaceSnapshot closed = store.capture();
        project.getDataStore().writeString("gradle/settings.json", "{\"offline\":false}");
        Railroad.EVENT_BUS.publish(new ProjectConfigurationChangedEvent(project));
        assertEquals(closed, store.capture());
    }

    @Test
    public void mavenModuleConfigurationChangesAreVisibleInWorkspaceCaptures() throws Exception {
        String rootPom = "<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>root</artifactId>"
            + "<version>1</version><packaging>pom</packaging><modules><module>app</module></modules></project>";
        Files.writeString(directory.resolve("pom.xml"), rootPom);
        Path app = Files.createDirectory(directory.resolve("app"));
        Files.writeString(app.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId>"
            + "<artifactId>app</artifactId><version>1</version></project>");
        var project = new RailroadProject(directory, "Maven modules", new WritableImage(1, 1));
        var store = new DefaultDocumentStore(project.getId(),
            ProjectWorkspaceConfiguration.initial(project));
        try (ProjectWorkspaceConfiguration tracker = new ProjectWorkspaceConfiguration(project, store)) {
            WorkspaceSnapshot before = store.capture();
            assertEquals(Set.of(":", ":app"), before.configuration().moduleIds());
            Files.writeString(directory.resolve("pom.xml"), rootPom.replace("<module>app</module>", ""));
            Railroad.EVENT_BUS.publish(new ProjectConfigurationChangedEvent(project));
            assertEquals(Set.of(":"), store.capture().configuration().moduleIds());
            assertTrue(store.capture().configuration().revision() > before.configuration().revision());
            assertEquals(Set.of(":", ":app"), before.configuration().moduleIds());
        }
    }

    @Test
    public void moduleReloadsPublishImmutableClasspathAndRetainLastConfigurationOnFailure() throws Exception {
        Files.writeString(directory.resolve("build.gradle"), "plugins { id 'java' }");
        RailroadProject project = new RailroadProject(directory, "Modules", new WritableImage(1, 1)) {
            @Override
            public boolean hasFacet(FacetType<?> type) {
                return type.equals(FacetManager.GRADLE);
            }

            @Override
            public List<Facet<?>> getFacets() {
                return List.of(new Facet<>(FacetManager.GRADLE));
            }
        };
        var models = new FakeModels();
        Field service = GradleManager.class.getDeclaredField("modelService");
        service.setAccessible(true);
        service.set(project.getGradleManager(), models);
        var store = new DefaultDocumentStore(project.getId(),
            ProjectWorkspaceConfiguration.initial(project));
        try (ProjectWorkspaceConfiguration tracker = new ProjectWorkspaceConfiguration(project, store)) {
            models.publish(model(":main", directory.resolve("a.jar")));
            WorkspaceSnapshot first = store.capture();
            assertEquals(Set.of(":main"), first.configuration().moduleIds());
            assertEquals(directory.resolve("a.jar").toString(),
                first.configuration().settings().get("module.:main.classpath"));
            models.publish(model(":main", directory.resolve("a.jar")));
            assertEquals(first, store.capture());
            models.publish(model(":main", directory.resolve("b.jar")));
            WorkspaceSnapshot second = store.capture();
            assertEquals(first.configuration().revision() + 1, second.configuration().revision());
            assertEquals(directory.resolve("a.jar").toString(),
                first.configuration().settings().get("module.:main.classpath"));
            assertEquals(directory.resolve("b.jar").toString(),
                second.configuration().settings().get("module.:main.classpath"));
            models.listeners
                .forEach(listener -> listener.modelReloadFailed(new IllegalStateException("import failed")));
            assertEquals(second, store.capture());
        }
        assertTrue(models.listeners.isEmpty());
    }

    @Test
    public void classpathOrderChangesAdvanceConfiguration() throws Exception {
        Files.writeString(directory.resolve("build.gradle"), "plugins { id 'java' }");
        RailroadProject project = new RailroadProject(directory, "Modules", new WritableImage(1, 1)) {
            @Override
            public boolean hasFacet(FacetType<?> type) {
                return type.equals(FacetManager.GRADLE);
            }

            @Override
            public List<Facet<?>> getFacets() {
                return List.of(new Facet<>(FacetManager.GRADLE));
            }
        };
        var models = new FakeModels();
        Field service = GradleManager.class.getDeclaredField("modelService");
        service.setAccessible(true);
        service.set(project.getGradleManager(), models);
        var store = new DefaultDocumentStore(project.getId(), ProjectWorkspaceConfiguration.initial(project));
        Path first = directory.resolve("z.jar");
        Path second = directory.resolve("a.jar");
        try (ProjectWorkspaceConfiguration tracker = new ProjectWorkspaceConfiguration(project, store)) {
            models.publish(model(":main", first, second));
            WorkspaceSnapshot before = store.capture();
            String original = before.configuration().settings().get("module.:main.classpath");
            assertTrue(original.indexOf(first.toString()) < original.indexOf(second.toString()));
            models.publish(model(":main", second, first));
            String reordered = store.capture().configuration().settings().get("module.:main.classpath");
            assertTrue(reordered.indexOf(second.toString()) < reordered.indexOf(first.toString()));
            assertEquals(before.configuration().revision() + 1, store.capture().configuration().revision());
            assertEquals(original, before.configuration().settings().get("module.:main.classpath"));
        }
    }

    private GradleBuildModel model(String id, Path... classpath) {
        RailroadModule module = (RailroadModule) Proxy.newProxyInstance(RailroadModule.class.getClassLoader(),
            new Class<?>[]{RailroadModule.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getPath" -> id;
                case "getProjectDir" -> directory;
                case "getClasspathRoots" -> Arrays.stream(classpath).map(Path::toFile).toList();
                case "getDependencyRoots", "getModulePathRoots" -> List.of();
                case "getContentRoots" -> new ModelSet<>();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });
        ModelSet<RailroadModule> modules = new ModelSet<>();
        modules.add(module);
        dev.railroadide.railroadplugin.dto.RailroadProject root = (dev.railroadide.railroadplugin.dto.RailroadProject) Proxy
            .newProxyInstance(RailroadModule.class.getClassLoader(),
                new Class<?>[]{dev.railroadide.railroadplugin.dto.RailroadProject.class},
                (proxy, method, args) -> method.getName().equals("getModules") ? modules : null);
        return new GradleBuildModel("9.1", directory, null, root);
    }

    private static final class ModelSet<T> extends LinkedHashSet<T> implements DomainObjectSet<T> {
        @Override
        public List<T> getAll() {
            return List.copyOf(this);
        }

        @Override
        public T getAt(int index) {
            return getAll().get(index);
        }
    }

    private static final class FakeModels implements GradleModelService {
        private final List<GradleModelListener> listeners = new CopyOnWriteArrayList<>();
        private GradleBuildModel cached;

        private void publish(GradleBuildModel model) {
            cached = model;
            listeners.forEach(listener -> listener.modelReloadSucceeded(model));
        }

        @Override
        public CompletableFuture<GradleBuildModel> refreshModel(boolean force) {
            return CompletableFuture.completedFuture(cached);
        }

        @Override
        public Optional<GradleBuildModel> getCachedModel() {
            return Optional.ofNullable(cached);
        }

        @Override
        public void addListener(GradleModelListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(GradleModelListener listener) {
            listeners.remove(listener);
        }
    }
}
