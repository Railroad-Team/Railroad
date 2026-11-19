package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

public class LanguageRuntimeTest {
    private static final CapabilityKey<Probe> PROBE = new CapabilityKey<>("test:probe", Probe.class);
    @TempDir
    public Path directory;

    @Test
    public void idsAndCapabilityTypesAreValidatedAndAbsenceIsExplicit() {
        assertThrows(IllegalArgumentException.class, () -> new LanguageId("java"));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityKey<>("bad", Probe.class));
        Probe probe = request -> new FeatureResult<>(request, "ok");
        var registry = CapabilityRegistry.builder().add(PROBE, probe).build();
        assertSame(probe, registry.find(PROBE).orElseThrow());
        assertTrue(registry.find(new CapabilityKey<>("test:missing", Probe.class)).isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> registry.find(new CapabilityKey<>("test:probe", Runnable.class)));
        assertThrows(IllegalArgumentException.class,
            () -> CapabilityRegistry.builder().add(PROBE, probe).add(PROBE, probe));
        assertThrows(IllegalArgumentException.class, () -> new LanguageSession(CapabilityRegistry.builder()
            .add(new CapabilityKey<>("test:concrete", String.class), "unsafe").build()));
    }

    @Test
    public void matchingIsDeterministicAndSupportsNamesCompoundSuffixesShebangsAndContent() {
        var ordinary = contribution("test:ordinary", LanguageMatcher.extension("json"), 0, _ -> emptySession());
        var compound = contribution("test:compound", LanguageMatcher.extension("schema.json"), 0, _ -> emptySession());
        var exact = contribution("test:exact", LanguageMatcher.exactName("project.schema.json"), 0,
            _ -> emptySession());
        try (var runtime = new LanguageRuntime()) {
            runtime.register("z", ordinary);
            runtime.register("a", compound);
            assertEquals(compound.id(), runtime.find(input("project.schema.json", "")).orElseThrow().id());
            runtime.register("b", exact);
            assertEquals(exact.id(), runtime.find(input("project.schema.json", "")).orElseThrow().id());
            runtime.register("priority", contribution("test:priority", LanguageMatcher.extension("json"), 2,
                _ -> emptySession()));
            assertEquals(new LanguageId("test:priority"),
                runtime.find(input("project.schema.json", "")).orElseThrow().id());
        }
        for (boolean reverse : List.of(false, true)) {
            try (var runtime = new LanguageRuntime()) {
                var alpha = contribution("a:language", LanguageMatcher.extension("same"), 0, _ -> emptySession());
                var beta = contribution("b:language", LanguageMatcher.extension("same"), 0, _ -> emptySession());
                runtime.register("one", reverse ? beta : alpha);
                runtime.register("two", reverse ? alpha : beta);
                assertEquals(alpha.id(), runtime.find(input("file.same", "")).orElseThrow().id());
            }
        }
        assertTrue(LanguageMatcher.shebang("tiny").match(input("script", "#!/usr/bin/env tiny\nlet a")) > 0);
        assertEquals(0, LanguageMatcher.shebang("tiny").match(input("script", "#!/usr/bin/tiny-other")));
        assertEquals(0, LanguageMatcher.shebang("tiny").match(input("script", "#!/usr/bin/other tiny")));
        assertTrue(LanguageMatcher.content(value -> value.prefix()[0] == 42).match(
            new LanguageMatchInput(directory.resolve("binary"), new byte[]{42})) > 0);
        assertEquals(LanguageMatchInput.MAX_PREFIX_BYTES,
            new LanguageMatchInput(directory, new byte[10000]).prefix().length);
    }

    @Test
    public void sessionsAreSharedPerProjectDialectAndOwnerUnloadDisposesOnlyOwnedServices() {
        var closed = new AtomicInteger();
        var opened = new AtomicInteger();
        try (var runtime = new LanguageRuntime()) {
            var contribution = contribution("test:owned", LanguageMatcher.extension("owned"), 0, context -> {
                opened.incrementAndGet();
                return new LanguageSession(CapabilityRegistry.builder().add(PROBE, new ClosingProbe(closed)).build());
            });
            runtime.register("owner", contribution);
            Project project = project();
            var first = runtime.session(project, contribution.id(), "one");
            assertSame(first, runtime.session(project, contribution.id(), "one"));
            var second = runtime.session(project, contribution.id(), "two");
            var third = runtime.session(project(), contribution.id(), "one");
            assertNotSame(first, second);
            assertNotSame(first, third);
            Probe retained = first.capability(PROBE).orElseThrow();
            assertTrue(retained.run(context()).value().equals("ok"));
            runtime.closeProject(project);
            assertEquals(2, closed.get());
            assertFalse(third.isClosed());
            assertThrows(CancellationException.class, () -> retained.run(context()));
            runtime.unregisterOwner("owner");
            assertEquals(3, closed.get());
            assertTrue(runtime.all().isEmpty());
            runtime.unregisterOwner("owner");
            assertEquals(opened.get(), closed.get());
        }
    }

    @Test
    public void unloadCancelsInFlightWorkAndRejectsLateResults() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var runtime = new LanguageRuntime(); var executor = Executors.newSingleThreadExecutor()) {
            var contribution = contribution("test:inflight", LanguageMatcher.extension("test"), 0,
                _ -> new LanguageSession(CapabilityRegistry.builder().add(PROBE, request -> {
                    entered.countDown();
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        throw new RuntimeException(exception);
                    }
                    assertTrue(request.cancellationToken().isCancellationRequested());
                    return new FeatureResult<>(request, "late");
                }).build()));
            runtime.register("owner", contribution);
            var probe = runtime.session(project(), contribution.id(), "").capability(PROBE).orElseThrow();
            Future<?> result = executor.submit(() -> probe.run(context()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            runtime.unregisterOwner("owner");
            release.countDown();
            assertInstanceOf(CancellationException.class,
                assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS)).getCause());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void sessionCreationRacingUnloadClosesUnpublishedSession() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closed = new AtomicInteger();
        try (var runtime = new LanguageRuntime(); var executor = Executors.newSingleThreadExecutor()) {
            var contribution = contribution("test:opening", LanguageMatcher.extension("test"), 0, _ -> {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    throw new RuntimeException(exception);
                }
                return new LanguageSession(CapabilityRegistry.builder().add(PROBE, new ClosingProbe(closed)).build());
            });
            runtime.register("owner", contribution);
            Future<?> result = executor.submit(() -> runtime.session(project(), contribution.id(), ""));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            runtime.unregisterOwner("owner");
            release.countDown();
            assertInstanceOf(CancellationException.class,
                assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS)).getCause());
            assertEquals(1, closed.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void sessionPreservesRequestIdentityAndRejectsForeignResults() {
        FeatureRequestContext original = context();
        try (var session = new LanguageSession(CapabilityRegistry.builder().add(PROBE,
            request -> new FeatureResult<>(request, "ok")).build())) {
            assertTrue(session.capability(PROBE).orElseThrow().run(original).belongsTo(original));
        }
        try (var session = new LanguageSession(CapabilityRegistry.builder().add(PROBE,
            request -> new FeatureResult<>(context(), "stale")).build())) {
            assertThrows(IllegalStateException.class, () -> session.capability(PROBE).orElseThrow().run(original));
        }
    }

    @Test
    public void alreadyCancelledRequestsNeverInvokeTheProvider() {
        var invoked = new AtomicInteger();
        var original = context();
        var cancelled = new FeatureRequestContext(original.workspaceSnapshot(), original.documentSnapshot(),
            () -> true, Deadline.none());
        try (var session = new LanguageSession(CapabilityRegistry.builder().add(PROBE, request -> {
            invoked.incrementAndGet();
            return new FeatureResult<>(request, "ignored cancellation");
        }).build())) {
            assertThrows(CancellationException.class, () -> session.capability(PROBE).orElseThrow().run(cancelled));
            assertEquals(0, invoked.get());
        }
    }

    @Test
    public void cleanupContinuesAfterFailuresAndInvokesEachResourceOnce() {
        var disposed = new AtomicInteger();
        var failing = new ClosingProbe(disposed) {
            @Override
            public void close() {
                super.close();
                throw new IllegalStateException("failure");
            }
        };
        var session = new LanguageSession(CapabilityRegistry.builder().add(PROBE, failing)
            .add(new CapabilityKey<>("test:alias", Probe.class), failing)
            .add(new CapabilityKey<>("test:second", Probe.class), new ClosingProbe(disposed)).build());
        var notifications = new AtomicInteger();
        session.onClose(notifications::incrementAndGet);
        assertThrows(IllegalStateException.class, session::close);
        session.close();
        session.onClose(notifications::incrementAndGet);
        assertEquals(2, disposed.get());
        assertEquals(2, notifications.get());
    }

    @Test
    public void mismatchedDeclaredCapabilitiesRejectAndDisposeTheSession() {
        var closed = new AtomicInteger();
        try (var runtime = new LanguageRuntime()) {
            var contribution = contribution("test:mismatch", LanguageMatcher.extension("test"), 0,
                _ -> new LanguageSession(CapabilityRegistry.builder()
                    .add(new CapabilityKey<>("test:wrong", Probe.class), new ClosingProbe(closed)).build()));
            runtime.register("owner", contribution);
            assertThrows(IllegalStateException.class, () -> runtime.session(project(), contribution.id(), ""));
            assertEquals(1, closed.get());
        }
    }

    @Test
    public void managedDiscoveryFiltersParentsAndRollsBackMalformedServiceBatches() throws Exception {
        String className = TestLanguageContribution.class.getName();
        Path descriptor = directory.resolve("META-INF/services/" + LanguageContribution.class.getName());
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, className);
        String classResource = className.replace('.', '/') + ".class";
        Path classFile = directory.resolve(classResource);
        Files.createDirectories(classFile.getParent());
        ClassLoader providerLoader = TestLanguageContribution.class.getClassLoader();
        try (var stream = providerLoader.getResourceAsStream(classResource)) {
            Files.copy(Objects.requireNonNull(stream), classFile);
        }
        URL[] urls = {directory.toUri().toURL()};
        try (var runtime = new LanguageRuntime();
            var parentOnly = new URLClassLoader(urls, getClass().getClassLoader())) {
            runtime.discover("parent", parentOnly);
            assertTrue(runtime.all().isEmpty());
        }
        try (var runtime = new LanguageRuntime(); var loader = new URLClassLoader(urls, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(className))
                    return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }) {
            runtime.discover("plugin", loader);
            assertEquals(1, runtime.all().size());
            runtime.unregisterOwner("plugin");
            Files.writeString(descriptor, className + "\nmissing.Provider");
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> runtime.discover("broken-plugin", loader));
            assertTrue(failure.getMessage().contains("broken-plugin"));
            assertTrue(runtime.all().isEmpty());
        }
    }

    public interface Probe {
        FeatureResult<String> run(FeatureRequestContext request);
    }

    private static class ClosingProbe implements Probe, AutoCloseable {
        private final AtomicInteger closed;
        private ClosingProbe(AtomicInteger closed) {
            this.closed = closed;
        }
        @Override
        public FeatureResult<String> run(FeatureRequestContext request) {
            return new FeatureResult<>(request, "ok");
        }
        @Override
        public void close() {
            closed.incrementAndGet();
        }
    }

    private LanguageMatchInput input(String name, String content) {
        return new LanguageMatchInput(directory.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    private static LanguageSession emptySession() {
        return new LanguageSession(CapabilityRegistry.empty());
    }

    private static LanguageContribution contribution(
        String id,
        LanguageMatcher matcher,
        int priority,
        Function<LanguageSessionContext, LanguageSession> factory
    ) {
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
                return Set.of(PROBE);
            }
            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                return factory.apply(context);
            }
        };
    }

    private FeatureRequestContext context() {
        DocumentId id = DocumentId.create();
        var document = new TextDocumentSnapshot(id, DocumentUri.inMemory(id), new DocumentVersion(1),
            "test:runtime", "", StandardCharsets.UTF_8);
        var workspace = new WorkspaceSnapshot("test", Map.of(id, document),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
        return new FeatureRequestContext(workspace, document, CancellationToken.none(), Deadline.none());
    }

    private Project project() {
        return (Project) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Project.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getPath" -> directory;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "RuntimeTestProject";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
