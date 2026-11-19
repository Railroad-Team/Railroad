package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.WorkspaceRequestContext;
import dev.railroadide.railroad.ide.sst.provider.api.ProviderExecutor;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.language.LanguageRuntime;
import dev.railroadide.railroad.ide.language.LanguageCapabilities;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.language.impl.index.BasicLanguageIndexContext;
import java.util.concurrent.atomic.AtomicInteger;
import dev.railroadide.railroad.ide.language.impl.index.JavaProjectLanguageIndexer;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticPersistence;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectLanguageIndexCoordinatorTest {

    @TempDir
    public Path tempDir;

    @Test
    public void fileEventsUseRuntimeWinnerAndDiscoverLanguagesLoadedAfterProjectOpen() throws Exception {
        Path file = tempDir.resolve("shared.conf");
        Files.writeString(file, "value");
        try (var runtime = new LanguageRuntime()) {
            var service = new ProjectLanguageIndexService(runtime);
            var lowerUpdates = new AtomicInteger();
            var higherUpdates = new AtomicInteger();
            var lower = indexingContribution("example:lower", 1, lowerUpdates);
            var higher = indexingContribution("example:higher", 10, higherUpdates);
            runtime.register("lower-plugin", lower);
            var context = createContext(tempDir, service);
            var coordinator = new ProjectLanguageIndexCoordinator(context, service);
            coordinator.handleFileChange(file, StandardWatchEventKinds.ENTRY_MODIFY);
            assertEquals(1, lowerUpdates.get());

            runtime.register("higher-plugin", higher);
            assertSame(higher, service.findLanguage(file).orElseThrow());
            assertSame(runtime.find(file).orElseThrow(), service.findLanguage(file).orElseThrow());
            coordinator.handleFileChange(file, StandardWatchEventKinds.ENTRY_MODIFY);
            assertEquals(1, higherUpdates.get());
            assertEquals(1, lowerUpdates.get());
            assertNotNull(service.current(context.project(), "example:higher"));

            runtime.unregisterOwner("higher-plugin");
            assertNull(service.current(context.project(), "example:higher"));
            coordinator.handleFileChange(file, StandardWatchEventKinds.ENTRY_MODIFY);
            assertEquals(2, lowerUpdates.get());
        }
    }

    @Test
    public void fullAndRebuiltIndexesOnlyContainTheWinningLanguageFiles() throws Exception {
        Path file = tempDir.resolve("shared.conf");
        Files.writeString(file, "value");
        try (var runtime = new LanguageRuntime()) {
            runtime.register("low", indexingContribution("example:low", 1, new AtomicInteger()));
            runtime.register("high", indexingContribution("example:high", 10, new AtomicInteger()));
            var service = new ProjectLanguageIndexService(runtime);
            var context = createContext(tempDir, service);
            var coordinator = new ProjectLanguageIndexCoordinator(context, service);
            coordinator.warmIndexes();
            assertNull(service.current(context.project(), "example:low").getFileIndex(file));
            assertNotNull(service.current(context.project(), "example:high").getFileIndex(file));
            assertNull(service.rebuild(context.project(), "example:low").getFileIndex(file));
            assertNotNull(service.rebuild(context.project(), "example:high").getFileIndex(file));
            Files.delete(file);
            coordinator.handleFileChange(file, StandardWatchEventKinds.ENTRY_DELETE);
            assertNull(service.current(context.project(), "example:low").getFileIndex(file));
            assertNull(service.current(context.project(), "example:high").getFileIndex(file));
        }
    }

    @Test
    public void warmingAfterRegistrationChangesTransfersPreviouslyIndexedFiles() throws Exception {
        Path file = tempDir.resolve("shared.conf");
        Files.writeString(file, "value");
        try (var runtime = new LanguageRuntime()) {
            runtime.register("low", indexingContribution("example:low", 1, new AtomicInteger()));
            var service = new ProjectLanguageIndexService(runtime);
            var context = createContext(tempDir, service);
            var coordinator = new ProjectLanguageIndexCoordinator(context, service);
            coordinator.warmIndexes();
            assertNotNull(service.current(context.project(), "example:low").getFileIndex(file));
            runtime.register("high", indexingContribution("example:high", 10, new AtomicInteger()));
            coordinator.warmIndexes();
            assertNull(service.current(context.project(), "example:low").getFileIndex(file));
            assertNotNull(service.current(context.project(), "example:high").getFileIndex(file));
            runtime.unregisterOwner("high");
            coordinator.warmIndexes();
            assertNotNull(service.current(context.project(), "example:low").getFileIndex(file));
        }
    }

    @Test
    public void deletingAContentMatchedFileRemovesItsIndexEntry() throws Exception {
        Path file = tempDir.resolve("script");
        Files.writeString(file, "#!/usr/bin/env tiny\nlet value = 1");
        try (var runtime = new LanguageRuntime()) {
            runtime.register("tiny", indexingContribution("example:tiny", 1, new AtomicInteger(),
                LanguageMatcher.shebang("tiny")));
            var service = new ProjectLanguageIndexService(runtime);
            var context = createContext(tempDir, service);
            var coordinator = new ProjectLanguageIndexCoordinator(context, service);
            coordinator.warmIndexes();
            assertNotNull(service.current(context.project(), "example:tiny").getFileIndex(file));
            Files.delete(file);
            assertTrue(runtime.find(file).isEmpty());
            coordinator.handleFileChange(file, StandardWatchEventKinds.ENTRY_DELETE);
            assertNull(service.current(context.project(), "example:tiny").getFileIndex(file));
        }
    }

    @Test
    public void contentChangesRemovePreviousOwnershipEvenWhenNoLanguageMatches() throws Exception {
        Path file = tempDir.resolve("script");
        Files.writeString(file, "#!/usr/bin/env tiny\nlet value = 1");
        try (var runtime = new LanguageRuntime()) {
            runtime.register("tiny", indexingContribution("example:tiny", 1, new AtomicInteger(),
                LanguageMatcher.shebang("tiny")));
            runtime.register("other", indexingContribution("example:other", 1, new AtomicInteger(),
                LanguageMatcher.shebang("other")));
            var service = new ProjectLanguageIndexService(runtime);
            var context = createContext(tempDir, service);
            var coordinator = new ProjectLanguageIndexCoordinator(context, service);
            coordinator.warmIndexes();
            Files.writeString(file, "#!/usr/bin/env other\nvalue = 2");
            coordinator.handleFileChange(file, StandardWatchEventKinds.ENTRY_MODIFY);
            assertNull(service.current(context.project(), "example:tiny").getFileIndex(file));
            assertNotNull(service.current(context.project(), "example:other").getFileIndex(file));
            Files.writeString(file, "unrecognized");
            coordinator.handleFileChange(file, StandardWatchEventKinds.ENTRY_MODIFY);
            assertNull(service.current(context.project(), "example:other").getFileIndex(file));
        }
    }

    @Test
    public void registrationChangeRejectsAnInFlightBuildWithPreviousOwnership() throws Exception {
        Path file = tempDir.resolve("shared.conf");
        Files.writeString(file, "value");
        var entered = new CountDownLatch(1);
        var release = new CompletableFuture<Void>();
        try (var runtime = new LanguageRuntime(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            runtime.register("low", indexingContribution("example:low", 1, new AtomicInteger(),
                LanguageMatcher.extension("conf"), () -> {
                    entered.countDown();
                    release.join();
                }));
            var service = new ProjectLanguageIndexService(runtime);
            var context = createContext(tempDir, service);
            var pending = executor.submit(() -> service.index(context.project(), "example:low"));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                runtime.register("high", indexingContribution("example:high", 10, new AtomicInteger()));
                release.complete(null);
                assertInstanceOf(CancellationException.class,
                    assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS)).getCause());
                assertNull(service.current(context.project(), "example:low"));
                new ProjectLanguageIndexCoordinator(context, service).warmIndexes();
                assertNull(service.current(context.project(), "example:low").getFileIndex(file));
                assertNotNull(service.current(context.project(), "example:high").getFileIndex(file));
            } finally {
                release.complete(null);
            }
        }
    }

    private static LanguageContribution indexingContribution(String id, int priority, AtomicInteger updates) {
        return indexingContribution(id, priority, updates, LanguageMatcher.extension("conf"));
    }

    private static LanguageContribution indexingContribution(
        String id,
        int priority,
        AtomicInteger updates,
        LanguageMatcher matcher
    ) {
        return indexingContribution(id, priority, updates, matcher, () -> {
        });
    }

    private static LanguageContribution indexingContribution(
        String id,
        int priority,
        AtomicInteger updates,
        LanguageMatcher matcher,
        Runnable build
    ) {
        var indexer = (ProjectLanguageIndexer<?, ?>) Proxy.newProxyInstance(
            ProjectLanguageIndexer.class.getClassLoader(), new Class<?>[]{ProjectLanguageIndexer.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "languageId" -> id;
                case "supports" -> true;
                case "build" -> {
                    build.run();
                    Map<Path, LanguageFileIndex> files = new HashMap<>();
                    for (Object value : (Collection<?>) arguments[1]) {
                        Path path = (Path) value;
                        files.put(path, () -> path);
                    }
                    yield new TestIndex(id, files);
                }
                case "indexFile" -> {
                    updates.incrementAndGet();
                    yield (LanguageFileIndex) () -> (Path) arguments[1];
                }
                case "withUpdatedFile", "withRemovedFile" -> {
                    var current = (TestIndex) arguments[0];
                    var files = new HashMap<>(current.files());
                    Path path = (Path) arguments[1];
                    if (method.getName().equals("withUpdatedFile")) {
                        files.put(path, (LanguageFileIndex) arguments[2]);
                    } else {
                        files.remove(path);
                    }
                    yield new TestIndex(id, files);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        LanguageIndexContextContributor contributor = new LanguageIndexContextContributor() {
            @Override
            public String languageId() {
                return id;
            }

            @Override
            public LanguageIndexContext resolve(Project project) {
                return new BasicLanguageIndexContext(id, List.of(project.getPath()), List.of(), List.of());
            }
        };
        return new LanguageContribution() {
            @Override
            public LanguageId id() {
                return new LanguageId(id);
            }

            @Override
            public String displayName() {
                return id;
            }

            @Override
            public LanguageMatcher matcher() {
                return matcher;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public Set<CapabilityKey<?>> supportedCapabilities() {
                return Set.of(LanguageCapabilities.INDEXER, LanguageCapabilities.INDEX_CONTEXT);
            }

            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                return new LanguageSession(CapabilityRegistry.builder()
                    .add(LanguageCapabilities.INDEXER, indexer)
                    .add(LanguageCapabilities.INDEX_CONTEXT, contributor).build());
            }
        };
    }

    @Test
    public void warmIndexesBuildsRegisteredLanguageIndexes() throws Exception {
        Path root = createProject("""
            package demo;

            class A {
            }
            """);

        ProjectLanguageIndexService indexService = createIndexService();
        ProjectIndexContext context = createContext(root, indexService);
        var coordinator = new ProjectLanguageIndexCoordinator(
            context, indexService);

        coordinator.warmIndexes();

        JavaProjectSemanticIndex index = indexService.currentTyped(context, JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(index);
        assertEquals(1, index.lookupQualifiedName("demo.A").size());
    }

    @Test
    public void handleFileChangeUpdatesAndRemovesIndexedFiles() throws Exception {
        Path root = createProject("""
            package demo;

            class A {
            }
            """);
        Path aFile = root.resolve("src/main/java/demo/A.java");

        ProjectLanguageIndexService indexService = createIndexService();
        ProjectIndexContext context = createContext(root, indexService);
        var coordinator = new ProjectLanguageIndexCoordinator(
            context, indexService);
        coordinator.warmIndexes();

        Files.writeString(aFile, """
            package demo;

            class A {
                static int VALUE;
            }
            """);

        coordinator.handleFileChange(aFile, StandardWatchEventKinds.ENTRY_MODIFY);

        JavaProjectSemanticIndex updated = indexService.currentTyped(context, JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(updated);
        assertEquals(1, updated.lookupMember("demo.A", "VALUE").size());

        coordinator.handleFileChange(aFile, StandardWatchEventKinds.ENTRY_DELETE);

        JavaProjectSemanticIndex removed = indexService.currentTyped(context, JavaLanguageSupport.LANGUAGE_ID);
        assertNotNull(removed);
        assertTrue(removed.lookupQualifiedName("demo.A").isEmpty());
    }

    @Test
    public void cancelledWarmupCannotPublishAfterProviderClearsInterruption() throws Exception {
        assertLateWarmupRejected(false);
    }

    @Test
    public void expiredWarmupCannotPublishAndDoesNotPoisonLaterRequests() throws Exception {
        assertLateWarmupRejected(true);
    }

    @Test
    public void aNoncooperativeLanguageTimesOutWithoutBlockingHealthyLanguageIndexes() throws Exception {
        Path root = createProject("package demo; class A {}");
        LanguageSupports.initialize();
        var service = IndexTestProviders.service();
        ProjectIndexContext base = createContext(root, service);
        var release = new CompletableFuture<Void>();
        var entered = new CountDownLatch(1);
        var healthy = new AtomicBoolean();
        ProjectLanguageIndexer<?, ?> badIndexer = fakeIndexer("bad", () -> {
            entered.countDown();
            release.join();
            Thread.interrupted();
        });
        ProjectLanguageIndexer<?, ?> healthyIndexer = fakeIndexer("healthy", () -> healthy.set(true));
        IndexTestProviders.indexer(service, badIndexer);
        IndexTestProviders.indexer(service, healthyIndexer);
        var context = new ProjectIndexContext(base.project(), root,
            Map.of("bad", emptyLanguage("bad"), "healthy", emptyLanguage("healthy")), base.session());
        var workspace = new WorkspaceSnapshot("provider-isolation", Map.of(),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
        var cancellation = new CancellationTokenSource();
        var request = new WorkspaceRequestContext(workspace, cancellation.token(), Deadline.none());
        try (var providers = new ProviderExecutor(4, 2, 8, Duration.ofMillis(100))) {
            var coordinator = new ProjectLanguageIndexCoordinator(context, service, providers, Duration.ofSeconds(1));
            coordinator.warmIndexes(request);
            assertEquals(0, entered.getCount());
            assertTrue(healthy.get());
            assertNull(service.current(context, "bad"));
            assertNotNull(service.current(context, "healthy"));
            assertTrue(providers.health().stream().anyMatch(item -> item.providerId().contains(":bad:")
                && item.timedOut() == 1));
        } finally {
            service.closeProject(base.session());
            release.complete(null);
        }
    }

    private static ProjectLanguageIndexer<?, ?> fakeIndexer(String id, Runnable build) {
        return (ProjectLanguageIndexer<?, ?>) Proxy.newProxyInstance(ProjectLanguageIndexer.class.getClassLoader(),
            new Class<?>[]{ProjectLanguageIndexer.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "languageId" -> id;
                case "supports" -> true;
                case "build" -> {
                    build.run();
                    yield new TestIndex(id);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static LanguageIndexContext emptyLanguage(String id) {
        return (LanguageIndexContext) Proxy.newProxyInstance(LanguageIndexContext.class.getClassLoader(),
            new Class<?>[]{LanguageIndexContext.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "languageId" -> id;
                case "sourceRoots", "generatedRoots", "dependencyRoots" -> List.of();
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private record TestIndex(String languageId, Map<Path, LanguageFileIndex> files)
        implements
            ProjectLanguageIndex<LanguageFileIndex> {
        private TestIndex {
            files = Map.copyOf(files);
        }

        private TestIndex(String languageId) {
            this(languageId, Map.of());
        }

        @Override
        public LanguageFileIndex getFileIndex(Path path) {
            return files.get(path);
        }
    }

    private void assertLateWarmupRejected(boolean expire) throws Exception {
        Path root = createProject("package demo; class A {}");
        LanguageSupports.initialize();
        var delegate = new JavaProjectLanguageIndexer();
        var entered = new CountDownLatch(1);
        var release = new CompletableFuture<Void>();
        var first = new AtomicBoolean(true);
        var indexer = (ProjectLanguageIndexer<?, ?>) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{ProjectLanguageIndexer.class}, (proxy, method, arguments) -> {
                Object value = method.invoke(delegate, arguments);
                if (method.getName().equals("build") && first.compareAndSet(true, false)) {
                    entered.countDown();
                    release.join();
                    Thread.interrupted();
                }
                return value;
            });
        var service = IndexTestProviders.service();
        IndexTestProviders.indexer(service, indexer);
        ProjectIndexContext context = createContext(root, service);
        var coordinator = new ProjectLanguageIndexCoordinator(context, service);
        var cancellation = new CancellationTokenSource();
        var nanos = new AtomicLong();
        Deadline deadline = expire ? Deadline.after(Duration.ofNanos(1), nanos::get) : Deadline.none();
        var workspace = new WorkspaceSnapshot("coordinator", Map.of(),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
        var request = new WorkspaceRequestContext(workspace, cancellation.token(), deadline);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = executor.submit(() -> coordinator.warmIndexes(request));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                if (expire) {
                    nanos.set(1);
                } else {
                    cancellation.cancel();
                }
                release.complete(null);
                assertInstanceOf(CancellationException.class,
                    assertThrows(ExecutionException.class, () -> pending.get(10, TimeUnit.SECONDS)).getCause());
                assertNull(service.currentTyped(context, JavaLanguageSupport.LANGUAGE_ID));
                coordinator.warmIndexes();
                assertNotNull(service.currentTyped(context, JavaLanguageSupport.LANGUAGE_ID));
            } finally {
                release.complete(null);
                service.closeProject(context.session());
            }
        }
    }

    private ProjectLanguageIndexService createIndexService() {
        LanguageSupport support = new JavaLanguageSupport();
        LanguageSupports.initialize();
        var indexService = IndexTestProviders.service();
        IndexTestProviders.indexer(indexService, new JavaProjectLanguageIndexer());
        IndexTestProviders.persistence(indexService, new JavaProjectSemanticPersistence());
        return indexService;
    }

    private ProjectIndexContext createContext(Path root, ProjectLanguageIndexService service) {
        Project project = (Project) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{Project.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getPath" -> root;
                case "getAlias" -> root.getFileName().toString();
                case "hasFacet" -> false;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "TestProject[" + root + "]";
                default -> throw new UnsupportedOperationException(method.getName());
            });
        var context = new DefaultProjectIndexContextResolver().resolve(project);
        return new ProjectIndexContext(project, root, context.languages(), service.openProject(project));
    }

    private Path createProject(String source) throws Exception {
        Path root = tempDir.resolve("project-" + System.nanoTime());
        Path file = root.resolve("src/main/java/demo/A.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return root;
    }
}
