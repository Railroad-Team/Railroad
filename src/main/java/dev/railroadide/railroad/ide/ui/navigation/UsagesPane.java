package dev.railroadide.railroad.ide.ui.navigation;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.provider.api.NavigationProvider;
import dev.railroadide.railroad.ide.sst.provider.api.ProviderExecutor;
import dev.railroadide.railroad.ide.sst.provider.api.UsageQuery;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSearch;
import dev.railroadide.railroad.ide.sst.provider.api.UsageSink;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shows the streamed results of one Find Usages search, grouped by document.
 * <p>
 * All state is owned by the JavaFX thread. A new search, Cancel, or closing the pane cancels the running request;
 * batches from superseded requests are discarded. Results stay bound to the document revisions they were found
 * in: once the workspace changes they are marked outdated, opening an outdated usage is refused instead of
 * selecting shifted text, and Refresh repeats the search for the same symbol identity against a new capture.
 */
public final class UsagesPane extends BorderPane implements AutoCloseable {
    private static final Duration SEARCH_BUDGET = Duration.ofSeconds(60);

    private final SstScheduler.Scope requests = SstScheduler.shared().newScope();
    private final Label status = new Label();
    private final Button refresh = new Button();
    private final Button cancel = new Button();
    private final TreeItem<Row> root = new TreeItem<>();
    private final TreeView<Row> tree = new TreeView<>(root);
    private final Map<DocumentId, TreeItem<Row>> groups = new LinkedHashMap<>();
    private final Set<Location.TextLocation> shown = new HashSet<>();
    private final AtomicBoolean outdatedCheckQueued = new AtomicBoolean();

    private @Nullable Request request;
    private @Nullable FeatureRequestContext activeContext;
    private @Nullable CancellationTokenSource activeCancellation;
    private DocumentStore.@Nullable ChangeSubscription storeSubscription;
    private @Nullable SymbolId symbol;
    private @Nullable String symbolName;
    private State state = State.IDLE;
    private int searchedDocuments;
    private int totalDocuments;
    private boolean closed;

    /**
     * Captured inputs of one search. The workspace and source must come from the same store capture.
     *
     * @param provider navigation provider of the source document's language session
     * @param providerLifetime lifetime of the provider's session; cancelling it cancels the search
     * @param store document store used to detect outdated results and to refresh
     * @param workspace captured workspace to search
     * @param source captured document the query is resolved in
     * @param query offset or symbol whose usages are searched
     */
    public record Request(
        NavigationProvider provider,
        CancellationToken providerLifetime,
        DocumentStore store,
        WorkspaceSnapshot workspace,
        TextDocumentSnapshot source,
        UsageQuery query
    ) {
        /**
         * Validates that all inputs are present.
         *
         * @param provider navigation provider of the source document's language session
         * @param providerLifetime lifetime of the provider's session
         * @param store document store used to detect outdated results and to refresh
         * @param workspace captured workspace to search
         * @param source captured document the query is resolved in
         * @param query offset or symbol whose usages are searched
         */
        public Request {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(providerLifetime, "providerLifetime");
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(query, "query");
        }
    }

    /**
     * Search state presented by the pane.
     */
    public enum State {
        /** No search has been started. */
        IDLE,
        /** A search is running; results may be partial. */
        SEARCHING,
        /** The search completed against an unchanged workspace. */
        COMPLETE,
        /** The search was cancelled; results may be partial. */
        CANCELLED,
        /** The workspace changed after the results were captured. */
        OUTDATED,
        /** The query resolved to no symbol or the language cannot search for usages. */
        UNRESOLVED,
        /** The provider failed or exceeded its budget. */
        FAILED
    }

    /**
     * Creates the pane used as the content of the Find Usages dock tool.
     *
     * @param project project whose workspace is searched
     */
    public UsagesPane(Project project) {
        this();
    }

    /**
     * Creates an empty results pane.
     */
    public UsagesPane() {
        getStyleClass().add("usages-pane");
        refresh.setText(L18n.localize("railroad.ide.usages.refresh"));
        refresh.setOnAction(_ -> refresh());
        cancel.setText(L18n.localize("railroad.ide.usages.cancel"));
        cancel.setOnAction(_ -> cancelSearch());
        HBox.setHgrow(status, Priority.ALWAYS);
        status.setMaxWidth(Double.MAX_VALUE);
        var header = new HBox(8, status, cancel, refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 8, 4, 8));
        setTop(header);

        tree.setShowRoot(false);
        tree.setCellFactory(_ -> new RowCell());
        tree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelected();
            }
        });
        tree.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                openSelected();
                event.consume();
            }
        });
        setCenter(tree);
        updateStatus();
    }

    /**
     * Starts a search, cancelling the previous one and clearing its results. Call on the JavaFX thread.
     *
     * @param request captured search inputs
     */
    public void search(Request request) {
        Objects.requireNonNull(request, "request");
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Usage searches must start on the FX thread");
        if (closed)
            return;

        invalidateRequest();
        clearResults();
        observe(request.store());
        this.request = request;
        if (request.query() instanceof UsageQuery.OfSymbol(SymbolId id)) {
            symbol = id;
        } else {
            symbol = null;
            symbolName = null;
        }
        state = State.SEARCHING;

        var cancellation = new CancellationTokenSource(request.providerLifetime());
        var context = new FeatureRequestContext(request.workspace(), request.source(), cancellation.token(),
            Deadline.after(SEARCH_BUDGET));
        activeCancellation = cancellation;
        activeContext = context;
        updateStatus();

        UsageSink sink = (batch, searched, total) -> {
            List<Location<?>> copy = List.copyOf(batch);
            Platform.runLater(() -> {
                if (activeContext != context || state != State.SEARCHING)
                    return;

                searchedDocuments = searched;
                totalDocuments = total;
                append(request.workspace(), copy);
                updateStatus();
            });
        };

        CompletableFuture<FeatureResult<UsageSearch>> future;
        try {
            future = requests.submit("usages", RequestPriority.NAVIGATION, context, cancellation,
                () -> ProviderExecutor.shared().execute(request.source().languageId() + ":usages", context,
                    SEARCH_BUDGET, () -> request.provider().usages(context, request.query(), sink)));
        } catch (RuntimeException exception) {
            future = CompletableFuture.failedFuture(exception);
        }

        future.thenAccept(result -> Platform.runLater(() -> {
            if (activeContext != context || result == null || !result.belongsTo(context))
                return;

            complete(request, result.value());
        })).exceptionally(throwable -> {
            Throwable cause = unwrap(throwable);
            Platform.runLater(() -> {
                if (activeContext != context)
                    return;

                releaseRequest();
                state = cause instanceof CancellationException && !(cause instanceof DeadlineExceededException)
                    ? State.CANCELLED
                    : State.FAILED;
                updateStatus();
            });
            if (!(cause instanceof CancellationException)) {
                Railroad.LOGGER.error("Failed to find usages", cause);
            }
            return null;
        });
    }

    /**
     * Repeats the last search against a new capture of its store. Searches whose symbol was resolved are repeated
     * by symbol identity; otherwise the original offset is reused only while its document is unchanged.
     */
    public void refresh() {
        Request previous = request;
        if (previous == null || closed)
            return;

        WorkspaceSnapshot workspace = previous.store().capture();
        if (!(workspace.document(previous.source().id()).orElse(null) instanceof TextDocumentSnapshot source))
            return;

        UsageQuery query;
        if (symbol != null) {
            query = new UsageQuery.OfSymbol(symbol);
        } else if (source.version().equals(previous.source().version())) {
            query = previous.query();
        } else
            return;

        search(new Request(previous.provider(), previous.providerLifetime(), previous.store(), workspace, source,
            query));
    }

    /**
     * Cancels the running search and keeps the partial results it produced.
     */
    public void cancelSearch() {
        if (state != State.SEARCHING)
            return;

        invalidateRequest();
        state = State.CANCELLED;
        updateStatus();
    }

    /**
     * Returns the presented search state.
     *
     * @return current state
     */
    public State state() {
        return state;
    }

    /**
     * Returns the usages currently listed, in display order.
     *
     * @return listed usage locations
     */
    public List<Location.TextLocation> usages() {
        return root.getChildren().stream()
            .flatMap(group -> group.getChildren().stream())
            .map(TreeItem::getValue)
            .filter(UsageRow.class::isInstance)
            .map(row -> ((UsageRow) row).location())
            .toList();
    }

    /**
     * Returns the user-facing status line.
     *
     * @return status text
     */
    public String statusText() {
        return status.getText();
    }

    /**
     * Opens a listed usage when its document revision is still current.
     *
     * @param location listed usage to open
     * @return whether the usage was revealed
     */
    public boolean open(Location.TextLocation location) {
        Request current = request;
        if (current == null || !shown.contains(location)
            || !(current.workspace().document(location.documentId())
                .orElse(null) instanceof TextDocumentSnapshot snapshot))
            return false;

        boolean opened = Services.EDITOR_TAB_MANAGER.navigateTo(null, snapshot, location);
        if (!opened) {
            status.setText(L18n.localize("railroad.ide.usages.outdated_target"));
        }
        return opened;
    }

    /**
     * Returns a completion barrier for the running search, for deterministic tests and shutdown.
     *
     * @return completion of the currently owned request
     */
    public CompletableFuture<Void> completion() {
        return requests.completion();
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        invalidateRequest();
        requests.close();
        if (storeSubscription != null) {
            storeSubscription.close();
            storeSubscription = null;
        }
        request = null;
        clearResults();
    }

    private void complete(Request request, @Nullable UsageSearch search) {
        releaseRequest();
        if (search == null) {
            state = State.UNRESOLVED;
            clearResults();
            updateStatus();
            return;
        }

        symbol = search.symbol();
        symbolName = search.displayName();
        clearResults();
        append(request.workspace(), search.usages());
        state = isCurrent(request) ? State.COMPLETE : State.OUTDATED;
        updateStatus();
    }

    private void append(WorkspaceSnapshot workspace, List<Location<?>> batch) {
        for (Location<?> usage : batch) {
            if (!(usage instanceof Location.TextLocation location)
                || !(workspace.document(location.documentId()).orElse(null) instanceof TextDocumentSnapshot snapshot))
                continue;

            try {
                location.validateAgainst(snapshot);
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                Railroad.LOGGER.warn("Discarding usage with an invalid location", exception);
                continue;
            }

            if (!shown.add(location))
                continue;

            TreeItem<Row> group = groups.computeIfAbsent(location.documentId(), _ -> {
                var item = new TreeItem<Row>(new FileRow(snapshot));
                item.setExpanded(true);
                root.getChildren().add(item);
                return item;
            });
            group.getChildren().add(new TreeItem<>(new UsageRow(snapshot, location, preview(snapshot, location))));
        }
    }

    private static String preview(TextDocumentSnapshot snapshot, Location.TextLocation location) {
        LineMap lines = snapshot.lineMap();
        int line = location.line();
        return (line + 1) + ":" + (location.column() + 1) + "  "
            + snapshot.text().substring(lines.lineStart(line), lines.lineEnd(line)).strip();
    }

    private void openSelected() {
        TreeItem<Row> selected = tree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() instanceof UsageRow row) {
            open(row.location());
        }
    }

    private void observe(DocumentStore store) {
        if (storeSubscription != null) {
            storeSubscription.close();
        }
        storeSubscription = store.onChange(() -> {
            if (outdatedCheckQueued.compareAndSet(false, true)) {
                Platform.runLater(this::markOutdatedIfChanged);
            }
        });
    }

    private void markOutdatedIfChanged() {
        outdatedCheckQueued.set(false);
        Request current = request;
        if (closed || current == null || state == State.SEARCHING || state == State.IDLE || state == State.OUTDATED
            || isCurrent(current))
            return;

        state = State.OUTDATED;
        updateStatus();
    }

    private static boolean isCurrent(Request request) {
        WorkspaceSnapshot latest = request.store().capture();
        return latest.documents().equals(request.workspace().documents())
            && latest.configuration().equals(request.workspace().configuration());
    }

    private void invalidateRequest() {
        requests.cancel("usages");
        if (activeCancellation != null) {
            activeCancellation.cancel();
        }
        releaseRequest();
    }

    private void releaseRequest() {
        activeCancellation = null;
        activeContext = null;
    }

    private void clearResults() {
        root.getChildren().clear();
        groups.clear();
        shown.clear();
        searchedDocuments = 0;
        totalDocuments = 0;
    }

    private void updateStatus() {
        int count = shown.size();
        String name = symbolName == null ? "" : symbolName;
        status.setText(switch (state) {
            case IDLE -> L18n.localize("railroad.ide.usages.idle");
            case SEARCHING -> L18n.localize("railroad.ide.usages.searching", count, searchedDocuments,
                totalDocuments);
            case COMPLETE -> L18n.localize("railroad.ide.usages.complete", count, groups.size(), name);
            case CANCELLED -> L18n.localize("railroad.ide.usages.cancelled", count);
            case OUTDATED -> L18n.localize("railroad.ide.usages.outdated", count, name);
            case UNRESOLVED -> L18n.localize("railroad.ide.usages.unresolved");
            case FAILED -> L18n.localize("railroad.ide.usages.failed");
        });
        cancel.setDisable(state != State.SEARCHING);
        refresh.setDisable(request == null || state == State.SEARCHING);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
            && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private sealed interface Row permits FileRow, UsageRow {
        String text();
    }

    private record FileRow(TextDocumentSnapshot snapshot) implements Row {
        @Override
        public String text() {
            String name = snapshot.uri().fileName().orElse(snapshot.uri().toString());
            return snapshot.uri().filePath()
                .map(Path::getParent)
                .map(parent -> name + "  " + parent)
                .orElse(name);
        }
    }

    private record UsageRow(TextDocumentSnapshot snapshot, Location.TextLocation location, String preview)
        implements
            Row {
        @Override
        public String text() {
            return preview;
        }
    }

    private static final class RowCell extends TreeCell<Row> {
        @Override
        protected void updateItem(Row item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.text());
        }
    }
}
