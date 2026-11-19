package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.language.BaseLanguageSupport;
import dev.railroadide.railroad.ide.language.EditorOpenView;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.language.index.ProjectIndexContext;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexService;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.Location.TextLocation;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.BinaryDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.ContentKind;
import dev.railroadide.railroad.ide.sst.document.api.Location.BinaryLocation;
import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.DeadlineExceededException;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.sst.request.api.RequestPriority;
import dev.railroadide.railroad.ide.sst.request.api.SstScheduler;
import dev.railroadide.railroad.ide.sst.request.api.WorkspaceRequestContext;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.railroadide.railroad.ide.diagnostics.DiagnosticSeverity;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectDiagnosticsScannerTest {
    @TempDir
    public Path directory;

    @BeforeAll
    public static void initialize() {
        WorkspaceModes.initialize();
    }

    @Test
    public void javaProjectScanResolvesSiblingSourceDependencies() throws Exception {
        LanguageSupports.initialize();
        Path sources = directory.resolve("src/main/java/demo");
        Files.createDirectories(sources);
        Files.writeString(sources.resolve("Use.java"), """
            package demo;
            class Use {
                void run(ReviewDependency dep) { dep.combine(1, true); }
            }
            """);
        Files.writeString(sources.resolve("ReviewDependency.java"), """
            package demo;
            class ReviewDependency {
                public int combine(int count, boolean enabled) { return count; }
            }
            """);
        Project project = project(false);
        try {
            Path report = ProjectDiagnosticsScanner.scan(project, directory.resolve("reports/java.txt"));
            String rendered = Files.readString(report);
            assertTrue(rendered.contains("Scanned files: 2"), rendered);
            assertTrue(rendered.contains("Failed files: 0"), rendered);
            assertFalse(rendered.contains("[ERROR]"), rendered);
        } finally {
            LanguageSupportRegistry.runtime().closeProject(project);
        }
    }

    @Test
    public void allFilesUseTheSameWorkspaceCapturedBeforeProvidersRun() throws Exception {
        Path first = directory.resolve("First.capturedscan");
        Path second = directory.resolve("Second.capturedscan");
        Files.writeString(first, "original");
        Files.writeString(second, "original");
        var captured = new AtomicReference<WorkspaceSnapshot>();
        var calls = new AtomicInteger();
        var provider = new DiagnosticsProvider<TextEditorDiagnostic>() {
            @Override
            public List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
                throw new AssertionError("Scan must pass the captured workspace");
            }

            @Override
            public FeatureResult<List<TextEditorDiagnostic>> compute(FeatureRequestContext context) {
                WorkspaceSnapshot workspace = context.workspaceSnapshot();
                captured.compareAndSet(null, workspace);
                assertSame(captured.get(), workspace);
                assertEquals(2, workspace.documents().size());
                workspace.documents().values()
                    .forEach(snapshot -> assertEquals("original", ((TextDocumentSnapshot) snapshot).text()));
                assertSame(context.documentSnapshot(),
                    workspace.document(context.documentSnapshot().id()).orElseThrow());
                assertDoesNotThrow(() -> {
                    Files.writeString(first, "changed");
                    Files.writeString(second, "changed");
                });
                calls.incrementAndGet();
                return new FeatureResult<>(context, List.of());
            }
        };
        var support = new BaseLanguageSupport("captured-scan-test", "Captured scan", Set.of("capturedscan")) {
            @Override
            public EditorOpenView open(Project project, DocumentId documentId, Path file) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<CapabilityKey<?>> supportedCapabilities() {
                return Set.of(SstCapabilities.DIAGNOSTICS);
            }

            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                return new LanguageSession(
                    CapabilityRegistry.builder().add(SstCapabilities.DIAGNOSTICS, provider).build());
            }
        };
        LanguageSupportRegistry.register(support);
        Project project = project();
        try {
            var context = new ProjectDiagnosticsContext(project, new ProjectIndexContext(project, directory, Map.of()));
            Path report = ProjectDiagnosticsScanner.scan(context, directory.resolve("reports/captured.txt"));
            assertTrue(Files.readString(report).contains("Failed files: 0"));
            assertEquals(2, calls.get());
        } finally {
            LanguageSupportRegistry.runtime().unregister(support.id());
            LanguageSupportRegistry.runtime().closeProject(project);
        }
    }

    @Test
    public void failedProviderIsReportedAsIncompleteWhileHealthyFilesStillProduceDiagnostics() throws Exception {
        Files.writeString(directory.resolve("Broken.scheduling"), "broken");
        Files.writeString(directory.resolve("Healthy.scheduling"), "healthy");
        var active = new AtomicInteger();
        var calls = new AtomicInteger();
        var provider = new DiagnosticsProvider<TextEditorDiagnostic>() {
            @Override
            public List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
                throw new AssertionError("Scan must pass request controls");
            }

            @Override
            public FeatureResult<List<TextEditorDiagnostic>> compute(FeatureRequestContext context) {
                assertTrue(active.incrementAndGet() <= 2, "Scan must bound outstanding provider work");
                try {
                    calls.incrementAndGet();
                    context.checkActive();
                    assertTrue(context.deadline().remaining().isPresent());
                    var snapshot = (TextDocumentSnapshot) context.documentSnapshot();
                    if (snapshot.text().equals("broken"))
                        throw new DeadlineExceededException();
                    return new FeatureResult<>(context, List.of(new TextEditorDiagnostic(
                        TextLocation.from(snapshot, 0, 1), DiagnosticSeverity.WARNING, "HEALTHY",
                        "healthy diagnostic")));
                } finally {
                    active.decrementAndGet();
                }
            }
        };
        var support = new BaseLanguageSupport("scheduler-scan-test", "Scheduler scan", Set.of("scheduling")) {
            @Override
            public EditorOpenView open(Project project, DocumentId documentId, Path file) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<CapabilityKey<?>> supportedCapabilities() {
                return Set.of(SstCapabilities.DIAGNOSTICS);
            }

            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                return new LanguageSession(
                    CapabilityRegistry.builder().add(SstCapabilities.DIAGNOSTICS, provider).build());
            }
        };
        LanguageSupportRegistry.register(support);
        try {
            Project project = project();
            var context = new ProjectDiagnosticsContext(project,
                new ProjectIndexContext(project, directory, Map.of()));
            Path report = directory.resolve("reports/result.txt");
            assertEquals(report, ProjectDiagnosticsScanner.scan(context, report));
            String rendered = Files.readString(report);
            assertEquals(2, calls.get());
            assertTrue(rendered.contains("Scanned files: 2"));
            assertTrue(rendered.contains("Failed files: 1"));
            assertTrue(rendered.contains("Incomplete analysis:"));
            assertTrue(rendered.contains("Broken.scheduling: DeadlineExceededException"));
            assertTrue(rendered.contains("HEALTHY: healthy diagnostic"));
        } finally {
            LanguageSupportRegistry.runtime().unregister(support.id());
        }
    }

    @Test
    public void cancelledScanDoesNotPublishEvenAnEmptyReport() {
        Project project = project();
        var cancellation = new CancellationTokenSource();
        cancellation.cancel();
        var context = new ProjectDiagnosticsContext(project,
            new ProjectIndexContext(project, directory, Map.of(), null, 0, cancellation.token()));
        Path report = directory.resolve("reports/cancelled.txt");
        assertThrows(CancellationException.class, () -> ProjectDiagnosticsScanner.scan(context, report));
        assertFalse(Files.exists(report));
    }

    @Test
    public void independentBinaryContributionUsesTheSameDiagnosticsScan() throws Exception {
        Files.write(directory.resolve("Header.bytesample"), new byte[]{(byte) 0xff, 0, 1});
        var id = new LanguageId("sample:binary-scan");
        DiagnosticsProvider<EditorDiagnostic.BinaryEditorDiagnostic> provider = snapshot -> {
            var binary = assertInstanceOf(BinaryDocumentSnapshot.class, snapshot);
            return List.of(new EditorDiagnostic.BinaryEditorDiagnostic(BinaryLocation.from(binary, 0, 1),
                DiagnosticSeverity.WARNING, "HEADER", "Unknown header", id.value(), DiagnosticOrigin.SEMANTIC,
                Set.of(), List.of(), null));
        };
        var contribution = new LanguageContribution() {
            @Override
            public LanguageId id() {
                return id;
            }

            @Override
            public String displayName() {
                return "Sample binary";
            }

            @Override
            public LanguageMatcher matcher() {
                return LanguageMatcher.extension("bytesample");
            }

            @Override
            public ContentKind contentKind() {
                return ContentKind.BINARY;
            }

            @Override
            public Set<CapabilityKey<?>> supportedCapabilities() {
                return Set.of(SstCapabilities.DIAGNOSTICS);
            }

            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                return new LanguageSession(
                    CapabilityRegistry.builder().add(SstCapabilities.DIAGNOSTICS, provider).build());
            }
        };
        Project project = project();
        LanguageSupportRegistry.runtime().register("binary-scan-test", contribution);
        try {
            var context = new ProjectDiagnosticsContext(project, new ProjectIndexContext(project, directory, Map.of()));
            Path report = ProjectDiagnosticsScanner.scan(context, directory.resolve("reports/binary.txt"));
            String rendered = Files.readString(report);
            assertTrue(rendered.contains("Failed files: 0"));
            assertTrue(rendered.contains("Scanned files: 1"));
            assertTrue(rendered.contains("HEADER: Unknown header"));
        } finally {
            LanguageSupportRegistry.runtime().unregisterOwner("binary-scan-test");
            LanguageSupportRegistry.runtime().closeProject(project);
        }
    }

    @Test
    public void cancellingAnActiveScanDoesNotWaitForAnUncooperativeProvider() throws Exception {
        assertScanStopsWhileProviderIsBlocked(false, ScanInvalidation.CANCELLATION);
    }

    @Test
    public void cancellingAnInlineScanDoesNotWaitForAnUncooperativeProvider() throws Exception {
        assertScanStopsWhileProviderIsBlocked(true, ScanInvalidation.CANCELLATION);
    }

    @Test
    public void closingTheProjectStopsAnActiveScanWhileItsProviderIsBlocked() throws Exception {
        assertScanStopsWhileProviderIsBlocked(false, ScanInvalidation.PROJECT_CLOSURE);
    }

    @Test
    public void closingTheProjectStopsAnInlineScanWhileItsProviderIsBlocked() throws Exception {
        assertScanStopsWhileProviderIsBlocked(true, ScanInvalidation.PROJECT_CLOSURE);
    }

    @Test
    public void refreshingTheProjectStopsAnActiveScanWhileItsProviderIsBlocked() throws Exception {
        assertScanStopsWhileProviderIsBlocked(false, ScanInvalidation.CONFIGURATION_CHANGE);
    }

    @Test
    public void refreshingTheProjectStopsAnInlineScanWhileItsProviderIsBlocked() throws Exception {
        assertScanStopsWhileProviderIsBlocked(true, ScanInvalidation.CONFIGURATION_CHANGE);
    }

    private void assertScanStopsWhileProviderIsBlocked(boolean inline, ScanInvalidation invalidation) throws Exception {
        Files.writeString(directory.resolve("Blocked.scheduling"), "blocked");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var observed = new AtomicReference<FeatureRequestContext>();
        var provider = new DiagnosticsProvider<TextEditorDiagnostic>() {
            @Override
            public List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
                throw new AssertionError("Scan must pass request controls");
            }

            @Override
            public FeatureResult<List<TextEditorDiagnostic>> compute(FeatureRequestContext context) {
                observed.set(context);
                entered.countDown();
                try {
                    while (true) {
                        try {
                            release.await();
                            return new FeatureResult<>(context, List.of());
                        } catch (InterruptedException _) {
                            // Simulate plugin code that keeps running until the test releases it.
                        }
                    }
                } finally {
                    exited.countDown();
                }
            }
        };
        var support = new BaseLanguageSupport("scheduler-cancel-scan-test", "Cancelled scan", Set.of("scheduling")) {
            @Override
            public EditorOpenView open(Project project, DocumentId documentId, Path file) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<CapabilityKey<?>> supportedCapabilities() {
                return Set.of(SstCapabilities.DIAGNOSTICS);
            }

            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                return new LanguageSession(
                    CapabilityRegistry.builder().add(SstCapabilities.DIAGNOSTICS, provider).build());
            }
        };
        LanguageSupportRegistry.register(support);
        var outcome = new CompletableFuture<Throwable>();
        var cancellation = new CancellationTokenSource();
        Project project = project();
        var service = new ProjectLanguageIndexService();
        var session = invalidation == ScanInvalidation.CANCELLATION ? null : service.openProject(project);
        var context = new ProjectDiagnosticsContext(project,
            new ProjectIndexContext(project, directory, Map.of(), session,
                session == null ? 0 : session.configurationGeneration(), cancellation.token()));
        Path report = directory.resolve("reports/cancelled-active.txt");
        Runnable scan = () -> {
            try {
                assertEquals(inline, SstScheduler.shared().isWorkerThread());
                ProjectDiagnosticsScanner.scan(context, report);
                outcome.complete(null);
            } catch (Throwable failure) {
                outcome.complete(failure);
            }
        };
        Thread caller = inline ? null : Thread.ofVirtual().unstarted(scan);
        try (var scope = SstScheduler.shared().newScope()) {
            if (inline) {
                var scheduledCancellation = new CancellationTokenSource();
                var workspace = new WorkspaceSnapshot(project.getId(), Map.of(),
                    new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
                var request = new WorkspaceRequestContext(workspace, scheduledCancellation.token(), Deadline.none());
                scope.submit("scan", RequestPriority.BACKGROUND_INDEXING, request, scheduledCancellation, () -> {
                    scan.run();
                    return null;
                });
            } else {
                caller.start();
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                switch (invalidation) {
                    case CANCELLATION -> cancellation.cancel();
                    case PROJECT_CLOSURE -> service.closeProject(session);
                    case CONFIGURATION_CHANGE -> service.refreshProject(session);
                }
                assertInstanceOf(CancellationException.class, outcome.get(5, TimeUnit.SECONDS));
                assertEquals(1, release.getCount());
                assertTrue(observed.get().cancellationToken().isCancellationRequested());
                assertThrows(CancellationException.class, observed.get()::checkActive);
                assertFalse(Files.exists(report));
            } finally {
                release.countDown();
                cancellation.cancel();
                assertTrue(exited.await(5, TimeUnit.SECONDS));
                if (caller != null) {
                    caller.join(5000);
                }
                scope.completion().get(5, TimeUnit.SECONDS);
            }
        } finally {
            service.closeProject(session);
            LanguageSupportRegistry.runtime().unregister(support.id());
        }
    }

    @Test
    public void cachedDiagnosticsFromAnEarlierScanAreReportedAsIncomplete() throws Exception {
        Path source = directory.resolve("Cached.scheduling");
        Files.writeString(source, "broken");
        var cached = new AtomicReference<FeatureResult<List<TextEditorDiagnostic>>>();
        var calls = new AtomicInteger();
        var provider = new DiagnosticsProvider<TextEditorDiagnostic>() {
            @Override
            public List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
                throw new AssertionError("Scan must pass request controls");
            }

            @Override
            public FeatureResult<List<TextEditorDiagnostic>> compute(FeatureRequestContext context) {
                calls.incrementAndGet();
                var snapshot = (TextDocumentSnapshot) context.documentSnapshot();
                if (cached.get() == null) {
                    assertEquals("broken", snapshot.text());
                    cached.set(new FeatureResult<>(context, List.of(new TextEditorDiagnostic(
                        TextLocation.from(snapshot, 0, 1), DiagnosticSeverity.ERROR, "STALE", "earlier diagnostic"))));
                    assertTrue(cached.get().belongsTo(context));
                } else {
                    assertEquals("healthy", snapshot.text());
                    assertFalse(cached.get().belongsTo(context));
                }
                return cached.get();
            }
        };
        var support = new BaseLanguageSupport("scheduler-stale-scan-test", "Stale scan", Set.of("scheduling")) {
            @Override
            public EditorOpenView open(Project project, DocumentId documentId, Path file) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<CapabilityKey<?>> supportedCapabilities() {
                return Set.of(SstCapabilities.DIAGNOSTICS);
            }

            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                return new LanguageSession(
                    CapabilityRegistry.builder().add(SstCapabilities.DIAGNOSTICS, provider).build());
            }
        };
        LanguageSupportRegistry.register(support);
        try {
            Project project = project();
            var context = new ProjectDiagnosticsContext(project,
                new ProjectIndexContext(project, directory, Map.of()));
            Path report = directory.resolve("reports/cached.txt");
            ProjectDiagnosticsScanner.scan(context, report);
            String firstReport = Files.readString(report);
            assertTrue(firstReport.contains("Failed files: 0"));
            assertTrue(firstReport.contains("STALE: earlier diagnostic"));

            Files.writeString(source, "healthy");
            ProjectDiagnosticsScanner.scan(context, report);
            String secondReport = Files.readString(report);
            assertEquals(2, calls.get());
            assertTrue(secondReport.contains("Failed files: 1"));
            assertTrue(secondReport.contains("Incomplete analysis:"));
            assertTrue(secondReport.contains("Cached.scheduling: IllegalStateException"));
            assertTrue(secondReport.contains("Diagnostics: 0"));
            assertFalse(secondReport.contains("STALE"));
        } finally {
            LanguageSupportRegistry.runtime().unregister(support.id());
        }
    }

    private enum ScanInvalidation {
        CANCELLATION,
        PROJECT_CLOSURE,
        CONFIGURATION_CHANGE
    }

    private Project project() {
        return project(true);
    }

    private Project project(boolean hasFacets) {
        return (Project) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Project.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getPath" -> directory;
                case "getId" -> directory.toString();
                case "getAlias" -> "Scanner test";
                case "getFacets" -> List.of();
                case "hasFacet" -> hasFacets;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "ScannerTestProject";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
