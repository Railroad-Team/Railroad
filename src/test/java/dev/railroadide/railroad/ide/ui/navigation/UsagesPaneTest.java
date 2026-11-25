package dev.railroadide.railroad.ide.ui.navigation;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.provider.api.NavigationProvider;
import dev.railroadide.railroad.ide.sst.provider.api.UsageQuery;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSearch;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSink;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.DeadlineExceededException;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.sst.semantic.api.DefinitionTarget;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.localization.Languages;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import javafx.scene.control.TreeItem;

import static dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorRequestTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class UsagesPaneTest {
    private static final SymbolId SYMBOL = new SymbolId("test:symbol");

    private final DefaultDocumentStore store = new DefaultDocumentStore("test",
        new WorkspaceConfigurationSnapshot(0, Set.of()));
    private final TextDocumentSnapshot first = document("First.java", "use one\nuse two");
    private final TextDocumentSnapshot second = document("Second.java", "use three");
    private UsagesPane pane;

    @BeforeAll
    public static void initializeFx() throws Exception {
        startFx();
        onFx(() -> {
            L18n.loadLanguage(Languages.EN_US);
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @AfterEach
    public void closePane() throws Exception {
        if (pane != null) {
            onFx(() -> {
                pane.close();
                return null;
            });
        }
    }

    @Test
    public void streamedGroupsKeepTheirCountsAndExpansionWhenTheSearchFinishes() throws Exception {
        var finish = new CountDownLatch(1);
        Location.TextLocation firstUsage = usage(first, 0);
        Location.TextLocation secondUsage = usage(first, 8);
        start(provider((context, query, sink) -> {
            sink.accept(List.of(firstUsage), 1, 2);
            sink.accept(List.of(secondUsage), 2, 2);
            await(finish);
            return search(context, firstUsage, secondUsage);
        }), new UsageQuery.AtOffset(0));

        try {
            waitUntil(() -> pane.usages().size() == 2);
            onFx(() -> {
                new Scene(pane, 800, 300);
                pane.applyCss();
                pane.layout();
                assertEquals("2", ((Label) pane.lookup(".usages-file-count")).getText());
                assertFalse(pane.lookup(".usages-empty").isVisible());
                groupButton("railroad.generic.collapse_all").fire();
                assertFalse(
                    ((TreeView<?>) pane.lookup(".usages-tree")).getRoot().getChildren().getFirst().isExpanded());
                return null;
            });
        } finally {
            finish.countDown();
        }

        waitUntil(() -> pane.state() == UsagesPane.State.COMPLETE);
        onFx(() -> {
            TreeItem<?> group = ((TreeView<?>) pane.lookup(".usages-tree")).getRoot().getChildren().getFirst();
            assertFalse(group.isExpanded());
            groupButton("railroad.generic.expand_all").fire();
            assertTrue(group.isExpanded());
            assertEquals("2 usages in 1 file", pane.statusText());
            return null;
        });
    }

    private Button groupButton(String key) {
        return pane.lookupAll(".toolbar-button").stream()
            .map(Button.class::cast)
            .filter(button -> L18n.localize(key).equals(button.getAccessibleText()))
            .findFirst().orElseThrow();
    }

    @Test
    public void previewHighlightsTheReturnedRangeWithinTheLine() throws Exception {
        TextDocumentSnapshot source = document("Repeated.java", "header\n    use(use);");
        store.publishBacking(source);
        start(provider((context, query, sink) -> search(context, usage(source, source.text().lastIndexOf("use")))),
            new UsageQuery.AtOffset(0));
        waitUntil(() -> pane.state() == UsagesPane.State.COMPLETE);
        onFx(() -> {
            new Scene(pane, 800, 300);
            pane.applyCss();
            pane.layout();
            HBox snippet = (HBox) pane.lookup(".usages-snippet");
            assertEquals(List.of("use(", "use", ");"), snippet.getChildren().stream()
                .map(Label.class::cast).map(Label::getText).toList());
            assertSame(snippet.getChildren().get(1), pane.lookup(".usages-match"));
            assertEquals("2:9", ((Label) pane.lookup(".usages-position")).getText());
            return null;
        });
    }

    @Test
    public void partialBatchesAreShownBeforeTheCompleteResult() throws Exception {
        var release = new CountDownLatch(1);
        start(provider((context, query, sink) -> {
            sink.accept(List.of(usage(first, 0)), 1, 2);
            await(release);
            sink.accept(List.of(usage(second, 0)), 2, 2);
            return search(context, usage(first, 0), usage(second, 0));
        }), new UsageQuery.AtOffset(0));

        waitUntil(() -> pane.usages().size() == 1);
        onFx(() -> {
            assertEquals(UsagesPane.State.SEARCHING, pane.state());
            return null;
        });

        release.countDown();
        pane.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            assertEquals(UsagesPane.State.COMPLETE, pane.state());
            assertEquals(List.of(usage(first, 0), usage(second, 0)), pane.usages());
            return null;
        });
    }

    @Test
    public void cancellingKeepsPartialResultsAndIgnoresLaterBatches() throws Exception {
        var entered = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        start(provider((context, query, sink) -> {
            sink.accept(List.of(usage(first, 0)), 1, 2);
            entered.countDown();
            while (!context.cancellationToken().isCancellationRequested()) {
                Thread.onSpinWait();
            }

            sink.accept(List.of(usage(second, 0)), 2, 2);
            cancelled.countDown();
            context.checkActive();
            return search(context);
        }), new UsageQuery.AtOffset(0));

        assertTrue(entered.await(10, TimeUnit.SECONDS));
        waitUntil(() -> pane.usages().size() == 1);
        onFx(() -> {
            pane.cancelSearch();
            return null;
        });

        assertTrue(cancelled.await(10, TimeUnit.SECONDS));
        pane.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> null);
        onFx(() -> {
            assertEquals(UsagesPane.State.CANCELLED, pane.state());
            assertEquals(List.of(usage(first, 0)), pane.usages());
            return null;
        });
    }

    @Test
    public void aNewSearchSupersedesTheRunningSearch() throws Exception {
        var releaseOld = new CountDownLatch(1);
        var oldContext = new CompletableFuture<FeatureRequestContext>();
        start(provider((context, query, sink) -> {
            if (query instanceof UsageQuery.AtOffset(int offset) && offset == 0) {
                oldContext.complete(context);
                await(releaseOld);
                sink.accept(List.of(usage(first, 0)), 1, 1);
                return search(context, usage(first, 0));
            }

            return search(context, usage(second, 0));
        }), new UsageQuery.AtOffset(0));
        oldContext.get(10, TimeUnit.SECONDS);
        onFx(() -> {
            pane.search(request(currentProvider, new UsageQuery.AtOffset(4)));
            return null;
        });

        releaseOld.countDown();
        pane.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> null);
        onFx(() -> {
            assertTrue(oldContext.getNow(null).cancellationToken().isCancellationRequested());
            assertEquals(List.of(usage(second, 0)), pane.usages());
            assertEquals(UsagesPane.State.COMPLETE, pane.state());
            return null;
        });
    }

    @Test
    public void workspaceChangesMarkResultsOutdatedAndRefreshSearchesBySymbol() throws Exception {
        BlockingQueue<UsageQuery> queries = new LinkedBlockingQueue<UsageQuery>();
        BlockingQueue<WorkspaceSnapshot> workspaces = new LinkedBlockingQueue<WorkspaceSnapshot>();
        start(provider((context, query, sink) -> {
            queries.add(query);
            workspaces.add(context.workspaceSnapshot());
            TextDocumentSnapshot document = (TextDocumentSnapshot) context.workspaceSnapshot().document(first.id())
                .orElseThrow();
            return search(context, usage(document, 0));
        }), new UsageQuery.AtOffset(0));
        pane.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> {
            assertEquals(UsagesPane.State.COMPLETE, pane.state());
            store.publishOverlay(new TextDocumentSnapshot(first.id(), first.uri(), first.version().next(), "java",
                first.origin(), "\n" + first.text(), StandardCharsets.UTF_8));
            return null;
        });

        onFx(() -> null);
        onFx(() -> {
            assertEquals(UsagesPane.State.OUTDATED, pane.state());
            pane.refresh();
            return null;
        });

        pane.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> null);
        assertEquals(new UsageQuery.AtOffset(0), queries.poll());
        assertEquals(new UsageQuery.OfSymbol(SYMBOL), queries.poll());
        workspaces.poll();
        assertEquals(first.version().next(), workspaces.poll().document(first.id()).orElseThrow().version());
        onFx(() -> {
            assertEquals(UsagesPane.State.COMPLETE, pane.state());
            assertEquals(first.version().next(), pane.usages().getFirst().documentVersion());
            return null;
        });
    }

    @Test
    public void invalidUsagesAreDiscardedAndUnresolvedSearchesAreReported() throws Exception {
        start(provider((context, query, sink) -> {
            sink.accept(List.of(new Location.TextLocation(first.id(), first.version().next(), new TextRange(0, 1), 0,
                0)), 1, 1);
            return new FeatureResult<>(context, null);
        }), new UsageQuery.AtOffset(0));
        pane.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> null);
        onFx(() -> {
            assertEquals(UsagesPane.State.UNRESOLVED, pane.state());
            assertTrue(pane.usages().isEmpty());
            return null;
        });
    }

    @Test
    public void providerFailuresAreReportedWithoutResults() throws Exception {
        start(provider((context, query, sink) -> {
            throw new IllegalStateException("Provider failed");
        }), new UsageQuery.AtOffset(0));
        pane.completion().get(10, TimeUnit.SECONDS);
        onFx(() -> null);
        onFx(() -> {
            assertEquals(UsagesPane.State.FAILED, pane.state());
            assertTrue(pane.statusText().contains("Provider failed"));
            assertTrue(pane.lookup(".usages-empty").isVisible());
            assertEquals("Provider failed", ((Label) pane.lookup(".usages-empty-detail")).getText());
            assertFalse(pane.lookup(".usages-tree").isVisible());
            return null;
        });
    }

    @Test
    public void preparationProgressAndTimeoutAreVisibleAndKeepPartialResults() throws Exception {
        var ready = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        start(provider((context, query, sink) -> {
            await(ready);
            sink.accept(List.of(usage(first, 0)), 1, 2);
            await(finish);
            throw new DeadlineExceededException();
        }), new UsageQuery.AtOffset(0));
        assertEquals(L18n.localize("railroad.ide.usages.preparing"), onFx(pane::statusText));
        ready.countDown();
        waitUntil(() -> pane.usages().size() == 1);
        assertEquals("Scanned 1 of 2 files · 1 usage", onFx(pane::statusText));
        finish.countDown();
        waitUntil(() -> pane.state() == UsagesPane.State.TIMED_OUT);
        assertEquals("Search timed out · 1 usage", onFx(pane::statusText));
        assertEquals(List.of(usage(first, 0)), onFx(pane::usages));
    }

    @Test
    public void closingThePaneCancelsTheRunningSearch() throws Exception {
        var context = new CompletableFuture<FeatureRequestContext>();
        start(provider((request, query, sink) -> {
            context.complete(request);
            while (!request.cancellationToken().isCancellationRequested()) {
                Thread.onSpinWait();
            }

            request.checkActive();
            return search(request);
        }), new UsageQuery.AtOffset(0));
        context.get(10, TimeUnit.SECONDS);
        onFx(() -> {
            pane.close();
            assertTrue(pane.usages().isEmpty());
            return null;
        });

        assertTrue(context.get().cancellationToken().isCancellationRequested());
    }

    private NavigationProvider currentProvider;

    private void start(NavigationProvider provider, UsageQuery query) throws Exception {
        store.publishBacking(first);
        store.publishBacking(second);
        currentProvider = provider;
        onFx(() -> {
            pane = new UsagesPane();
            pane.search(request(provider, query));
            return null;
        });
    }

    private UsagesPane.Request request(NavigationProvider provider, UsageQuery query) {
        WorkspaceSnapshot workspace = store.capture();
        return new UsagesPane.Request(provider, CancellationToken.none(), store,
            workspace, (TextDocumentSnapshot) workspace.document(first.id()).orElseThrow(), query);
    }

    private static void waitUntil(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!onFx(condition)) {
            assertTrue(System.nanoTime() < deadline, "Condition was not reached");
            Thread.sleep(10);
        }
    }

    @FunctionalInterface
    private interface UsageFunction {
        FeatureResult<UsageSearch> search(FeatureRequestContext context, UsageQuery query, UsageSink sink);
    }

    private static NavigationProvider provider(UsageFunction function) {
        return new NavigationProvider() {
            @Override
            public FeatureResult<List<DefinitionTarget>> definitions(FeatureRequestContext context, int offset) {
                return new FeatureResult<>(context, List.of());
            }

            @Override
            public FeatureResult<UsageSearch> usages(
                FeatureRequestContext context,
                UsageQuery query,
                UsageSink sink
            ) {
                return function.search(context, query, sink);
            }
        };
    }

    private static FeatureResult<UsageSearch> search(FeatureRequestContext context, Location<?>... usages) {
        return new FeatureResult<>(context, new UsageSearch(SYMBOL, "symbol", List.of(usages)));
    }

    private static Location.TextLocation usage(TextDocumentSnapshot snapshot, int start) {
        return Location.TextLocation.from(snapshot, start, start + 3);
    }

    private static TextDocumentSnapshot document(String name, String text) {
        var uri = DocumentUri.fromPath(Path.of(name).toAbsolutePath());
        return new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
    }
}
