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
import javafx.application.Platform;
import org.gradle.tooling.model.DomainObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import dev.railroadide.railroad.ide.language.impl.index.JavaLanguageIndexContext;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysisProvider;
import dev.railroadide.railroad.ide.completion.JavaCompletionProvider;
import dev.railroadide.railroad.ide.completion.CompletionResult;
import dev.railroadide.railroad.ide.diagnostics.JavaDiagnosticsProvider;
import dev.railroadide.railroad.ide.signature.SignatureHelpArchiveProbe;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectWorkspaceConfigurationTest {
    @TempDir
    public Path directory;

    @Test
    public void liveSessionRefreshPublishesANewEpochWithUnchangedSettings() throws Exception {
        var project = new RailroadProject(directory, "Refresh", new WritableImage(1, 1));
        var service = Services.PROJECT_LANGUAGE_INDEX_SERVICE;
        var session = service.openProject(project);
        var store = new DefaultDocumentStore(project.getId(), ProjectWorkspaceConfiguration.initial(project));
        try (var tracker = new ProjectWorkspaceConfiguration(project, store)) {
            tracker.ready().get(10, TimeUnit.SECONDS);
            var first = store.capture().configuration();
            assertEquals(session.id(), first.settings().get("index.session"));
            assertNotNull(first.settings().get("index.binary.fingerprint"));
            long generation = session.configurationGeneration();
            tracker.refresh();
            tracker.ready().get(10, TimeUnit.SECONDS);
            var refreshed = store.capture().configuration();
            assertNotEquals(first.settings().get("index.binary.epoch"), refreshed.settings().get("index.binary.epoch"));
            assertEquals(first.settings().get("index.binary.fingerprint"),
                refreshed.settings().get("index.binary.fingerprint"));
            assertTrue(session.configurationGeneration() > generation);
        } finally {
            service.closeProject(session);
            JavaLanguageSupport.analysisContextProvider().closeSession(session);
        }
    }

    @BeforeAll
    public static void initialize() {
        WorkspaceModes.initialize();
        LanguageSupports.initialize();
    }

    @BeforeEach
    public void normalizeProjectDirectory() throws Exception {
        directory = directory.toRealPath();
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

    @Test
    public void plainProjectsCaptureLocalJarsInStableOrderAndRefreshDependencies() throws Exception {
        Files.createDirectories(directory.resolve("src/main/java"));
        Path first = libraryJar("lib/z.jar", "fromFirst");
        Path second = libraryJar("libs/a.jar", "fromSecond");
        var project = new RailroadProject(directory, "Plain", new WritableImage(1, 1));
        var store = new DefaultDocumentStore(project.getId(), ProjectWorkspaceConfiguration.initial(project));
        TextDocumentSnapshot request = libraryRequest(store);
        try (ProjectWorkspaceConfiguration tracker = new ProjectWorkspaceConfiguration(project, store)) {
            WorkspaceSnapshot captured = store.capture();
            assertLibraryFeatures(project, captured, request, "fromFirst", List.of(first, second));
            Path added = libraryJar("lib/a.jar", "fromAdded");
            Railroad.EVENT_BUS.publish(new ProjectConfigurationChangedEvent(project));
            assertLibraryFeatures(project, store.capture(), request, "fromAdded", List.of(added, first, second));
            assertLibraryFeatures(project, captured, request, "fromFirst", List.of(first, second));
        }
    }

    @Test
    public void mavenProjectsPublishResolvedDependenciesAndPreserveCapturedClasspathOrder() throws Exception {
        Path sourceRoot = directory.resolve("custom-source");
        Path generatedRoot = directory.resolve("target/generated-sources");
        Path first = libraryJar("dependencies/z.jar", "fromFirst");
        Path second = libraryJar("dependencies/a.jar", "fromSecond");
        Files.writeString(directory.resolve("pom.xml"), mavenPom(first, second));
        writeStaleClass("target/classes", "Removed");
        writeStaleClass("target/test-classes", "RemovedTest");
        RailroadProject project = mavenProject();
        var store = new DefaultDocumentStore(project.getId(), ProjectWorkspaceConfiguration.initial(project));
        TextDocumentSnapshot request = libraryRequest(store);
        try (ProjectWorkspaceConfiguration tracker = new ProjectWorkspaceConfiguration(project, store)) {
            tracker.ready().get(30, TimeUnit.SECONDS);
            WorkspaceSnapshot captured = store.capture();
            String sources = captured.configuration().settings().get("module.:.sources");
            assertTrue(sources.contains(sourceRoot + ":generated=false"));
            assertTrue(sources.contains(generatedRoot + ":generated=true"));
            assertLibraryFeatures(project, captured, request, "fromFirst", List.of(first, second));
            JavaWorkspaceAnalysis analysis = new JavaWorkspaceAnalysisProvider().capture(captured);
            assertTrue(analysis.lookupQualifiedName("deps.Removed").isEmpty());
            assertTrue(analysis.lookupQualifiedName("deps.RemovedTest").isEmpty());
            Files.writeString(directory.resolve("pom.xml"), mavenPom(second, first));
            Railroad.EVENT_BUS.publish(new ProjectConfigurationChangedEvent(project));
            tracker.ready().get(30, TimeUnit.SECONDS);
            assertLibraryFeatures(project, store.capture(), request, "fromSecond", List.of(second, first));
            assertLibraryFeatures(project, captured, request, "fromFirst", List.of(first, second));
        }
    }

    @Test
    public void mavenResolutionLeavesFxResponsiveAndDiscardsSupersededAndClosedRequests() throws Exception {
        var started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException _) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Files.writeString(directory.resolve("pom.xml"),
            "<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId>"
                + "<artifactId>app</artifactId><version>1</version></project>");
        RailroadProject project = mavenProject();
        DefaultDocumentStore store = onFx(() -> new DefaultDocumentStore(project.getId(),
            ProjectWorkspaceConfiguration.initial(project)));
        var requests = new LinkedBlockingQueue<PendingResolution>();
        try (ProjectWorkspaceConfiguration tracker = onFx(() -> new ProjectWorkspaceConfiguration(project, store,
            _ -> {
                assertFalse(Platform.isFxApplicationThread());
                var pending = new PendingResolution(Thread.currentThread(), new CompletableFuture<>());
                requests.add(pending);
                return pending.result().join();
            }))) {
            PendingResolution first = awaitRequest(requests);
            WorkspaceSnapshot initial = store.capture();
            onFx(() -> {
                project.getDataStore().writeString("maven/settings.json", "{\"revision\":2}");
                return null;
            });
            PendingResolution second = awaitRequest(requests);
            assertEquals(initial, store.capture());
            second.result().complete(dependencyContext("new.jar"));
            tracker.ready().get(10, TimeUnit.SECONDS);
            WorkspaceSnapshot resolved = store.capture();
            assertEquals(directory.resolve("new.jar").toString(),
                resolved.configuration().settings().get("module.:.classpath"));
            first.result().complete(dependencyContext("old.jar"));
            first.awaitFinished();
            assertEquals(resolved, store.capture());

            project.getDataStore().writeString("maven/settings.json", "{\"revision\":3}");
            PendingResolution failed = awaitRequest(requests);
            assertEquals(resolved, store.capture());
            failed.result().completeExceptionally(new IllegalStateException("Dependency repository unavailable"));
            assertThrows(ExecutionException.class, () -> tracker.ready().get(10, TimeUnit.SECONDS));
            assertEquals(resolved, store.capture());

            Railroad.EVENT_BUS.publish(new ProjectConfigurationChangedEvent(project));
            PendingResolution closing = awaitRequest(requests);
            tracker.close();
            closing.result().complete(dependencyContext("closed.jar"));
            closing.awaitFinished();
            assertEquals(resolved, store.capture());
        } finally {
            for (PendingResolution pending : requests) {
                pending.result().cancel(false);
            }
        }
    }

    private RailroadProject mavenProject() {
        return new RailroadProject(directory, "Maven", new WritableImage(1, 1)) {
            @Override
            public boolean hasFacet(FacetType<?> type) {
                return type.equals(FacetManager.MAVEN);
            }

            @Override
            public List<Facet<?>> getFacets() {
                return List.of(new Facet<>(FacetManager.MAVEN));
            }
        };
    }

    private JavaLanguageIndexContext dependencyContext(String jar) {
        List<Path> dependencies = List.of(directory.resolve(jar));
        return new JavaLanguageIndexContext(List.of(directory.resolve("src/main/java")), List.of(),
            dependencies, dependencies, List.of(), Path.of(System.getProperty("java.home")));
    }

    private static PendingResolution awaitRequest(BlockingQueue<PendingResolution> requests) throws Exception {
        PendingResolution pending = requests.poll(10, TimeUnit.SECONDS);
        assertNotNull(pending);
        return pending;
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private record PendingResolution(Thread worker, CompletableFuture<JavaLanguageIndexContext> result) {
        private void awaitFinished() throws Exception {
            worker.join(10_000);
            assertFalse(worker.isAlive());
        }
    }

    private void writeStaleClass(String output, String name) throws Exception {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "deps/" + name, null, "java/lang/Object", null);
        writer.visitEnd();
        Path file = directory.resolve(output).resolve("deps/" + name + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, writer.toByteArray());
    }

    private TextDocumentSnapshot libraryRequest(DefaultDocumentStore store) {
        var request = new TextDocumentSnapshot(DocumentId.create(),
            DocumentUri.fromPath(directory.resolve("scratch/Use.java")), DocumentVersion.initial(), "java",
            "import deps.Library; class Use { void run() { Library.answer(1); Library. } }", StandardCharsets.UTF_8);
        store.publishBacking(request);
        return request;
    }

    @Test
    public void configuredSourceRootsIncludeNewUnsavedFilesBeforeDirectoriesExist() {
        var project = new RailroadProject(directory, "New sources", new WritableImage(1, 1));
        var store = new DefaultDocumentStore(project.getId(), ProjectWorkspaceConfiguration.initial(project));
        Path source = project.getPath().resolve("src/main/java/Dep.java");
        Path testSource = project.getPath().resolve("src/test/java/TestDep.java");
        assertFalse(Files.exists(source.getParent()));
        assertFalse(Files.exists(testSource.getParent()));
        for (Path path : List.of(source, testSource)) {
            String name = path.getFileName().toString().replace(".java", "");
            store.publishOverlay(new TextDocumentSnapshot(DocumentId.create(), DocumentUri.fromPath(path),
                DocumentVersion.initial(), "java", "class " + name + " {}", StandardCharsets.UTF_8));
        }
        JavaWorkspaceAnalysis analysis = new JavaWorkspaceAnalysisProvider().capture(store.capture());
        assertEquals(Set.of(source, testSource), analysis.sourceTexts().keySet());
    }

    private void assertLibraryFeatures(
        RailroadProject project,
        WorkspaceSnapshot captured,
        TextDocumentSnapshot request,
        String field,
        List<Path> classpath
    ) throws Exception {
        JavaWorkspaceAnalysis analysis = new JavaWorkspaceAnalysisProvider().capture(captured, request.id());
        assertEquals(classpath, analysis.classpath());
        assertEquals(field, analysis.lookupClassStub("deps.Library").fields().getFirst().name());
        CompletionResult completion = new JavaCompletionProvider(project).compute(captured, request.id(),
            request.text().lastIndexOf("Library.") + "Library".length());
        assertNotNull(completion);
        assertTrue(completion.items().stream().anyMatch(item -> item.insertText().equals(field)));
        SignatureHelpArchiveProbe.verify(directory, analysis.jdkHome(), analysis.classpath(), request.text(),
            request.text().indexOf("1)") + 1, "deps.Library", "answer", List.of("int"));
        assertTrue(new JavaDiagnosticsProvider().compute(captured, request.id()).stream()
            .noneMatch(diagnostic -> diagnostic.code().equals("SEM_UNRESOLVED_CALL")
                || diagnostic.code().equals("SEM_UNRESOLVED_TYPE")));
    }

    private String mavenPom(Path first, Path second) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>app</artifactId>"
            + "<version>1</version><build><sourceDirectory>custom-source</sourceDirectory></build><dependencies>"
            + mavenDependency("first", first) + mavenDependency("second", second) + "</dependencies></project>";
    }

    private String mavenDependency(String name, Path jar) {
        return "<dependency><groupId>test</groupId><artifactId>" + name
            + "</artifactId><version>1</version><scope>system</scope><systemPath>"
            + jar.toString().replace("&", "&amp;") + "</systemPath></dependency>";
    }

    private Path libraryJar(String relative, String field) throws Exception {
        Path archive = directory.resolve(relative);
        Files.createDirectories(archive.getParent());
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "deps/Library", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, field, "I", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, "answer", "(I)I", null, null)
            .visitEnd();
        writer.visitEnd();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry("deps/Library.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
        return archive;
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
