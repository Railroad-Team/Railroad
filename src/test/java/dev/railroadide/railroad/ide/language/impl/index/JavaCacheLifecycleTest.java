package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexService;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.*;
import java.util.jar.*;
import dev.railroadide.railroad.ide.sst.project.JavaStubSymbolIndex;

import static org.junit.jupiter.api.Assertions.*;

public class JavaCacheLifecycleTest {
    @TempDir
    public Path root;

    @BeforeAll
    public static void initialize() {
        WorkspaceModes.initialize();
    }

    @Test
    public void liveAuxiliaryIndexingObservesRequestCancellationBeforeCachingBinaries() throws Exception {
        Path jar = root.resolve("library.jar");
        Path jdk = root.resolve("jdk");
        archive(jar, "demo/Library", "oldValue");
        archive(jdk.resolve("lib/rt.jar"), "demo/Runtime", "runtime");
        Project project = project();
        var service = Services.PROJECT_LANGUAGE_INDEX_SERVICE;
        var session = service.openProject(project);
        try (var provider = new JavaAnalysisContextProvider()) {
            provider.adoptConfiguration(session, provider.prepareConfiguration(session, configuration(jar, jdk)));
            var cancellation = new CancellationTokenSource();
            List<Path> cancellingRoots = new AbstractList<>() {
                @Override
                public Path get(int index) {
                    Objects.checkIndex(index, 1);
                    cancellation.cancel();
                    return jar;
                }

                @Override
                public int size() {
                    return 1;
                }
            };
            var language = new JavaLanguageIndexContext(List.of(), List.of(), cancellingRoots, List.of(), List.of(),
                jdk);
            var request = new ProjectIndexContext(project, root, Map.of("java", language), session,
                session.configurationGeneration(), cancellation.token());
            assertThrows(CancellationException.class, () -> provider.index(request));
            assertTrue(cancellation.token().isCancellationRequested());

            archive(jar, "demo/Library", "newValue");
            var retryLanguage = new JavaLanguageIndexContext(List.of(), List.of(), List.of(jar), List.of(), List.of(),
                jdk);
            var retry = new ProjectIndexContext(project, root, Map.of("java", retryLanguage), session);
            var result = provider.index(retry);
            assertNotNull(result);
            assertEquals("newValue", result.classStubsByQualifiedName().get("demo.Library").fields().getFirst().name());
        } finally {
            service.closeProject(session);
        }
    }

    @Test
    public void concurrentWarmupsShareOneLibraryBuild() throws Exception {
        Path jar = root.resolve("library.jar");
        Path jdk = root.resolve("jdk");
        archive(jar, "demo/Library", "value");
        archive(jdk.resolve("lib/rt.jar"), "demo/Runtime", "runtime");
        Project project = project();
        var service = Services.PROJECT_LANGUAGE_INDEX_SERVICE;
        var session = service.openProject(project);
        var building = new CountDownLatch(1);
        var waiting = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        CancellationToken pausesWhileBuilding = () -> {
            if (inFrame("dev.railroadide.railroad.ide.sst.project.JavaLibrarySymbolIndex", null)) {
                building.countDown();
                awaitQuietly(release);
            }
            return false;
        };
        CancellationToken reportsWaiting = () -> {
            if (inFrame(JavaAnalysisContextProvider.class.getName() + "$Cache", "acquire")) {
                waiting.countDown();
            }
            return false;
        };
        try (var provider = new JavaAnalysisContextProvider(); var workers = Executors.newFixedThreadPool(2)) {
            provider.adoptConfiguration(session, provider.prepareConfiguration(session, configuration(jar, jdk)));
            var language = new JavaLanguageIndexContext(List.of(), List.of(), List.of(jar), List.of(), List.of(),
                jdk);
            var first = workers.submit(() -> provider.index(new ProjectIndexContext(project, root,
                Map.of("java", language), session, session.configurationGeneration(), pausesWhileBuilding)));
            assertTrue(building.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> provider.index(new ProjectIndexContext(project, root,
                Map.of("java", language), session, session.configurationGeneration(), reportsWaiting)));
            assertTrue(waiting.await(5, TimeUnit.SECONDS), "A second warm-up must wait for the running build");
            release.countDown();

            assertSame(first.get(5, TimeUnit.SECONDS).classStubsByQualifiedName().get("demo.Library"),
                second.get(5, TimeUnit.SECONDS).classStubsByQualifiedName().get("demo.Library"));
        } finally {
            release.countDown();
            service.closeProject(session);
        }
    }

    @Test
    public void liveLookupsKeepConflictingModuleClasspathsSeparate() throws Exception {
        Path common = root.resolve("common/library.jar");
        Path loader = root.resolve("loader/library.jar");
        Path jdk = root.resolve("jdk");
        archive(common, "demo/Library", "vanilla");
        archive(loader, "demo/Library", "patched");
        archive(jdk.resolve("lib/rt.jar"), "demo/Runtime", "runtime");
        var configuration = new WorkspaceConfigurationSnapshot(0, Set.of(":common", ":loader"), Map.of(
            "module.:common.directory", common.getParent().toString(),
            "module.:common.classpath", common.toString(),
            "module.:loader.directory", loader.getParent().toString(),
            "module.:loader.classpath", String.join("\n", loader.toString(), common.toString()),
            "module.:loader.jdkHome", jdk.toString()));
        var language = new JavaLanguageIndexContext(List.of(), List.of(), List.of(), List.of(), List.of(), null,
            configuration);
        Project project = project();
        var service = Services.PROJECT_LANGUAGE_INDEX_SERVICE;
        var session = service.openProject(project);
        try (var provider = new JavaAnalysisContextProvider()) {
            provider.adoptConfiguration(session, provider.prepareConfiguration(session, configuration));
            var context = new ProjectIndexContext(project, root, Map.of("java", language), session);
            assertEquals("patched", provider.index(context, loader.resolveSibling("Use.java"))
                .lookupClassStub("demo.Library").fields().getFirst().name());
            assertEquals("vanilla", provider.index(context, common.resolveSibling("Use.java"))
                .lookupClassStub("demo.Library").fields().getFirst().name());
        } finally {
            service.closeProject(session);
        }
    }

    private static boolean inFrame(String className, String methodName) {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals(className)
            && (methodName == null || frame.getMethodName().equals(methodName))));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void standaloneProviderDoesNotReuseAPathOnlyJdkAcrossConfigurations() throws Exception {
        Path jar = root.resolve("library.jar"), jdk = root.resolve("jdk");
        archive(jar, "demo/Library", "library");
        archive(jdk.resolve("lib/rt.jar"), "demo/Runtime", "oldRuntime");
        var provider = new JavaWorkspaceAnalysisProvider();
        var first = configuration(jar, jdk);
        var retained = provider.capture(new DefaultDocumentStore("test", first).capture());
        archive(jdk.resolve("lib/rt.jar"), "demo/Runtime", "newRuntime");
        var refreshed = new WorkspaceConfigurationSnapshot(2, first.moduleIds(), first.settings());
        var current = provider.capture(new DefaultDocumentStore("test", refreshed).capture());
        assertEquals("newRuntime", current.classStubsByQualifiedName().get("demo.Runtime").fields().getFirst().name());
        assertEquals("oldRuntime", retained.classStubsByQualifiedName().get("demo.Runtime").fields().getFirst().name());
    }

    @Test
    public void blockedBinaryConstructionCannotReturnAfterSessionClosure() throws Exception {
        var service = new ProjectLanguageIndexService();
        var session = service.openProject(project());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var provider = new JavaWorkspaceAnalysisProvider(() -> !session.isClosed(), (paths, jdk) -> {
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                throw new AssertionError(exception);
            }
            return new JavaStubSymbolIndex(Map.of(), Map.of());
        });
        var capture = new DefaultDocumentStore("test", new WorkspaceConfigurationSnapshot(0, Set.of())).capture();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var result = workers.submit(() -> provider.capture(capture));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try {
                workers.submit(() -> service.closeProject(session)).get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            assertInstanceOf(CancellationException.class, assertThrows(ExecutionException.class,
                () -> result.get(5, TimeUnit.SECONDS)).getCause());
        }
    }

    @Test
    public void explicitRefreshReplacesSamePathJarAndJdkWhileRetainedAnswersStayImmutable() throws Exception {
        Path jar = root.resolve("library.jar");
        Path jdk = root.resolve("jdk");
        Path runtime = jdk.resolve("lib/rt.jar");
        archive(jar, "demo/Library", "oldValue");
        archive(runtime, "demo/Runtime", "oldRuntime");
        var service = new ProjectLanguageIndexService();
        var session = service.openProject(project());
        var provider = new JavaAnalysisContextProvider();
        var raw = configuration(jar, jdk);
        var firstConfiguration = provider.prepareConfiguration(session, raw);
        provider.adoptConfiguration(session, firstConfiguration);
        var firstStore = new DefaultDocumentStore("test", firstConfiguration);
        var retained = provider.index(firstStore.capture());
        assertEquals("oldValue", retained.classStubsByQualifiedName().get("demo.Library").fields().getFirst().name());
        var jarTime = Files.getLastModifiedTime(jar);
        var runtimeTime = Files.getLastModifiedTime(runtime);
        archive(jar, "demo/Library", "newValue");
        archive(runtime, "demo/Runtime", "newRuntime");
        Files.setLastModifiedTime(jar, jarTime);
        Files.setLastModifiedTime(runtime, runtimeTime);
        // The immutable configuration, not interactive filesystem inspection, controls reuse.
        assertSame(retained, provider.index(firstStore.capture()));
        var refreshed = provider.prepareConfiguration(session, raw);
        assertNotEquals(firstConfiguration.settings().get("index.binary.epoch"),
            refreshed.settings().get("index.binary.epoch"));
        provider.adoptConfiguration(session, refreshed);
        var current = provider.index(new DefaultDocumentStore("test", refreshed).capture());
        assertEquals("newValue", current.classStubsByQualifiedName().get("demo.Library").fields().getFirst().name());
        assertEquals("newRuntime", current.classStubsByQualifiedName().get("demo.Runtime").fields().getFirst().name());
        assertEquals("oldValue", retained.classStubsByQualifiedName().get("demo.Library").fields().getFirst().name());
        assertThrows(CancellationException.class, () -> provider.index(firstStore.capture()));
        service.closeProject(session);
        provider.closeSession(session);
        provider.closeSession(session);
        assertEquals(0, provider.cachedSessions());
        assertThrows(CancellationException.class,
            () -> provider.index(new DefaultDocumentStore("test", refreshed).capture()));
    }

    @Test
    public void unbuiltSupersededCaptureCannotConstructFromRefreshedDisk() throws Exception {
        Path jar = root.resolve("library.jar"), jdk = root.resolve("jdk");
        archive(jar, "demo/Library", "oldValue");
        archive(jdk.resolve("lib/rt.jar"), "demo/Runtime", "runtime");
        var service = new ProjectLanguageIndexService();
        var session = service.openProject(project());
        var provider = new JavaAnalysisContextProvider();
        var first = provider.prepareConfiguration(session, configuration(jar, jdk));
        provider.adoptConfiguration(session, first);
        var oldCapture = new DefaultDocumentStore("test", first).capture();
        archive(jar, "demo/Library", "newValue");
        var second = provider.prepareConfiguration(session, configuration(jar, jdk));
        provider.adoptConfiguration(session, second);
        assertThrows(CancellationException.class, () -> provider.index(oldCapture));
        service.closeProject(session);
        assertThrows(CancellationException.class, () -> provider.adoptConfiguration(session, first));
    }

    private WorkspaceConfigurationSnapshot configuration(Path jar, Path jdk) {
        return new WorkspaceConfigurationSnapshot(1, Set.of("main"), Map.of(
            "module.main.dependencies", jar.toString(), "module.main.jdkHome", jdk.toString()));
    }

    private Project project() {
        return (Project) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Project.class},
            (proxy, method, args) -> method.getName().equals("getPath") ? root : null);
    }

    private void archive(Path path, String name, String field) throws Exception {
        Files.createDirectories(path.getParent());
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, field, "I", null, null).visitEnd();
        writer.visitEnd();
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            var entry = new JarEntry(name + ".class");
            entry.setTime(0);
            output.putNextEntry(entry);
            output.write(writer.toByteArray());
            output.closeEntry();
        }
    }
}
